package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ryczalt_obligation", schema = "investory")
public class RyczaltObligationEntity extends RyczaltEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "period_id", nullable = false)
  private RyczaltPeriodEntity period;

  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @Enumerated(EnumType.STRING)
  @Column(name = "obligation_type", nullable = false, length = 8)
  private com.smartbox.investory.ryczalt.domain.ObligationType type;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ObligationStatus status;

  @Column(name = "calculation_id")
  private Long calculationId;

  protected RyczaltObligationEntity() {}

  public RyczaltObligationEntity(
      RyczaltPeriodEntity period,
      long profileId,
      com.smartbox.investory.ryczalt.domain.ObligationType type,
      BigDecimal amount,
      String currency,
      LocalDate dueDate,
      ObligationStatus status,
      Long calculationId) {
    this.period = period;
    this.profileId = profileId;
    this.type = type;
    this.amount = amount;
    this.currency = currency;
    this.dueDate = dueDate;
    this.status = status;
    this.calculationId = calculationId;
  }

  public RyczaltPeriodEntity getPeriod() {
    return period;
  }

  public long getProfileId() {
    return profileId;
  }

  public com.smartbox.investory.ryczalt.domain.ObligationType getType() {
    return type;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public ObligationStatus getStatus() {
    return status;
  }

  public Long getCalculationId() {
    return calculationId;
  }

  public Long id() {
    return getId();
  }

  public void setStatus(ObligationStatus status) {
    this.status = status;
  }
}
