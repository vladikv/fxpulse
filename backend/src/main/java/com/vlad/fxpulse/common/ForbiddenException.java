package com.vlad.fxpulse.common;

/** The request is understood but not allowed, whoever sends it (e.g. admin API disabled); mapped to HTTP 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
