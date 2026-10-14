import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Development-only preview harness for the console program in OOPS.java.
 *
 * The project itself is a plain Java console program (no build tool, no web
 * layer), so there is nothing to open in a browser. This harness gives the
 * preview something real to show: it compiles OOPS.java with the JDK, runs the
 * program with the pen color / type typed into the page as stdin, and returns
 * the program's own console output.
 *
 * It only uses the JDK (com.sun.net.httpserver), so the project still needs no
 * dependencies. Sources are recompiled on every run, so edits to OOPS.java show
 * up without restarting anything.
 */
public class PreviewServer {

    private static final int PORT = 3000;
    private static final String MAIN_CLASS = "OOPS";
    private static final List<String> SOURCE_FILES = Arrays.asList(
            "/app/OOPS.java",
            "/app/.base44/preview/PreviewServer.java");
    private static final long COMPILE_TIMEOUT_SECONDS = 60;
    private static final long RUN_TIMEOUT_SECONDS = 20;
    private static final int MAX_INPUT_LENGTH = 120;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/", PreviewServer::handlePage);
        server.createContext("/run", PreviewServer::handleRun);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Preview harness for " + MAIN_CLASS + " listening on http://0.0.0.0:" + PORT);
    }

    private static void handlePage(HttpExchange exchange) throws IOException {
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        send(exchange, 200, "text/html; charset=utf-8", PAGE);
    }

    private static void handleRun(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        String color = cleanInput(params.get("color"));
        String type = cleanInput(params.get("type"));

        StringBuilder transcript = new StringBuilder();
        try {
            Path classes = Files.createTempDirectory("oops-classes-");
            List<String> compile = new ArrayList<>(Arrays.asList("javac", "-d", classes.toString()));
            compile.addAll(SOURCE_FILES);

            Result compiled = run(compile, null, COMPILE_TIMEOUT_SECONDS);
            if (compiled.exitCode != 0) {
                transcript.append("javac failed (exit ").append(compiled.exitCode).append(")\n\n")
                        .append(compiled.output);
            } else {
                Result program = run(
                        Arrays.asList("java", "-cp", classes.toString(), MAIN_CLASS),
                        color + "\n" + type + "\n",
                        RUN_TIMEOUT_SECONDS);
                transcript.append(program.output);
                if (program.timedOut) {
                    transcript.append("\n[stopped after ").append(RUN_TIMEOUT_SECONDS).append("s]");
                }
            }
        } catch (Exception e) {
            transcript.append("Could not run ").append(MAIN_CLASS).append(": ").append(e);
        }
        send(exchange, 200, "text/plain; charset=utf-8", transcript.toString());
    }

    private static Result run(List<String> command, String stdin, long timeoutSeconds)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    captured.write(buffer, 0, read);
                }
            } catch (IOException ignored) {
                // stream closed when the process is killed
            }
        });
        reader.setDaemon(true);
        reader.start();

        if (stdin == null) {
            process.getOutputStream().close();
        } else {
            try (OutputStream out = process.getOutputStream()) {
                out.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        reader.join(2000);

        Result result = new Result();
        result.output = captured.toString(StandardCharsets.UTF_8);
        result.exitCode = finished ? process.exitValue() : -1;
        result.timedOut = !finished;
        return result;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private static String cleanInput(String value) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replace("\r", " ").replace("\n", " ").trim();
        return singleLine.length() > MAX_INPUT_LENGTH ? singleLine.substring(0, MAX_INPUT_LENGTH) : singleLine;
    }

    private static void send(HttpExchange exchange, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static final class Result {
        private String output = "";
        private int exitCode;
        private boolean timedOut;
    }

    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>OOPS.java - live run</title>
              <style>
                :root { color-scheme: light dark; }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  padding: 2rem 1rem 3rem;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                  background: #f5f6f8;
                  color: #1c1e21;
                }
                main { max-width: 44rem; margin: 0 auto; }
                h1 { margin: 0 0 .35rem; font-size: 1.6rem; }
                .lede { margin: 0 0 1.5rem; color: #5b6169; }
                .lede code { background: #e7e9ee; padding: .1rem .3rem; border-radius: 4px; }
                form {
                  display: flex;
                  flex-wrap: wrap;
                  gap: .75rem;
                  align-items: flex-end;
                  background: #fff;
                  padding: 1rem;
                  border: 1px solid #dfe3e8;
                  border-radius: 10px;
                }
                label { display: flex; flex-direction: column; gap: .3rem; font-size: .8rem; color: #5b6169; flex: 1 1 12rem; }
                input {
                  font: inherit;
                  padding: .5rem .6rem;
                  border: 1px solid #cfd5dd;
                  border-radius: 6px;
                  background: #fff;
                  color: inherit;
                }
                button {
                  font: inherit;
                  font-weight: 600;
                  padding: .55rem 1.1rem;
                  border: 1px solid #1f6feb;
                  border-radius: 6px;
                  background: #1f6feb;
                  color: #fff;
                  cursor: pointer;
                }
                button:hover { background: #1a5fcc; }
                h2 { font-size: .8rem; text-transform: uppercase; letter-spacing: .08em; color: #5b6169; margin: 1.75rem 0 .5rem; }
                pre {
                  margin: 0;
                  padding: 1rem;
                  min-height: 12rem;
                  background: #12161c;
                  color: #d6e2f0;
                  border-radius: 10px;
                  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                  font-size: .85rem;
                  line-height: 1.5;
                  white-space: pre-wrap;
                  overflow: auto;
                }
              </style>
            </head>
            <body>
            <main>
              <h1>OOPS.java</h1>
              <p class="lede">The console program in <code>OOPS.java</code>, compiled and run with what you type below.</p>
              <form id="run-form">
                <label>Pen color<input name="color" value="sky blue"></label>
                <label>Pen type<input name="type" value="dark gel"></label>
                <button type="submit">Run program</button>
              </form>
              <h2>Console output</h2>
              <pre id="output">Press "Run program" to compile and run OOPS.java.</pre>
            </main>
            <script>
              const form = document.getElementById("run-form");
              const output = document.getElementById("output");
              form.addEventListener("submit", async (event) => {
                event.preventDefault();
                output.textContent = "Compiling and running...";
                try {
                  const query = new URLSearchParams(new FormData(form)).toString();
                  const response = await fetch("/run?" + query);
                  output.textContent = await response.text();
                } catch (error) {
                  output.textContent = "Request failed: " + error;
                }
              });
            </script>
            </body>
            </html>
            """;
}
