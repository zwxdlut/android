public class NativeDemo
{
    static
    {
        System.loadLibrary("native_demo");
    }

    public static void main(String args[])
    {
        NativeDemo nd = new NativeDemo();
        nd.init();
        nd.hello(100);
        nd.deinit();
    }

    public native void init();
    public native void deinit();
    public native void hello(int parm);
}