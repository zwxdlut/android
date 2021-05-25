public class NativeExample
{
    static
    {
        System.loadLibrary("native_example_jni");
    }

    public static void main(String args[])
    {
        NativeExample nativeExample = new NativeExample();
        nativeExample.hello("Hello World!");
    }

    public native void hello(String content);
}