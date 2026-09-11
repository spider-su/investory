package com.smartbox.investory.investment.valuation.fx;

import com.smartbox.investory.investment.port.fx.FxRateProvider.FxHistoryQuote;
import com.smartbox.investory.investment.valuation.fx.persistence.CurrencyRateEntity;
import com.smartbox.investory.investment.valuation.fx.persistence.CurrencyRateRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DailyFxRateService {
  private static final LocalDate DEFAULT_START = LocalDate.of(2024, 9, 1);
  private static final List<CurrencyType> CURRENCIES =
      List.of(CurrencyType.USD, CurrencyType.EUR, CurrencyType.PLN);
  private final CurrencyRateRepository repository;

  public void replaceRange(List<FxHistoryQuote> quotes, LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to)) return;
    NavigableMap<LocalDate, Map<CurrencyType, BigDecimal>> usd = new TreeMap<>();
    for (FxHistoryQuote quote : Optional.ofNullable(quotes).orElse(List.of())) {
      if (quote == null
          || quote.providerDate() == null
          || quote.rate() == null
          || quote.rate().signum() <= 0) continue;
      usd.computeIfAbsent(quote.providerDate(), ignored -> new EnumMap<>(CurrencyType.class))
          .put(quote.target(), quote.rate());
    }
    for (Map.Entry<LocalDate, Map<CurrencyType, BigDecimal>> entry :
        usd.subMap(from, true, to, true).entrySet()) {
      LocalDate date = entry.getKey();
      Map<CurrencyType, BigDecimal> rates = entry.getValue();
      if (!rates.containsKey(CurrencyType.EUR) || !rates.containsKey(CurrencyType.PLN)) continue;
      rates.put(CurrencyType.USD, BigDecimal.ONE);
      for (CurrencyType base : CURRENCIES) {
        for (CurrencyType target : CURRENCIES) {
          if (base == target) continue;
          BigDecimal rate = rates.get(target).divide(rates.get(base), 18, RoundingMode.HALF_UP);
          upsert(date, base, target, rate);
        }
      }
    }
  }

  public LocalDate defaultStart() {
    return DEFAULT_START;
  }

  public LocalDate refreshStart(LocalDate effectiveDate) {
    if (repository.countByPurpose("VALUATION") == 0) return DEFAULT_START;
    return effectiveDate.minusDays(7).isBefore(DEFAULT_START)
        ? DEFAULT_START
        : effectiveDate.minusDays(7);
  }

  private void upsert(LocalDate date, CurrencyType base, CurrencyType target, BigDecimal rate) {
    CurrencyRateEntity row =
        repository
            .findByRateDateAndBaseAndToCurrencyAndPurpose(date, base, target, "VALUATION")
            .orElseGet(CurrencyRateEntity::new);
    row.setRateDate(date);
    row.setBase(base);
    row.setToCurrency(target);
    row.setRate(rate.setScale(8, RoundingMode.HALF_UP));
    row.setPurpose("VALUATION");
    row.setSource("NBP");
    row.setMethod("OBSERVED");
    row.setSourceRateDate(date);
    row.setSourceReference("NBP:" + date);
    repository.save(row);
  }
}
