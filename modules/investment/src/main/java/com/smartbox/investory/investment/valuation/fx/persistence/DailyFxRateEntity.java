package com.smartbox.investory.investment.valuation.fx.persistence;

import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

@Data
@NoArgsConstructor
@Entity
@Table(
    name = "fx_daily_rates",
    uniqueConstraints = @UniqueConstraint(columnNames = {"rate_date", "base", "to_currency"}))
public class DailyFxRateEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "rate_date", nullable = false)
  private LocalDate rateDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private CurrencyType base;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_currency", nullable = false, length = 3)
  private CurrencyType toCurrency;

  @Column(nullable = false, precision = 20, scale = 8)
  private BigDecimal rate;

  @Column(nullable = false, length = 32)
  private String source;

  @Column(nullable = false, length = 32)
  private String method;

  @Column(name = "source_rate_date")
  private LocalDate sourceRateDate;

  @Column(name = "source_reference", length = 256)
  private String sourceReference;
}
