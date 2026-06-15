class A{
    String val="A";
    void print(){
        System.out.print(val);
    }
}
class B extends A {
    String val = "B";

    }

    public class qqw {
        public static void main(String[] args) {
            A a = new A();
            A b = new B();
            System.out.print(a.val);
            System.out.print(b.val);
            a.print();
            b.print();

        }
    }

