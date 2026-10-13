package com.devtrack.devtrack_backend.exception;

import org.springframework.http.HttpStatus;

/** AI provider or AI feature failure; messages are safe to show to clients. */
public class AiServiceException extends ApiException {

    public AiServiceException(HttpStatus status, String message) {
        super(status, message);
    }
}
