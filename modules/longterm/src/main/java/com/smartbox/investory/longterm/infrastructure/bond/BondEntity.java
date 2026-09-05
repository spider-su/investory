package com.smartbox.investory.longterm.infrastructure.bond;

import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bond")
@Getter
@Setter
@SequenceGenerator(
    name = "longTermAssetId",
    sequenceName = "long_term_asset_id_seq",
    allocationSize = 1)
public class BondEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "longTermAssetId")
  private Long id;

  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private CurrencyType currency;

  @Column(nullable = false, precision = 30, scale = 12)
  private BigDecimal value;

  @Column(name = "acquisition_date")
  private LocalDate acquisitionDate;

  @Column(name = "interest_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal interestRate;

  @Column(name = "maturity_date", nullable = false)
  private LocalDate maturityDate;

  @Column(name = "archived_at")
  private LocalDate archivedAt;

  @Column(length = 128)
  private String externalKey;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void created() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void updated() {
    updatedAt = Instant.now();
  }
}
