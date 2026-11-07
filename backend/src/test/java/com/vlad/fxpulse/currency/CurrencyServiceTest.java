package com.vlad.fxpulse.currency;

import com.vlad.fxpulse.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock
    private CurrencyRepository currencyRepository;

    @InjectMocks
    private CurrencyService currencyService;

    @Test
    void mapsEntitiesToDtosInRepositoryOrder() {
        when(currencyRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(
                new Currency("EUR", "euro"),
                new Currency("USD", "dolar amerykański")));

        assertThat(currencyService.findAll()).containsExactly(
                new CurrencyDto("EUR", "euro"),
                new CurrencyDto("USD", "dolar amerykański"));
    }

    @Test
    void requireExistsPassesForKnownCode() {
        when(currencyRepository.existsById("EUR")).thenReturn(true);

        assertThatCode(() -> currencyService.requireExists("EUR")).doesNotThrowAnyException();
    }

    @Test
    void requireExistsThrowsNotFoundForUnknownCode() {
        when(currencyRepository.existsById("XYZ")).thenReturn(false);

        assertThatThrownBy(() -> currencyService.requireExists("XYZ"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Unknown currency: XYZ");
    }
}
