package com.smartbox.investory.ryczalt.integration.fx;

import com.smartbox.investory.ryczalt.persistence.RyczaltFxRateEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltFxRateJpaRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads historical facts first and acquires a missing fact exactly once. */
@Service
public class RyczaltFxRateService {
  private final RyczaltFxRateJpaRepository rates;
  private final FxRateSourcePort source;

  public RyczaltFxRateService(RyczaltFxRateJpaRepository rates, FxRateSourcePort source) {
    this.rates = rates;
    this.source = source;
  }

  @Transactional
  public FxRate rateFor(String currency, LocalDate accountingDate) {
    String code = currency.toUpperCase(Locale.ROOT);
    CurrencyType currencyType = CurrencyType.valueOf(code);
    LocalDate requestedDate = FxRateDatePolicy.priorBusinessDay(accountingDate);
    return rates
        .findTopByProviderAndCurrencyAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
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
    RyczaltFxRateEntity stored =
        rates
            .findByProviderAndCurrencyAndEffectiveDate(
                acquired.provider(),
                CurrencyType.valueOf(acquired.currency()),
                acquired.effectiveDate())
            .orElseGet(
                () ->
                    rates.save(
                        new RyczaltFxRateEntity(
                            CurrencyType.valueOf(acquired.currency()),
                            acquired.effectiveDate(),
                            acquired.rate(),
                            acquired.provider(),
                            acquired.providerReference(),
                            Instant.now())));
    return new FxRate(
        stored.getCurrency().name(),
        requestedDate,
        stored.getEffectiveDate(),
        stored.getRate(),
        stored.getProvider(),
        stored.getProviderReference());
  }
}
