package com.vlad.fxpulse.currency;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/currencies")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    /** GET /api/currencies - every currency from NBP table A that has been ingested. */
    @GetMapping
    public List<CurrencyDto> list() {
        return currencyService.findAll();
    }
}
