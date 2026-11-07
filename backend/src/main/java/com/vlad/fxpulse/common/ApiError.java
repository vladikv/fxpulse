package com.vlad.fxpulse.common;

import java.time.Instant;

/**
 * The JSON body of every error response, so clients can rely on a single shape.
 *
 * @param timestamp when the error happened
 * @param status    HTTP status code, e.g. 404
 * @param error     HTTP reason phrase, e.g. "Not Found"
 * @param message   human-readable explanation, safe to show to the client
 * @param path      request path that caused the error
 */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {
}
