package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Exact provider resolution for one requested business date. */
@Entity
@Table(name = "ryczalt_fx_resolution", schema = "investory")
public class RyczaltFxResolutionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private CurrencyType currency;

  @Column(name = "requested_date", nullable = false)
  private LocalDate requestedDate;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(nullable = false, precision = 19, scale = 8)
  private BigDecimal rate;

  @Column(nullable = false, length = 64)
  private String provider;

  @Column(name = "provider_reference", length = 256)
  private String providerReference;

  @Column(name = "resolved_at", nullable = false)
  private Instant resolvedAt;

  protected RyczaltFxResolutionEntity() {}

  public RyczaltFxResolutionEntity(
      CurrencyType currency,
      LocalDate requestedDate,
      LocalDate effectiveDate,
      BigDecimal rate,
      String provider,
      String providerReference,
      Instant resolvedAt) {
    this.currency = currency;
    this.requestedDate = requestedDate;
    this.effectiveDate = effectiveDate;
    this.rate = rate;
    this.provider = provider;
    this.providerReference = providerReference;
    this.resolvedAt = resolvedAt;
  }

  public CurrencyType getCurrency() {
    return currency;
  }

  public LocalDate getRequestedDate() {
    return requestedDate;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public BigDecimal getRate() {
    return rate;
  }

  public String getProvider() {
    return provider;
  }

  public String getProviderReference() {
    return providerReference;
  }
}
