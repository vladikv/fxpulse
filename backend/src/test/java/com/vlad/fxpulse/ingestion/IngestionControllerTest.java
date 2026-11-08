package com.vlad.fxpulse.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IngestionController.class, properties = "app.admin.token=test-token")
class IngestionControllerTest {

    // The MVC slice doesn't run @ConfigurationPropertiesScan, so bind the properties record explicitly.
    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IngestionProperties.class)
    static class PropertiesConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    private static final String TOKEN = "test-token";

    private static final IngestionResult RESULT = new IngestionResult(
            LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-06"), 33, 66, 66, List.of());

    @Test
    void ingestsRequestedNumberOfDays() throws Exception {
        when(ingestionService.ingest(30)).thenReturn(RESULT);

        mockMvc.perform(post("/api/admin/ingest").param("days", "30").header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-05"))
                .andExpect(jsonPath("$.currencies").value(33))
                .andExpect(jsonPath("$.ratesInserted").value(66))
                .andExpect(jsonPath("$.failedCurrencies").isEmpty());
    }

    @Test
    void usesConfiguredBackfillDaysWhenParameterIsMissing() throws Exception {
        when(ingestionService.ingest(365)).thenReturn(RESULT); // app.ingestion.backfill-days in application.yml

        mockMvc.perform(post("/api/admin/ingest").header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk());

        verify(ingestionService).ingest(365);
    }

    @Test
    void rejectsDaysOutsideAllowedRange() throws Exception {
        mockMvc.perform(post("/api/admin/ingest").param("days", "0").header("X-Admin-Token", TOKEN))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/ingest").param("days", "3651").header("X-Admin-Token", TOKEN))
                .andExpect(status().isBadRequest());

        verify(ingestionService, never()).ingest(anyInt());
    }
}
