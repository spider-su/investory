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

@Entity
@Table(name = "ryczalt_fx_rate", schema = "investory")
public class RyczaltFxRateEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private CurrencyType currency;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(nullable = false, precision = 19, scale = 8)
  private BigDecimal rate;

  @Column(nullable = false, length = 64)
  private String provider;

  @Column(name = "provider_reference", length = 256)
  private String providerReference;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;

  protected RyczaltFxRateEntity() {}

  public RyczaltFxRateEntity(
      CurrencyType currency,
      LocalDate effectiveDate,
      BigDecimal rate,
      String provider,
      String providerReference,
      Instant fetchedAt) {
    this.currency = currency;
    this.effectiveDate = effectiveDate;
    this.rate = rate;
    this.provider = provider;
    this.providerReference = providerReference;
    this.fetchedAt = fetchedAt;
  }

  public Long getId() {
    return id;
  }

  public CurrencyType getCurrency() {
    return currency;
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

  public Instant getFetchedAt() {
    return fetchedAt;
  }
}
