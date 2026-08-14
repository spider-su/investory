package com.smartbox.investory.infrastructure.repository.portfolio;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SymbolPerformanceId implements Serializable {
  private Long portfolioId;
  private Long assetId;
}
