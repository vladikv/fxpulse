package com.vlad.fxpulse.common;

import com.vlad.fxpulse.currency.CurrencyController;
import com.vlad.fxpulse.currency.CurrencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS rules from {@link CorsConfig}, checked on a real endpoint. The MVC slice loads WebMvcConfigurer
 * beans, and the origins come from application.yml's default ({@code http://localhost:5173}).
 */
@WebMvcTest(CurrencyController.class)
class CorsConfigTest {

    private static final String FRONTEND = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrencyService currencyService;

    @Test
    void allowedOriginGetsCorsHeader() throws Exception {
        when(currencyService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/currencies").header(HttpHeaders.ORIGIN, FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND));
    }

    @Test
    void unknownOriginIsRejected() throws Exception {
        mockMvc.perform(get("/api/currencies").header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightForGetIsAllowed() throws Exception {
        mockMvc.perform(options("/api/currencies")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    @Test
    void preflightForPostIsRejected() throws Exception {
        // Only GET is allowed, so a page cannot make the browser send a POST (e.g. to /api/admin/ingest).
        mockMvc.perform(options("/api/currencies")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
