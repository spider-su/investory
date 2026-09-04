package com.smartbox.investory.investment.valuation.fx.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface FxRateResolutionRow {

  /**
   * The valuation date the row was resolved for. Only populated by batch/range queries that resolve
   * several dates at once; single-date queries leave it {@code null}.
   */
  LocalDate getValuationDate();

  String getSourceCurrency();

  String getTargetCurrency();

  BigDecimal getFxRateToTarget();

  String getSource();

  String getRateMethod();

  String getRateSource();

  LocalDate getSourceRateDate();

  Integer getAgeDays();

  String getConversionStatus();
}
