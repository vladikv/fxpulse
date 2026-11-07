package com.vlad.fxpulse.common;

import com.vlad.fxpulse.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test: the full app starts with springdoc and the generated spec lists every endpoint.
 * Needs the whole context (springdoc scans all controllers), hence @SpringBootTest + Testcontainers.
 */
@SpringBootTest(properties = "app.ingestion.scheduler-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsListAllEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FxPulse API"))
                .andExpect(jsonPath("$.paths['/api/currencies'].get").exists())
                .andExpect(jsonPath("$.paths['/api/rates/{code}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/rates/{code}/stats'].get").exists())
                .andExpect(jsonPath("$.paths['/api/convert'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/ingest'].post").exists())
                .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
