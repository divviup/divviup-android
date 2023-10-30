package org.divviup.android;

public class NativeLib {

    // Used to load the 'android' library on application startup.
    static {
        System.loadLibrary("android");
    }

    /**
     * A native method that is implemented by the 'android' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}