# Project notes for agents

## What this repository is
A first Java project: one console program in `OOPS.java` (class `OOPS` with `main`, plus a `Pen` class).
It reads a pen color and a pen type from stdin and prints the objects it created.
There is **no build tool** (no Maven/Gradle), **no dependencies** (only `java.util`), and **no web layer**.

## How it runs here
`docker-compose.base44.yml` runs everything from the bind-mounted source in a `eclipse-temurin:17-jdk-alpine` container:

- `javac -d /tmp/classes /app/OOPS.java /app/.base44/preview/PreviewServer.java` at container start,
- then `java -cp /tmp/classes PreviewServer`, listening on `0.0.0.0:3000`.

`.base44/preview/PreviewServer.java` is a **development-only preview harness** (JDK-only, `com.sun.net.httpserver`).
A console program cannot be seen in the browser, so the harness compiles `OOPS.java` and runs it with the pen color/type
from the page as stdin, returning the program's own console output. Sources are recompiled on **every** `/run` request,
so edits to `OOPS.java` appear without restarting the container.

- `GET /` - the page (two inputs + "Run program").
- `GET /run?color=sky+blue&type=dark+gel` - runs the program and returns its console transcript as `text/plain`.

### Adding a new Java source file
Add it to the `javac` file list in `docker-compose.base44.yml` **and** to `SOURCE_FILES` in
`.base44/preview/PreviewServer.java`, otherwise it will not be compiled.

## Verify a change
```sh
curl -s "http://localhost:3000/run?color=sky%20blue&type=dark%20gel"
```
Expected: `writing something`, the two `Pen` object reports, both prompts, and the final report with the typed values.
Compile errors in `OOPS.java` are returned as `javac failed (exit 1)` plus the compiler message.

## Sandbox notes
- No env vars, no secrets, no database, no migrations - nothing to install or seed.
- The program's prompts and outputs go to stdout; there is no file or network I/O to configure.
- `*.class` files are written to a temp directory, never into the repository.
