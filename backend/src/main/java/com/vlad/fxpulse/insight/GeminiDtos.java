package com.vlad.fxpulse.insight;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * JSON shapes of the Gemini {@code models/{model}:generateContent} endpoint, reduced to the fields we use.
 * Unknown response fields (safetyRatings, usageMetadata, ...) are ignored by Jackson.
 */
public final class GeminiDtos {

    private GeminiDtos() {
    }

    // ---- request ----

    public record GenerateRequest(List<Content> contents, GenerationConfig generationConfig) {

        /** A single-turn request: one user message with one text part. */
        static GenerateRequest ofPrompt(String prompt, int maxOutputTokens) {
            return new GenerateRequest(
                    List.of(new Content("user", List.of(new Part(prompt)))),
                    new GenerationConfig(maxOutputTokens));
        }
    }

    /** {@code role} is "user" in requests and "model" in responses. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(String role, List<Part> parts) {
    }

    public record Part(String text) {
    }

    public record GenerationConfig(Integer maxOutputTokens) {
    }

    // ---- response ----

    public record GenerateResponse(List<Candidate> candidates, String modelVersion) {
    }

    /** {@code finishReason} is e.g. STOP, MAX_TOKENS, SAFETY. */
    public record Candidate(Content content, String finishReason) {
    }
}
