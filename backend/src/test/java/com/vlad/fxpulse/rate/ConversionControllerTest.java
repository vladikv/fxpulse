package com.vlad.fxpulse.rate;

import com.vlad.fxpulse.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversionController.class)
class ConversionControllerTest {

    private static final LocalDate FRIDAY = LocalDate.parse("2026-11-06");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversionService conversionService;

    @Test
    void convertsAmount() throws Exception {
        when(conversionService.convert("EUR", "USD", new BigDecimal("100"), FRIDAY))
                .thenReturn(new ConversionResponse("EUR", "USD", new BigDecimal("100"),
                        new BigDecimal("106.25"), new BigDecimal("1.062500"), FRIDAY, FRIDAY));

        mockMvc.perform(get("/api/convert")
                        .param("from", "EUR").param("to", "USD")
                        .param("amount", "100").param("date", "2026-11-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(106.25))
                .andExpect(jsonPath("$.rate").value(1.0625))
                .andExpect(jsonPath("$.fromRateDate").value("2026-11-06"));
    }

    @Test
    void rejectsNonPositiveAmount() throws Exception {
        mockMvc.perform(get("/api/convert").param("from", "EUR").param("to", "USD").param("amount", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("amount: must be greater than 0"));

        verifyNotCalled();
    }

    @Test
    void rejectsMissingAmount() throws Exception {
        mockMvc.perform(get("/api/convert").param("from", "EUR").param("to", "USD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'amount'"));

        verifyNotCalled();
    }

    @Test
    void rejectsNonNumericAmount() throws Exception {
        mockMvc.perform(get("/api/convert").param("from", "EUR").param("to", "USD").param("amount", "ten"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'ten' for parameter 'amount'"));
    }

    @Test
    void rejectsMalformedCode() throws Exception {
        mockMvc.perform(get("/api/convert").param("from", "EURO").param("to", "USD").param("amount", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("from: must be a 3-letter currency code"));

        verifyNotCalled();
    }

    @Test
    void missingRateReturns404() throws Exception {
        when(conversionService.convert("EUR", "PLN", BigDecimal.ONE, null))
                .thenThrow(new NotFoundException("No EUR rate on or before 2026-11-07"));

        mockMvc.perform(get("/api/convert").param("from", "EUR").param("to", "PLN").param("amount", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No EUR rate on or before 2026-11-07"));
    }

    private void verifyNotCalled() {
        verify(conversionService, never()).convert(anyString(), anyString(), any(), any());
    }
}
