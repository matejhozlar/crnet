package com.saunhardy.crnet.auth;

/** Thrown when the {@link TokenManager} cannot obtain a valid JWT. */
public class TokenException extends Exception {

    public TokenException(String message) {
        super(message);
    }

    public TokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
