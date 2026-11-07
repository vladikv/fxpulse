package com.vlad.fxpulse.insight;

/** The AI provider answered HTTP 429 (free-tier quota used up); mapped to HTTP 429 for our client too. */
public class AiRateLimitedException extends RuntimeException {

    public AiRateLimitedException(String message) {
        super(message);
    }
}
