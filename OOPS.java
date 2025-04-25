import java.util.*;
class Pen{
    String color;
    String type;
    public void write(){
        System.out.println("writing something");
    }
    public void printColor(){
        System.out.println("The color of pen is "+this.color);
        System.out.println("The type of pen is "+this.type);

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
        System.out.println("Enter the color of pen. ");
        pen1.color=sc.nextLine();
        System.out.println("Enter the type of pen ");
        pen1.type=sc.nextLine();
        pen1.printColor();

    }
}
