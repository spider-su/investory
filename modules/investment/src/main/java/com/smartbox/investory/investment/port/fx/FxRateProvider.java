package com.smartbox.investory.investment.port.fx;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Outbound FX boundary. Credentials and provider selection are adapter concerns. */
public interface FxRateProvider {

  List<FxQuote> fetchRates(FxRequest request);

  record FxRequest(CurrencyType base, List<CurrencyType> targets, LocalDate effectiveDate) {
    public FxRequest {
      targets = targets == null ? List.of() : List.copyOf(targets);
    }
  }

  record FxQuote(
      CurrencyType base,
      CurrencyType target,
      BigDecimal rate,
      LocalDate effectiveDate,
      LocalDate providerDate) {}
}
