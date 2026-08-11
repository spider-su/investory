package com.example.demo.infrastructure.repository.portfolio;

import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PortfolioMonthlyPerformanceId implements Serializable {
  private Long portfolioId;
  private LocalDate month;
}
