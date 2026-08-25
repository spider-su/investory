package com.smartbox.investory.longterm.infrastructure.rental;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "long_term_asset_rental_contracts")
@Getter
@Setter
public class LongTermAssetRentalContractEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "terminated_date")
  private LocalDate terminatedDate;

  @Column(name = "rental_tax_paid_by_tenant")
  private Boolean rentalTaxPaidByTenant;

  @Column(name = "monthly_tax_base", precision = 20, scale = 2)
  private BigDecimal monthlyTaxBase;

  @Column(name = "tenant_name", length = 200)
  private String tenantName;

  @Column(name = "tenant_email", length = 320)
  private String tenantEmail;

  @Column(name = "tenant_phone", length = 50)
  private String tenantPhone;

  private String notes;

  @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<LongTermAssetRentalContractTermEntity> terms = new ArrayList<>();
}
