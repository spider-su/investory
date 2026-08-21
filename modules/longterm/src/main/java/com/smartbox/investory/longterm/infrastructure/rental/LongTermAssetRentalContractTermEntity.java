package com.smartbox.investory.longterm.infrastructure.rental;

import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "long_term_asset_rental_contract_terms")
@Getter @Setter
public class LongTermAssetRentalContractTermEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "contract_id", nullable = false) private LongTermAssetRentalContractEntity contract;
  @Enumerated(EnumType.STRING) @Column(name = "cash_flow_type", nullable = false) private CashFlowType type;
  @Column(nullable = false, precision = 30, scale = 12) private BigDecimal amount;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private Frequency frequency;
  @Column(name = "paid_by_tenant", nullable = false) private boolean paidByTenant;
}
