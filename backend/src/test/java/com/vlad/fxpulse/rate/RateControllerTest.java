package com.vlad.fxpulse.rate;

import com.vlad.fxpulse.common.BadRequestException;
import com.vlad.fxpulse.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RateController.class)
class RateControllerTest {

    private static final LocalDate FROM = LocalDate.parse("2026-11-05");
    private static final LocalDate TO = LocalDate.parse("2026-11-06");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateService rateService;

    @Test
    void returnsTimeSeries() throws Exception {
        when(rateService.getSeries("EUR", FROM, TO)).thenReturn(new RateSeriesResponse("EUR", FROM, TO, List.of(
                new RatePoint(FROM, new BigDecimal("4.2500")),
                new RatePoint(TO, new BigDecimal("4.2600")))));

        mockMvc.perform(get("/api/rates/EUR").param("from", "2026-11-05").param("to", "2026-11-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EUR"))
                .andExpect(jsonPath("$.from").value("2026-11-05"))
                .andExpect(jsonPath("$.rates.length()").value(2))
                .andExpect(jsonPath("$.rates[0].date").value("2026-11-05"))
                .andExpect(jsonPath("$.rates[0].mid").value(4.25));
    }

    @Test
    void passesNullWhenDatesAreOmitted() throws Exception {
        when(rateService.getSeries("EUR", null, null))
                .thenReturn(new RateSeriesResponse("EUR", FROM, TO, List.of()));

        mockMvc.perform(get("/api/rates/EUR"))
                .andExpect(status().isOk());

        verify(rateService).getSeries(anyString(), isNull(), isNull());
    }

    @Test
    void returnsStats() throws Exception {
        when(rateService.getStats("EUR", FROM, TO)).thenReturn(new RateStatsResponse("EUR", FROM, TO, 2,
                new BigDecimal("4.2500"), new BigDecimal("4.2600"), new BigDecimal("4.2550"),
                new BigDecimal("4.2500"), new BigDecimal("4.2600"), new BigDecimal("0.24"),
                new BigDecimal("0.0050")));

        mockMvc.perform(get("/api/rates/EUR/stats").param("from", "2026-11-05").param("to", "2026-11-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.avg").value(4.255))
                .andExpect(jsonPath("$.changePercent").value(0.24))
                .andExpect(jsonPath("$.volatility").value(0.005));
    }

    @Test
    void rejectsMalformedCurrencyCode() throws Exception {
        mockMvc.perform(get("/api/rates/EU1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("code: must be a 3-letter currency code"));

        verify(rateService, never()).getSeries(anyString(), any(), any());
    }

    @Test
    void rejectsUnparsableDate() throws Exception {
        mockMvc.perform(get("/api/rates/EUR/stats").param("from", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'yesterday' for parameter 'from'"));
    }

    @Test
    void unknownCurrencyReturns404() throws Exception {
        when(rateService.getSeries("XYZ", null, null)).thenThrow(new NotFoundException("Unknown currency: XYZ"));

        mockMvc.perform(get("/api/rates/XYZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Unknown currency: XYZ"))
                .andExpect(jsonPath("$.path").value("/api/rates/XYZ"));
    }

    @Test
    void invalidRangeReturns400() throws Exception {
        when(rateService.getSeries("EUR", TO, FROM))
                .thenThrow(new BadRequestException("'from' (2026-11-06) must not be after 'to' (2026-11-05)"));

        mockMvc.perform(get("/api/rates/EUR").param("from", "2026-11-06").param("to", "2026-11-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' (2026-11-06) must not be after 'to' (2026-11-05)"));
    }
}
