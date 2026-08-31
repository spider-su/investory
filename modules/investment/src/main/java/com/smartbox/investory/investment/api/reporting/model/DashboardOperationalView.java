package com.smartbox.investory.investment.api.reporting.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public record DashboardOperationalView(
    ImportContext importContext,
    FreshnessContext freshness,
    FxContext fx,
    ValuationContext valuation,
    YahooContext yahoo) {

  public record ImportContext(
      ZonedDateTime finishedAt, int rowsApplied, int rowsFailed, long accountsProcessed) {
    public boolean available() {
      return finishedAt != null;
    }
  }

  public record FreshnessContext(
      OffsetDateTime lastUpdate,
      ZonedDateTime latestTransaction,
      LocalDate latestValuation,
      long accountsUpdated) {}

  public record FxContext(
      CurrencyType baseCurrency,
      Map<CurrencyType, Double> rates,
      LocalDate ratesUpdated,
      int currencyCount) {
    public FxContext {
      rates = com.smartbox.investory.shared.util.CollectionUtils.immutableMapOrEmpty(rates);
    }
  }

  public record ValuationContext(
      String state,
      long affectedCount,
      long staleCount,
      long unreliableCount,
      List<String> symbols) {
    public ValuationContext {
      symbols = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(symbols);
    }
  }

  public record YahooContext(ZonedDateTime lastExport, boolean upToDate) {
    public boolean neverExported() {
      return lastExport == null;
    }
  }
}
