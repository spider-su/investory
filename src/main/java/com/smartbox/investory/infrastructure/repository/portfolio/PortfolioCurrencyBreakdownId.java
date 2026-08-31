package com.smartbox.investory.infrastructure.repository.portfolio;

import com.smartbox.investory.infrastructure.CurrencyType;
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
