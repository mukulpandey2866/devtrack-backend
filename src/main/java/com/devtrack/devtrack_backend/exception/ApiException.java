package com.devtrack.devtrack_backend.exception;

import org.springframework.http.HttpStatus;

/** Business-level failure that maps directly to an HTTP status. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
