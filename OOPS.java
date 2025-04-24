import java.util.*;
class Pen{
    String color;
    String type;
    public void write(){
        System.out.println("writing something");
    }
    public void printColor(){
        System.out.println(this.color);
        System.out.println(this.type);
    }
}
public class OOPS{
    public static void main(String args []){
        Pen pen1= new Pen();
        pen1.color="red";
        pen1.type="gel";
        pen1.write();
        Pen pen2= new Pen();
        pen2.color="blue";
        pen2.type="bol";
        pen2.printColor();
        pen1.printColor();
        Scanner sc= new Scanner(System.in);
        pen1.color=sc.next();
        pen1.type=sc.next();
        pen1.printColor();

    }
}
