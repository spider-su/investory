package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ryczalt_calculation", schema = "investory")
public class RyczaltCalculationEntity {
  @jakarta.persistence.Id
  @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "period_id", nullable = false)
  private RyczaltPeriodEntity period;

  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @Enumerated(EnumType.STRING)
  @Column(name = "calculation_type", nullable = false, length = 8)
  private CalculationType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CalculationStatus status;

  @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
  private String resultJson;

  @Column(name = "input_fingerprint", nullable = false, length = 128)
  private String inputFingerprint;

  @Column(name = "rule_version", nullable = false, length = 64)
  private String ruleVersion;

  @Column(name = "calculator_version", nullable = false, length = 64)
  private String calculatorVersion;

  @Column(name = "calculated_at", nullable = false)
  private Instant calculatedAt;

  protected RyczaltCalculationEntity() {}

  public RyczaltCalculationEntity(
      RyczaltPeriodEntity period,
      long profileId,
      CalculationType type,
      CalculationStatus status,
      String resultJson,
      String inputFingerprint,
      String ruleVersion,
      String calculatorVersion,
      Instant calculatedAt) {
    this.period = period;
    this.profileId = profileId;
    this.type = type;
    this.status = status;
    this.resultJson = resultJson;
    this.inputFingerprint = inputFingerprint;
    this.ruleVersion = ruleVersion;
    this.calculatorVersion = calculatorVersion;
    this.calculatedAt = calculatedAt;
  }

  public Long getId() {
    return id;
  }

  public RyczaltPeriodEntity getPeriod() {
    return period;
  }

  public long getProfileId() {
    return profileId;
  }

  public CalculationType getType() {
    return type;
  }

  public CalculationStatus getStatus() {
    return status;
  }

  public String getResultJson() {
    return resultJson;
  }

  public String getInputFingerprint() {
    return inputFingerprint;
  }

  public String getRuleVersion() {
    return ruleVersion;
  }

  public String getCalculatorVersion() {
    return calculatorVersion;
  }

  public Instant getCalculatedAt() {
    return calculatedAt;
  }
}
