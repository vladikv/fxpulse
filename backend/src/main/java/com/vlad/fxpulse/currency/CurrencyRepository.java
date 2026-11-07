package com.vlad.fxpulse.currency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurrencyRepository extends JpaRepository<Currency, String> {

    List<Currency> findAllByOrderByCodeAsc();
}
