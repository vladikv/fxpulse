package com.vlad.fxpulse.insight;

/**
 * The AI provider cannot produce an answer right now: no API key configured, timeout, 5xx,
 * rejected request, or an empty/blocked answer. Mapped to HTTP 503.
 * The message is written by us and safe to show; provider error bodies are never put in it.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
