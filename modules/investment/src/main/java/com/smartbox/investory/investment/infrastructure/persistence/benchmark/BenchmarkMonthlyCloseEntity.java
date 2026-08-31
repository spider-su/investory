package com.smartbox.investory.investment.infrastructure.persistence.benchmark;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "benchmark_monthly_closes")
public class BenchmarkMonthlyCloseEntity {

  @Id
  @SequenceGenerator(
      name = "benchmark_monthly_closes_id_seq",
      sequenceName = "benchmark_monthly_closes_id_seq",
      allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "benchmark_monthly_closes_id_seq")
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Column(name = "symbol", nullable = false, length = 32)
  private String symbol;

  @Column(name = "month", nullable = false)
  private LocalDate monthDate;

  @Column(name = "close_price", nullable = false)
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private BigDecimal closePrice;

  @Column(name = "fetched_at", nullable = false)
  private ZonedDateTime fetchedAt;

  public void setClosePrice(Double closePrice) {
    this.closePrice = closePrice == null ? null : BigDecimal.valueOf(closePrice);
  }
}
