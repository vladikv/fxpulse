package com.vlad.fxpulse.insight;

import com.vlad.fxpulse.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsightController.class)
class InsightControllerTest {

    private static final LocalDate FROM = LocalDate.parse("2026-10-01");
    private static final LocalDate TO = LocalDate.parse("2026-10-31");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsightService insightService;

    @Test
    void insight_validRequest_returnsAllFields() throws Exception {
        when(insightService.getInsight("EUR", FROM, TO)).thenReturn(new InsightResponse("EUR", FROM, TO,
                "EUR rose slightly.", "gemini-x", Instant.parse("2026-11-06T12:00:00Z"), true));

        mockMvc.perform(get("/api/insights/EUR").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EUR"))
                .andExpect(jsonPath("$.from").value("2026-10-01"))
                .andExpect(jsonPath("$.to").value("2026-10-31"))
                .andExpect(jsonPath("$.content").value("EUR rose slightly."))
                .andExpect(jsonPath("$.model").value("gemini-x"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-06T12:00:00Z"))
                .andExpect(jsonPath("$.cached").value(true));
    }

    @Test
    void insight_datesOmitted_passesNulls() throws Exception {
        when(insightService.getInsight("EUR", null, null)).thenReturn(new InsightResponse("EUR", FROM, TO,
                "text", "m", Instant.parse("2026-11-06T12:00:00Z"), false));

        mockMvc.perform(get("/api/insights/EUR")).andExpect(status().isOk());

        verify(insightService).getInsight(anyString(), isNull(), isNull());
    }

    @Test
    void insight_malformedCode_returns400ApiError() throws Exception {
        mockMvc.perform(get("/api/insights/EU1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/insights/EU1"));

        verifyNoInteractions(insightService);
    }

    @Test
    void insight_unparsableDate_returns400() throws Exception {
        mockMvc.perform(get("/api/insights/EUR").param("from", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(insightService);
    }

    @Test
    void insight_serviceThrowsNotFound_returns404() throws Exception {
        when(insightService.getInsight("XYZ", null, null)).thenThrow(new NotFoundException("Unknown currency XYZ"));

        mockMvc.perform(get("/api/insights/XYZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Unknown currency XYZ"));
    }

    @Test
    void insight_aiRateLimited_returns429ApiError() throws Exception {
        when(insightService.getInsight("EUR", null, null))
                .thenThrow(new AiRateLimitedException("AI rate limit reached, try again later"));

        mockMvc.perform(get("/api/insights/EUR"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("AI rate limit reached, try again later"));
    }

    @Test
    void insight_aiUnavailable_returns503ApiError() throws Exception {
        when(insightService.getInsight("EUR", null, null))
                .thenThrow(new AiUnavailableException("AI service returned no answer"));

        mockMvc.perform(get("/api/insights/EUR"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("AI service returned no answer"));
    }
}
