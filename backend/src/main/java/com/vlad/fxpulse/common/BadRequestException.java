package com.vlad.fxpulse.common;

/**
 * Thrown by services when a request is well-formed but breaks a business rule
 * (e.g. from is after to); mapped to HTTP 400.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
