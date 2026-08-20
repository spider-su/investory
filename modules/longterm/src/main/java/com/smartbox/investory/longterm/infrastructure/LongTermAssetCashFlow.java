package com.smartbox.investory.longterm.infrastructure;

import com.smartbox.investory.longterm.api.CashFlowType;
import com.smartbox.investory.longterm.api.Frequency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "long_term_asset_cash_flows")
@Getter
@Setter
public class LongTermAssetCashFlow {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "cash_flow_type", nullable = false)
  private CashFlowType type;

  @Column(nullable = false, precision = 30, scale = 12)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Frequency frequency;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  @Column(name = "paid_by_tenant", nullable = false)
  private Boolean paidByTenant;

  public boolean isPaidByTenant() {
    return paidByTenant != null
        ? paidByTenant
        : type == CashFlowType.ADMIN_FEE || type == CashFlowType.UTILITIES;
  }

  public void setPaidByTenant(boolean paidByTenant) {
    this.paidByTenant = paidByTenant;
  }

  @PrePersist
  @PreUpdate
  void applyOwnershipDefault() {
    if (paidByTenant == null)
      paidByTenant = type == CashFlowType.ADMIN_FEE || type == CashFlowType.UTILITIES;
  }
}
