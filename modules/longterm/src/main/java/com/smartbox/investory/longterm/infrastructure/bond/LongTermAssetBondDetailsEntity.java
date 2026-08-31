package com.smartbox.investory.longterm.infrastructure.bond;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "long_term_asset_bond_details")
@Getter
@Setter
public class LongTermAssetBondDetailsEntity {
  @Id
  @Column(name = "asset_id")
  private Long assetId;

  @Column(name = "maturity_date", nullable = false)
  private LocalDate maturityDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "interest_treatment", nullable = false)
  private InterestTreatment interestTreatment;

  @Column(name = "tax_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal taxRate;

  @Column(name = "redemption_value", precision = 30, scale = 12)
  private BigDecimal redemptionValue;
}
