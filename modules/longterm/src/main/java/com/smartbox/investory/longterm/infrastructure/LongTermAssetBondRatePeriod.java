package com.smartbox.investory.longterm.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "long_term_asset_bond_rate_periods")
@Getter
@Setter
public class LongTermAssetBondRatePeriod {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  @Column(name = "annual_interest_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal annualInterestRate;
}
