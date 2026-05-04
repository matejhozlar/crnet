package com.saunhardy.crnet.http;

import org.jetbrains.annotations.Nullable;

/**
 * Generic wrapper for HTTP responses from the backend.
 * <p>
 * Both {@code message} and {@code playerMessage} are extracted from the
 * backend's JSON envelope when present:
 * <ul>
 *   <li>{@code message} — system/developer message for logging</li>
 *   <li>{@code playerMessage} — player-facing message for in-game chat (optional)</li>
 * </ul>
 *
 * @param <T> the type of the parsed response body
 */
public class ApiResponse<T> {

    private final int statusCode;
    @Nullable private final String rawBody;
    @Nullable private final T data;
    @Nullable private final String error;
    @Nullable private final String message;
    @Nullable private final String playerMessage;

    public ApiResponse(int statusCode, @Nullable String rawBody, @Nullable T data,
                       @Nullable String error, @Nullable String message,
                       @Nullable String playerMessage) {
        this.statusCode = statusCode;
        this.rawBody = rawBody;
        this.data = data;
        this.error = error;
        this.message = message;
        this.playerMessage = playerMessage;
    }

    public int getStatusCode() { return statusCode; }

    /**
     * The unparsed response body. Populated on error responses (status &lt; 200 or
     * &gt;= 300) for diagnostics; {@code null} on success to avoid retaining the
     * payload alongside the deserialised {@link #getData() data}.
     */
    @Nullable public String getRawBody() { return rawBody; }
    @Nullable public T getData() { return data; }
    @Nullable public String getError() { return error; }

    /** System/developer message for logging. */
    @Nullable public String getMessage() { return message; }

    /** Player-facing message for in-game chat, or {@code null} if not applicable. */
    @Nullable public String getPlayerMessage() { return playerMessage; }

    public boolean isSuccess() { return statusCode >= 200 && statusCode < 300; }
}
