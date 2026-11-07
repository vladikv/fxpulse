package com.vlad.fxpulse.rate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/convert")
public class ConversionController {

    private static final String CODE_REGEX = "[A-Za-z]{3}";
    private static final String CODE_MESSAGE = "must be a 3-letter currency code";

    private final ConversionService conversionService;

    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /** GET /api/convert?from=EUR&to=USD&amount=100&date=2026-11-06 (date optional, PLN allowed). */
    @GetMapping
    public ConversionResponse convert(
            @RequestParam @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String from,
            @RequestParam @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String to,
            // @Digits caps the size: without it, amount=1E+999999 is a "valid" positive number whose
            // multiplication and division would burn CPU and memory on the server.
            @RequestParam @NotNull @Positive @Digits(integer = 15, fraction = 6) BigDecimal amount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return conversionService.convert(from, to, amount, date);
    }
}
