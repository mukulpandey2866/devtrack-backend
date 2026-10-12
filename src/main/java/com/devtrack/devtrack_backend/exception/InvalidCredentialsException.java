package com.devtrack.devtrack_backend.exception;

import org.springframework.http.HttpStatus;

/** Deliberately generic: never reveals whether the email or the password was wrong. */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
