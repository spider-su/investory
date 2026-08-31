package com.smartbox.investory.longterm.infrastructure.asset;

import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "long_term_assets")
@Getter
@Setter
public class LongTermAssetEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Column(nullable = false)
  private String name;

  @Column(name = "external_key", length = 128)
  private String externalKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "asset_type", nullable = false)
  private LongTermAssetType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private CurrencyType currency;

  @Transient private CurrencyType persistedCurrency;

  @Column(name = "acquisition_date")
  private LocalDate acquisitionDate;

  @Column(name = "acquisition_value", precision = 30, scale = 12)
  private BigDecimal acquisitionValue;

  @Column(name = "current_value", nullable = false, precision = 30, scale = 12)
  private BigDecimal currentValue;

  @Column(name = "tax_base", precision = 30, scale = 12)
  private BigDecimal taxBase;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "archived_at")
  private LocalDate archivedAt;

  @Column(name = "rental_tax_paid_by_tenant", nullable = false)
  private boolean rentalTaxPaidByTenant;

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

  @PostLoad
  void loaded() {
    persistedCurrency = currency;
  }

  @PostPersist
  @PostUpdate
  void stored() {
    persistedCurrency = currency;
  }

  @PreUpdate
  void updated() {
    if (persistedCurrency != null && currency != persistedCurrency)
      throw new IllegalArgumentException("Asset currency cannot be changed after creation");
    updatedAt = Instant.now();
  }
}
