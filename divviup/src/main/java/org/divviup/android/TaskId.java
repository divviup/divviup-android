package org.divviup.android;

import android.util.Base64;

/**
 * A DAP task identifier. This is the unique identifier that clients, aggregators, and collectors
 * use to distinguish between different kinds of measurements, and associate reports with a task.
 * Objects of this class are immutable.
 */
public class TaskId {
    private final byte[] bytes;

    private TaskId(byte[] bytes) {
        if (bytes.length != 32) {
            throw new IllegalArgumentException("TaskId must be 32 bytes long");
        }
        this.bytes = bytes;
    }

    /**
     * Encodes this task ID into its textual representation.
     *
     * @return  the task ID in un-padded base64url form
     */
    public String encodeToString() {
        return Base64.encodeToString(
                this.bytes,
                Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE
        );
    }

    /**
     * Parses a task ID from its textual representation, as seen in DAP URLs.
     *
     * @param input                     the task ID in un-padded base64url form
     * @return                          the task ID
     * @throws IllegalArgumentException if the input is not a valid un-padded base64url value, or if
     *                                  it does not decode to 32 bytes
     */
    public static TaskId parse(String input) {
        byte[] bytes = Base64.decode(
                input,
                Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE
        );
        return new TaskId(bytes);
    }

    /**
     * Gets the byte array representation of this task ID. This array must not be modified, as it
     * will be shared with native code.
     *
     * @return  the task ID as an array of 32 bytes
     */
    byte[] toBytes() {
        return this.bytes;
    }
}
