package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ryczalt_zus_rule_set", schema = "investory")
public class RyczaltZusRuleSetEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "rule_year")
  private int year;

  private String version;

  @Column(name = "social_insurance")
  private BigDecimal socialInsurance;

  @Column(name = "labour_fund")
  private BigDecimal labourFund;

  @Column(name = "voluntary_sickness")
  private BigDecimal voluntarySickness;

  @Column(name = "health_low")
  private BigDecimal healthLow;

  @Column(name = "health_medium")
  private BigDecimal healthMedium;

  @Column(name = "health_high")
  private BigDecimal healthHigh;

  @Column(name = "threshold_low")
  private BigDecimal thresholdLow;

  @Column(name = "threshold_medium")
  private BigDecimal thresholdMedium;

  protected RyczaltZusRuleSetEntity() {}

  public com.smartbox.investory.ryczalt.calculation.zus.ZusRuleSet ruleSet() {
    return new com.smartbox.investory.ryczalt.calculation.zus.ZusRuleSet(
        year,
        version,
        socialInsurance,
        labourFund,
        voluntarySickness,
        healthLow,
        healthMedium,
        healthHigh,
        thresholdLow,
        thresholdMedium);
  }
}
