package com.smartbox.investory.longterm.infrastructure.lifecycle;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "long_term_asset_lifecycle_periods")
@Getter
@Setter
public class LongTermAssetLifecyclePeriodEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "active_from", nullable = false)
  private LocalDate activeFrom;

  @Column(name = "active_to")
  private LocalDate activeTo;
}
