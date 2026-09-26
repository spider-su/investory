package com.smartbox.investory.ryczalt.integration.fx;

import com.smartbox.investory.ryczalt.persistence.RyczaltFxRateJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltFxResolutionJpaRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads historical facts first and acquires a missing fact exactly once. */
@Service
public class RyczaltFxRateService {
  private final RyczaltFxRateJpaRepository rates;
  private final RyczaltFxResolutionJpaRepository resolutions;
  private final FxRateSourcePort source;
  private final Clock clock;

  public RyczaltFxRateService(
      RyczaltFxRateJpaRepository rates,
      RyczaltFxResolutionJpaRepository resolutions,
      FxRateSourcePort source,
      Clock clock) {
    this.rates = rates;
    this.resolutions = resolutions;
    this.source = source;
    this.clock = clock;
  }

  @Transactional
  public FxRate rateFor(String currency, LocalDate accountingDate) {
    String code = currency.toUpperCase(Locale.ROOT);
    CurrencyType currencyType = CurrencyType.valueOf(code);
    LocalDate requestedDate = FxRateDatePolicy.priorBusinessDay(accountingDate);
    return resolutions
        .findByProviderAndCurrencyAndRequestedDate(
            NbpFxRateAdapter.PROVIDER, currencyType, requestedDate)
        .map(
            stored ->
                new FxRate(
                    stored.getCurrency().name(),
                    requestedDate,
                    stored.getEffectiveDate(),
                    stored.getRate(),
                    stored.getProvider(),
                    stored.getProviderReference()))
        .orElseGet(() -> persist(source.fetch(code, requestedDate), requestedDate));
  }

  private FxRate persist(FxRate acquired, LocalDate requestedDate) {
    CurrencyType currency = CurrencyType.valueOf(acquired.currency());
    Instant now = Instant.now(clock);
    rates.insertIfAbsent(
        currency.name(),
        acquired.effectiveDate(),
        acquired.rate(),
        acquired.provider(),
        acquired.providerReference(),
        now);
    var stored =
        rates
            .findByProviderAndCurrencyAndEffectiveDate(
                acquired.provider(), currency, acquired.effectiveDate())
            .orElseThrow();
    resolutions.insertIfAbsent(
        currency.name(),
        requestedDate,
        stored.getEffectiveDate(),
        stored.getRate(),
        stored.getProvider(),
        stored.getProviderReference(),
        now);
    stored =
        rates
            .findByProviderAndCurrencyAndEffectiveDate(
                acquired.provider(), currency, acquired.effectiveDate())
            .orElseThrow();
    var resolved =
        resolutions
            .findByProviderAndCurrencyAndRequestedDate(acquired.provider(), currency, requestedDate)
            .orElseThrow();
    return new FxRate(
        resolved.getCurrency().name(),
        requestedDate,
        resolved.getEffectiveDate(),
        resolved.getRate(),
        resolved.getProvider(),
        resolved.getProviderReference());
  }
}
