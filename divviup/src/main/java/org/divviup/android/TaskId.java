package org.divviup.android;

import android.util.Base64;

public class TaskId {
    private final byte[] bytes;

    public TaskId(byte[] bytes) {
        if (bytes.length != 32) {
            throw new IllegalArgumentException("TaskId must be 32 bytes long");
        }
        this.bytes = bytes;
    }

    public String encodeToString() {
        return Base64.encodeToString(
                this.bytes,
                Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE
        );
    }

    public static TaskId parse(String input) {
        byte[] bytes = Base64.decode(
                input,
                Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE
        );
        return new TaskId(bytes);
    }

    public byte[] toBytes() {
        return this.bytes;
    }
}
