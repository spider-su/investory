package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PortfolioCurrencyBreakdownId implements Serializable {
  private Long portfolioId;
  private String metricType;
  private CurrencyType currency;
}
