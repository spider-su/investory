package com.smartbox.investory.infrastructure.repository.portfolio;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PortfolioAssetAllocationId implements Serializable {
  private Long portfolioId;
  private Long assetId;
}
