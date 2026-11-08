package com.vlad.fxpulse.common;

import com.vlad.fxpulse.ingestion.IngestionController;
import com.vlad.fxpulse.ingestion.IngestionProperties;
import com.vlad.fxpulse.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With no ADMIN_TOKEN the admin API must be closed (secure default), whatever the caller sends.
 * Separate class from {@link AdminTokenInterceptorTest} because the property is set per test class.
 */
@WebMvcTest(controllers = IngestionController.class, properties = "app.admin.token=")
class AdminDisabledTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IngestionProperties.class)
    static class PropertiesConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    @Test
    void ingest_tokenNotConfigured_returns403EvenWithHeader() throws Exception {
        // When / Then
        mockMvc.perform(post("/api/admin/ingest").header("X-Admin-Token", "anything"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Admin API is disabled (ADMIN_TOKEN is not set)"))
                .andExpect(jsonPath("$.path").value("/api/admin/ingest"))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(ingestionService);
    }

    @Test
    void ingest_tokenNotConfiguredAndNoHeader_returns403() throws Exception {
        // When / Then
        mockMvc.perform(post("/api/admin/ingest"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Admin API is disabled (ADMIN_TOKEN is not set)"));

        verifyNoInteractions(ingestionService);
    }
}
