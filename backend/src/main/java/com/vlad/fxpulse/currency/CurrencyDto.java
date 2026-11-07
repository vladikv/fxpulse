package com.vlad.fxpulse.currency;

/** API view of a currency; the entity itself never leaves the service layer. */
public record CurrencyDto(String code, String name) {

    static CurrencyDto from(Currency currency) {
        return new CurrencyDto(currency.getCode(), currency.getName());
    }
}
