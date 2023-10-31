package org.divviup.android;

public class NativeLib {

    // Used to load the 'divviup_android' library on application startup.
    static {
        System.loadLibrary("divviup_android");
    }

    /**
     * A native method that is implemented by the 'divviup_android' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
