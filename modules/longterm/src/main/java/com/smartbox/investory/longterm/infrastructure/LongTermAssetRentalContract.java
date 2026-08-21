package com.smartbox.investory.longterm.infrastructure;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "long_term_asset_rental_contracts")
@Getter @Setter
public class LongTermAssetRentalContract {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "asset_id", nullable = false) private Long assetId;
  @Column(name = "start_date", nullable = false) private LocalDate startDate;
  @Column(name = "end_date") private LocalDate endDate;
  @Column(name = "terminated_date") private LocalDate terminatedDate;
  private String notes;
  @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<LongTermAssetRentalContractTerm> terms = new ArrayList<>();
}
