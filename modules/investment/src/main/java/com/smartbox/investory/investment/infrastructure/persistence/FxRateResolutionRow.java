package com.smartbox.investory.investment.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface FxRateResolutionRow {
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
