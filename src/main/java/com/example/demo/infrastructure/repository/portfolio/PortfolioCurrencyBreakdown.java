package com.example.demo.infrastructure.repository.portfolio;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(PortfolioCurrencyBreakdownId.class)
@Table(name = "app_v_portfolio_currency_breakdown")
public class PortfolioCurrencyBreakdown {

  @Id
  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "base_currency", nullable = false)
  private CurrencyType baseCurrency;

  @Id
  @Column(name = "metric_type", nullable = false)
  private String metricType;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false)
  private CurrencyType currency;

  @Column(name = "amount_local")
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private Double amountLocal;

  @Column(name = "amount_in_base_currency")
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private Double amountInBaseCurrency;

  @Column(name = "updated_at", nullable = false)
  private java.time.ZonedDateTime updatedAt;
}
