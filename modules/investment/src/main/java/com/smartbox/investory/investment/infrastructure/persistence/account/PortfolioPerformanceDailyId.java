package com.smartbox.investory.investment.infrastructure.persistence.account;

import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PortfolioPerformanceDailyId implements Serializable {
  private Long portfolioId;
  private LocalDate date;
}
