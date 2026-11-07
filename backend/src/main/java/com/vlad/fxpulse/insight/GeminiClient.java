package com.vlad.fxpulse.insight;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link AiInsightClient} backed by the Google Gemini {@code generateContent} REST endpoint.
 *
 * <p>The API key is sent in the {@code x-goog-api-key} header, not as {@code ?key=} in the URL:
 * URLs are written to access logs, proxies and error messages; headers usually are not.
 * Nothing in this class logs the key, the prompt or Gemini's error body.
 */
@Component
public class GeminiClient implements AiInsightClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    static final String API_KEY_HEADER = "x-goog-api-key";

    private final RestClient restClient;
    private final AiProperties properties;

    public GeminiClient(AiProperties properties) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // LLM answers take seconds, so the read timeout is longer than NBP's.
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public AiCompletion generate(String prompt) {
        if (!properties.hasApiKey()) {
            // Fail before any HTTP call: there is no point in sending a request that must be rejected.
            throw new AiUnavailableException("AI insights are not configured (GEMINI_API_KEY is not set)");
        }

        GeminiDtos.GenerateResponse response = callGemini(prompt);
        String text = extractText(response);
        return new AiCompletion(text, properties.model());
    }

    private GeminiDtos.GenerateResponse callGemini(String prompt) {
        try {
            return restClient.post()
                    .uri("/models/{model}:generateContent", properties.model())
                    .header(API_KEY_HEADER, properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GeminiDtos.GenerateRequest.ofPrompt(prompt, properties.maxOutputTokens()))
                    .retrieve()
                    .body(GeminiDtos.GenerateResponse.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Gemini rate limit reached (HTTP 429) for model {}", properties.model());
            throw new AiRateLimitedException("AI rate limit reached, try again later");
        } catch (RestClientResponseException e) {
            // Any other 4xx/5xx. Log the status only: the body may echo parts of the request.
            log.warn("Gemini returned HTTP {} for model {}", e.getStatusCode().value(), properties.model());
            throw new AiUnavailableException("AI service returned an error (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            // Timeouts, connection refused, unreadable JSON.
            log.warn("Gemini call failed for model {}: {}", properties.model(), e.getClass().getSimpleName());
            throw new AiUnavailableException("AI service is unreachable, try again later", e);
        }
    }

    /**
     * Joins the text parts of the first candidate. No candidate, or a candidate without text
     * (e.g. blocked by safety filters), counts as "no answer".
     */
    private String extractText(GeminiDtos.GenerateResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new AiUnavailableException("AI service returned no answer");
        }
        GeminiDtos.Candidate candidate = response.candidates().getFirst();
        List<GeminiDtos.Part> parts = candidate.content() == null ? null : candidate.content().parts();
        String text = parts == null ? "" : parts.stream()
                .map(GeminiDtos.Part::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining())
                .strip();
        if (text.isEmpty()) {
            log.warn("Gemini returned no text (finishReason={})", candidate.finishReason());
            throw new AiUnavailableException("AI service returned no answer");
        }
        return text;
    }
}
