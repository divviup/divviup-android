package android.util;

public class Base64 {
    public static final int NO_PADDING = 1, NO_WRAP = 2, URL_SAFE = 8;
    private static final int DAP_BASE64_FLAGS = NO_PADDING | NO_WRAP | URL_SAFE;

    public static String encodeToString(byte[] input, int flags) {
        assert flags == DAP_BASE64_FLAGS;
        java.util.Base64.Encoder encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(input);
    }

    public static byte[] decode(String str, int flags) {
        assert flags == DAP_BASE64_FLAGS;
        java.util.Base64.Decoder decoder = java.util.Base64.getUrlDecoder();
        return decoder.decode(str);
    }
}
