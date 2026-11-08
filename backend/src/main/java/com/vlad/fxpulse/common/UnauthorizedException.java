package com.vlad.fxpulse.common;

/** The caller did not prove who it is (e.g. missing or wrong admin token); mapped to HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
