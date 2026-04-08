package com.saunhardy.crnet.http;

/**
 * Thrown by {@link BackendHttpClient} when a request cannot be completed.
 * Covers HTTP errors (non-2xx), parse failures, auth failures, and I/O errors.
 */
public class BackendException extends Exception {

    private final int statusCode;

    public BackendException(String message) {
        this(message, -1);
    }

    public BackendException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public BackendException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /** HTTP status code that caused the error, or {@code -1} for non-HTTP errors. */
    public int getStatusCode() {
        return statusCode;
    }

    public boolean isAuthError() {
        return statusCode == 401;
    }
}
