package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ryczalt_period", schema = "investory")
public class RyczaltPeriodEntity extends RyczaltEntity {
  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @Column(name = "period_year", nullable = false)
  private int year;

  @Column(name = "period_month", nullable = false)
  private int month;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PeriodStatus status;

  @Column(name = "calculated_at")
  private Instant calculatedAt;

  @Column(name = "frozen_at")
  private Instant frozenAt;

  @Column(name = "reopened_at")
  private Instant reopenedAt;

  @Column(name = "reopen_reason", length = 1000)
  private String reopenReason;

  protected RyczaltPeriodEntity() {}

  public RyczaltPeriodEntity(long profileId, int year, int month, PeriodStatus status) {
    this.profileId = profileId;
    this.year = year;
    this.month = month;
    this.status = status;
  }

  public long getProfileId() {
    return profileId;
  }

  public int getYear() {
    return year;
  }

  public int getMonth() {
    return month;
  }

  public PeriodStatus getStatus() {
    return status;
  }

  public void markDirty() {
    if (status.isFrozen()) throw new IllegalStateException("Frozen period cannot become dirty");
    this.status = PeriodStatus.DIRTY;
  }

  public void markCalculated(Instant at) {
    if (status.isFrozen()) throw new IllegalStateException("Frozen period cannot be calculated");
    this.status = PeriodStatus.CALCULATED;
    this.calculatedAt = at;
  }

  public void markReopened(String reason, Instant at) {
    this.status = PeriodStatus.DIRTY;
    this.frozenAt = null;
    this.reopenedAt = at;
    this.reopenReason = reason;
  }

  public void markFrozen(Instant at) {
    this.status = PeriodStatus.FROZEN;
    this.frozenAt = at;
  }

  public Instant getCalculatedAt() {
    return calculatedAt;
  }

  public Instant getFrozenAt() {
    return frozenAt;
  }

  public Instant getReopenedAt() {
    return reopenedAt;
  }

  public String getReopenReason() {
    return reopenReason;
  }

  public Long id() {
    return getId();
  }
}
