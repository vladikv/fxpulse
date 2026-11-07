package com.vlad.fxpulse.insight;

/**
 * Anything that can turn a prompt into text. The service depends on this interface, not on Gemini,
 * so another provider (e.g. a local Ollama model) can be added as a second implementation, and
 * service tests can use a mock without any HTTP.
 */
public interface AiInsightClient {

    /**
     * @throws AiRateLimitedException when the provider's quota is exhausted (HTTP 429)
     * @throws AiUnavailableException when the provider is not configured, unreachable or returns no text
     */
    AiCompletion generate(String prompt);
}
