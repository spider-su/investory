package com.smartbox.investory.retirement.infrastructure.planning;

import com.smartbox.investory.retirement.api.model.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "planning_year_values",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_planning_year_values_key",
            columnNames = {"planning_year_id", "value_kind", "metric"}))
@Getter
@Setter
public class PlanningYearValueEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "planning_year_id", nullable = false)
  private Long planningYearId;

  @Enumerated(EnumType.STRING)
  @Column(name = "value_kind", nullable = false, length = 16)
  private PlanningValueKind valueKind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 48)
  private PlanningMetric metric;

  @Column(name = "derived_value", precision = 30, scale = 12)
  private BigDecimal derivedValue;

  @Column(name = "approved_value", precision = 30, scale = 12)
  private BigDecimal approvedValue;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 32)
  private PlanningValueSource sourceType;

  @Column(length = 1000)
  private String note;

  @Column(name = "captured_at", nullable = false)
  private Instant capturedAt;

  @PrePersist
  void onCreate() {
    capturedAt = Instant.now();
  }
}
