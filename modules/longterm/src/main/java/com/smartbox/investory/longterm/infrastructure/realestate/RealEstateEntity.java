package com.smartbox.investory.longterm.infrastructure.realestate;

import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "real_estate")
@Getter
@Setter
@SequenceGenerator(
    name = "longTermAssetId",
    sequenceName = "long_term_asset_id_seq",
    allocationSize = 1)
public class RealEstateEntity {
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

  @Column(name = "tax_base", precision = 30, scale = 12)
  private BigDecimal taxBase;

  @Column(name = "acquisition_date")
  private LocalDate acquisitionDate;

  @Column(name = "land_register_number", length = 128)
  private String landRegisterNumber;

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
