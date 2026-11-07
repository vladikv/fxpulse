package com.vlad.fxpulse.insight;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests GeminiClient against a local WireMock server (no Spring context, no real internet).
 */
class GeminiClientTest {

    private static final String API_KEY = "test-key-123";
    private static final String MODEL = "gemini-test";
    private static final String PATH = "/models/gemini-test:generateContent";
    private static final int MAX_OUTPUT_TOKENS = 256;

    @RegisterExtension
    static WireMockExtension gemini = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
            .build();

    private GeminiClient client;

    @BeforeEach
    void setUp() {
        client = new GeminiClient(propertiesWithKey(API_KEY));
    }

    private static AiProperties propertiesWithKey(String apiKey) {
        return propertiesWith(apiKey, Duration.ofSeconds(5));
    }

    private static AiProperties propertiesWith(String apiKey, Duration readTimeout) {
        return new AiProperties(gemini.baseUrl(), MODEL, apiKey, MAX_OUTPUT_TOKENS,
                Duration.ofSeconds(2), readTimeout, 50);
    }

    private static String responseWithTextAndFinishReason(String text, String finishReasonJson) {
        return """
                {"candidates": [{"content": {"role": "model", "parts": [{"text": "%s"}]}%s}]}
                """.formatted(text, finishReasonJson);
    }

    private static String responseWithText(String textPartsJson) {
        return """
                {
                  "candidates": [{
                    "content": {"role": "model", "parts": [%s]},
                    "finishReason": "STOP",
                    "index": 0
                  }],
                  "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 5, "totalTokenCount": 15},
                  "modelVersion": "gemini-test-001",
                  "responseId": "abc123"
                }
                """.formatted(textPartsJson);
    }

    // ---- success ----

    @Test
    void generate_validResponse_returnsStrippedTextAndModel() {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH))
                .willReturn(okJson(responseWithText("{\"text\": \"  USD is stable.\\n\"}"))));

        // When
        AiCompletion completion = client.generate("Summarize USD");

        // Then
        assertThat(completion.text()).isEqualTo("USD is stable.");
        assertThat(completion.model()).isEqualTo(MODEL);
    }

    @Test
    void generate_twoTextParts_joinsParts() {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(
                responseWithText("{\"text\": \"First part. \"}, {\"text\": \"Second part.\"}"))));

        // When
        AiCompletion completion = client.generate("prompt");

        // Then
        assertThat(completion.text()).isEqualTo("First part. Second part.");
    }

    // ---- request verification ----

    @Test
    void generate_validCall_sendsApiKeyHeaderAndJsonContentType() {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(responseWithText("{\"text\": \"ok\"}"))));

        // When
        client.generate("prompt");

        // Then
        gemini.verify(1, postRequestedFor(urlEqualTo(PATH))
                .withHeader("x-goog-api-key", equalTo(API_KEY))
                .withHeader("Content-Type", com.github.tomakehurst.wiremock.client.WireMock.containing("application/json")));
    }

    @Test
    void generate_validCall_sendsPromptAndMaxOutputTokensInBody() {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(responseWithText("{\"text\": \"ok\"}"))));

        // When
        client.generate("Explain the EUR trend");

        // Then
        gemini.verify(1, postRequestedFor(urlEqualTo(PATH))
                .withRequestBody(matchingJsonPath("$.contents[0].parts[0].text", equalTo("Explain the EUR trend")))
                .withRequestBody(matchingJsonPath("$.generationConfig.maxOutputTokens", equalTo("256"))));
    }

    @Test
    void generate_validCall_doesNotPutApiKeyInUrl() {
        // Given
        gemini.stubFor(post(anyUrl()).willReturn(okJson(responseWithText("{\"text\": \"ok\"}"))));

        // When
        client.generate("prompt");

        // Then
        var requests = gemini.findAll(anyRequestedFor(anyUrl()));
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().getUrl()).doesNotContain(API_KEY).doesNotContain("key=");
        assertThat(requests.getFirst().queryParameter("key").isPresent()).isFalse();
    }

    // ---- errors ----

    @Test
    void generate_http429_throwsAiRateLimitedException() {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(429).withBody("quota exceeded")));

        // When / Then
        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOf(AiRateLimitedException.class)
                .hasMessage("AI rate limit reached, try again later");
    }

    @Test
    void generate_http500_throwsAiUnavailableWithoutLeakingBody() {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(500)
                .withBody("{\"error\":\"internal secret-token-xyz\"}")));

        // When / Then
        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageNotContaining("secret-token-xyz")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void generate_http400_throwsAiUnavailableException() {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(400)
                .withBody("{\"error\":{\"message\":\"API key not valid secret-token-xyz\"}}")));

        // When / Then
        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageNotContaining("secret-token-xyz");
    }

    @Test
    void generate_readTimeout_throwsAiUnavailableException() {
        // Given: response delayed longer than the 300 ms read timeout (only this test uses a short one)
        GeminiClient impatientClient = new GeminiClient(propertiesWith(API_KEY, Duration.ofMillis(300)));
        gemini.stubFor(post(urlEqualTo(PATH)).willReturn(
                okJson(responseWithText("{\"text\": \"late\"}")).withFixedDelay(1500)));

        // When / Then
        assertThatThrownBy(() -> impatientClient.generate("prompt"))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void generate_emptyCandidates_throwsNoAnswer() {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH)).willReturn(okJson("{\"candidates\": [], \"modelVersion\": \"x\"}")));

        // When / Then
        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessage("AI service returned no answer");
    }

    @Test
    void generate_candidateWithoutContent_throwsNoAnswer() {
        // Given: e.g. blocked by safety filters
        gemini.stubFor(post(urlEqualTo(PATH))
                .willReturn(okJson("{\"candidates\": [{\"finishReason\": \"SAFETY\"}]}")));

        // When / Then
        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessage("AI service returned no answer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAX_TOKENS", "SAFETY"})
    void generate_nonStopFinishReasonWithText_throwsIncompleteAnswer(String reason) {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(
                responseWithTextAndFinishReason("Partial text", ", \"finishReason\": \"" + reason + "\""))));

        // When / Then
        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessage("AI service returned an incomplete answer, try again later");
    }

    @Test
    void generate_finishReasonStop_returnsText() {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(
                responseWithTextAndFinishReason("Complete text", ", \"finishReason\": \"STOP\""))));

        // When
        AiCompletion completion = client.generate("prompt");

        // Then
        assertThat(completion.text()).isEqualTo("Complete text");
    }

    @Test
    void generate_textWithoutFinishReason_returnsText() {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(
                responseWithTextAndFinishReason("No reason given", ""))));

        // When
        AiCompletion completion = client.generate("prompt");

        // Then
        assertThat(completion.text()).isEqualTo("No reason given");
    }

    @Test
    void generate_whitespaceOnlyText_throwsNoAnswer() {
        // Given
        gemini.stubFor(post(urlEqualTo(PATH))
                .willReturn(okJson(responseWithText("{\"text\": \"  \\n\\t \"}"))));

        // When / Then
        assertThatThrownBy(() -> client.generate("prompt"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessage("AI service returned no answer");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void generate_missingApiKey_throwsAiUnavailableAndSendsNoRequest(String apiKey) {
        // Given
        GeminiClient clientWithoutKey = new GeminiClient(propertiesWithKey(apiKey));

        // When / Then
        assertThatThrownBy(() -> clientWithoutKey.generate("prompt"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("GEMINI_API_KEY");
        gemini.verify(0, anyRequestedFor(anyUrl()));
    }
}
