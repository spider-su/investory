package com.smartbox.investory.longterm.infrastructure;

import com.smartbox.investory.longterm.api.InterestTreatment;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "long_term_asset_deposit_details")
@Getter
@Setter
public class LongTermAssetDepositDetails {
  @Id
  @Column(name = "asset_id")
  private Long assetId;

  @Column(name = "maturity_date")
  private LocalDate maturityDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "interest_treatment", nullable = false)
  private InterestTreatment interestTreatment;

  @Column(name = "annual_interest_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal annualInterestRate;

  @Column(name = "tax_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal taxRate;
}
