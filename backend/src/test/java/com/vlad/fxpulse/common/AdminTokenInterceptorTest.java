package com.vlad.fxpulse.common;

import com.vlad.fxpulse.currency.CurrencyController;
import com.vlad.fxpulse.currency.CurrencyService;
import com.vlad.fxpulse.ingestion.IngestionController;
import com.vlad.fxpulse.ingestion.IngestionProperties;
import com.vlad.fxpulse.ingestion.IngestionResult;
import com.vlad.fxpulse.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Token protection of /api/admin/** when ADMIN_TOKEN is configured. The MVC slice loads
 * {@link AdminConfig} (a WebMvcConfigurer), so the real interceptor runs in front of the controller.
 */
@WebMvcTest(controllers = {IngestionController.class, CurrencyController.class},
        properties = "app.admin.token=test-token")
class AdminTokenInterceptorTest {

    // The MVC slice doesn't run @ConfigurationPropertiesScan, so bind IngestionProperties explicitly.
    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IngestionProperties.class)
    static class PropertiesConfig {
    }

    private static final String HEADER = "X-Admin-Token";
    private static final IngestionResult RESULT = new IngestionResult(
            LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-06"), 33, 66, 66, List.of());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    @MockitoBean
    private CurrencyService currencyService;

    @Test
    void ingest_correctToken_returnsOkAndCallsService() throws Exception {
        // Given
        when(ingestionService.ingest(30)).thenReturn(RESULT);

        // When / Then
        mockMvc.perform(post("/api/admin/ingest").param("days", "30").header(HEADER, "test-token"))
                .andExpect(status().isOk());

        verify(ingestionService).ingest(30);
    }

    @Test
    void ingest_headerMissing_returns401AndDoesNotCallService() throws Exception {
        // When / Then
        mockMvc.perform(post("/api/admin/ingest").param("days", "30"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Missing or invalid X-Admin-Token header"))
                .andExpect(jsonPath("$.path").value("/api/admin/ingest"))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(ingestionService);
    }

    /** Includes a prefix and a longer token: only an exact match may pass. */
    @ParameterizedTest
    @ValueSource(strings = {"wrong", "test-tok", "test-token-extra", "TEST-TOKEN", ""})
    void ingest_wrongToken_returns401AndDoesNotCallService(String sent) throws Exception {
        // When / Then
        mockMvc.perform(post("/api/admin/ingest").param("days", "30").header(HEADER, sent))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid X-Admin-Token header"));

        verifyNoInteractions(ingestionService);
    }

    @Test
    void currencies_noAdminHeader_isNotAffected() throws Exception {
        // Given
        when(currencyService.findAll()).thenReturn(List.of());

        // When / Then: the interceptor is registered only for /api/admin/**
        mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isOk());
    }
}
