package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Historical closed-position result already converted using the close-date FX rate. */
@Entity
@Immutable
@Getter
@NoArgsConstructor
@Table(name = "app_v_portfolio_tax_year_realized")
public class PortfolioTaxYearRealizedEntity {
  @Id
  @Column(name = "portfolio_tax_year_key")
  private String key;

  @Column(name = "portfolio_id")
  private Long portfolioId;

  @Column(name = "tax_year")
  private Integer taxYear;

  @Column(name = "realized_result")
  private BigDecimal realizedResult;
}
