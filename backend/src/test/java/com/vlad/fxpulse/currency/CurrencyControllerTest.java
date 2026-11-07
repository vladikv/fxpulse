package com.vlad.fxpulse.currency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The MVC slice also loads @RestControllerAdvice beans, so GlobalExceptionHandler is active here.
@WebMvcTest(CurrencyController.class)
class CurrencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrencyService currencyService;

    @Test
    void listsCurrencies() throws Exception {
        when(currencyService.findAll()).thenReturn(List.of(
                new CurrencyDto("EUR", "euro"),
                new CurrencyDto("USD", "dolar amerykański")));

        mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("EUR"))
                .andExpect(jsonPath("$[0].name").value("euro"))
                .andExpect(jsonPath("$[1].code").value("USD"));
    }

    @Test
    void unexpectedErrorReturnsGenericApiError() throws Exception {
        when(currencyService.findAll()).thenThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"))
                .andExpect(jsonPath("$.path").value("/api/currencies"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unknownEndpointReturnsNotFoundApiError() throws Exception {
        mockMvc.perform(get("/api/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/nothing-here"));
    }
}
