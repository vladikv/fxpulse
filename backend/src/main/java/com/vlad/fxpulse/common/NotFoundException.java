package com.vlad.fxpulse.common;

/** Thrown by services when a requested resource (e.g. a currency) does not exist; mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
