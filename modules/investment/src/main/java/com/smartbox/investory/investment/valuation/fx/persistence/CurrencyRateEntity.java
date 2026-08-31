package com.smartbox.investory.investment.valuation.fx.persistence;

import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import lombok.*;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "exchange_rates")
public class CurrencyRateEntity {

  @Id
  @SequenceGenerator(
      name = "exchange_rates_id_seq",
      sequenceName = "exchange_rates_id_seq",
      allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "exchange_rates_id_seq")
  private Long id;

  @Column(name = "rate_date", nullable = false)
  private LocalDate rateDate;

  @Enumerated(value = EnumType.STRING)
  @Column(nullable = false)
  private CurrencyType base;

  @Enumerated(value = EnumType.STRING)
  @Column(name = "to_currency", nullable = false)
  private CurrencyType toCurrency;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(nullable = false, precision = 20, scale = 8)
  private BigDecimal rate;

  @Column(name = "source", nullable = false, length = 32)
  private String source;

  @Column(name = "method", nullable = false, length = 32)
  private String method;

  @Column(name = "observed_at")
  private ZonedDateTime observedAt;

  @Column(name = "source_reference", length = 256)
  private String sourceReference;

  @Column(name = "imported_at", nullable = false, insertable = false, updatable = false)
  private ZonedDateTime importedAt;

  @PrePersist
  @PreUpdate
  void applyDefaults() {
    if (source == null) source = "MANUAL";
    if (method == null) method = "HISTORICAL_MONTHLY";
    if (rateDate == null) throw new IllegalStateException("rateDate is required");
  }

  public BigDecimal getRate() {
    return rate;
  }

  public void setRate(BigDecimal rate) {
    this.rate = scaleRate(rate);
  }

  private static BigDecimal scaleRate(BigDecimal rate) {
    return rate == null ? null : rate.setScale(8, RoundingMode.HALF_UP);
  }
}
