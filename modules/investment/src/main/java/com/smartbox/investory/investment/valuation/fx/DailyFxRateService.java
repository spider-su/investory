package com.smartbox.investory.investment.valuation.fx;

import com.smartbox.investory.investment.port.fx.FxRateProvider.FxHistoryQuote;
import com.smartbox.investory.investment.valuation.fx.persistence.DailyFxRateEntity;
import com.smartbox.investory.investment.valuation.fx.persistence.DailyFxRateRepository;
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
  private final DailyFxRateRepository repository;

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
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      Map<CurrencyType, BigDecimal> rates = usd.get(date);
      if (rates == null) rates = nearestRates(usd, date);
      if (rates == null
          || !rates.containsKey(CurrencyType.EUR)
          || !rates.containsKey(CurrencyType.PLN)) continue;
      rates.put(CurrencyType.USD, BigDecimal.ONE);
      for (CurrencyType base : CURRENCIES) {
        for (CurrencyType target : CURRENCIES) {
          if (base == target) continue;
          BigDecimal rate = rates.get(target).divide(rates.get(base), 18, RoundingMode.HALF_UP);
          upsert(
              date,
              base,
              target,
              rate,
              usd.containsKey(date) ? "OBSERVED" : "CARRY_FORWARD",
              usd.containsKey(date) ? date : nearestDate(usd, date));
        }
      }
    }
  }

  public LocalDate defaultStart() {
    return DEFAULT_START;
  }

  public LocalDate refreshStart(LocalDate effectiveDate) {
    if (repository.count() == 0) return DEFAULT_START;
    return effectiveDate.minusDays(7).isBefore(DEFAULT_START)
        ? DEFAULT_START
        : effectiveDate.minusDays(7);
  }

  private Map<CurrencyType, BigDecimal> nearestRates(
      NavigableMap<LocalDate, Map<CurrencyType, BigDecimal>> rates, LocalDate date) {
    Map.Entry<LocalDate, Map<CurrencyType, BigDecimal>> entry = rates.floorEntry(date);
    if (entry == null) entry = rates.ceilingEntry(date);
    return entry == null ? null : entry.getValue();
  }

  private LocalDate nearestDate(
      NavigableMap<LocalDate, Map<CurrencyType, BigDecimal>> rates, LocalDate date) {
    Map.Entry<LocalDate, Map<CurrencyType, BigDecimal>> entry = rates.floorEntry(date);
    if (entry == null) entry = rates.ceilingEntry(date);
    return entry == null ? null : entry.getKey();
  }

  private void upsert(
      LocalDate date,
      CurrencyType base,
      CurrencyType target,
      BigDecimal rate,
      String method,
      LocalDate sourceDate) {
    DailyFxRateEntity row =
        repository
            .findByRateDateAndBaseAndToCurrency(date, base, target)
            .orElseGet(DailyFxRateEntity::new);
    row.setRateDate(date);
    row.setBase(base);
    row.setToCurrency(target);
    row.setRate(rate.setScale(8, RoundingMode.HALF_UP));
    row.setSource("NBP");
    row.setMethod(method);
    row.setSourceRateDate(sourceDate);
    row.setSourceReference("NBP:" + sourceDate);
    repository.save(row);
  }
}
