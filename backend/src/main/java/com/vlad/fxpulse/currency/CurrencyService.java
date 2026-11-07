package com.vlad.fxpulse.currency;

import com.vlad.fxpulse.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CurrencyService {

    private final CurrencyRepository currencyRepository;

    public CurrencyService(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    /** All known currencies, sorted by code. */
    public List<CurrencyDto> findAll() {
        return currencyRepository.findAllByOrderByCodeAsc().stream()
                .map(CurrencyDto::from)
                .toList();
    }

    /**
     * Fails with 404 when the code is unknown, so other services can say "unknown currency"
     * instead of silently returning an empty time series.
     */
    public void requireExists(String code) {
        if (!currencyRepository.existsById(code)) {
            throw new NotFoundException("Unknown currency: " + code);
        }
    }
}
