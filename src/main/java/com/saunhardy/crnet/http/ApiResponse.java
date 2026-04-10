package com.saunhardy.crnet.http;

import org.jetbrains.annotations.Nullable;

/**
 * Generic wrapper for HTTP responses from the backend.
 * <p>
 * The {@code message} field is extracted from the backend's JSON envelope
 * ({@code {"success": ..., "message": "..."}}) when present.
 *
 * @param <T> the type of the parsed response body
 */
public class ApiResponse<T> {

    private final int statusCode;
    private final String rawBody;
    @Nullable private final T data;
    @Nullable private final String error;
    @Nullable private final String message;

    public ApiResponse(int statusCode, String rawBody, @Nullable T data,
                       @Nullable String error, @Nullable String message) {
        this.statusCode = statusCode;
        this.rawBody = rawBody;
        this.data = data;
        this.error = error;
        this.message = message;
    }

    public int getStatusCode() { return statusCode; }
    public String getRawBody() { return rawBody; }
    @Nullable public T getData() { return data; }
    @Nullable public String getError() { return error; }
    @Nullable public String getMessage() { return message; }

    public boolean isSuccess() { return statusCode >= 200 && statusCode < 300; }
}
