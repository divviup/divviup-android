package org.divviup.android;

public class NativeLib {

    // Used to load the 'divviup' library on application startup.
    static {
        System.loadLibrary("divviup");
    }

    /**
     * A native method that is implemented by the 'divviup' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
