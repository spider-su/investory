package com.smartbox.investory.retirement.infrastructure.planning;

import com.smartbox.investory.retirement.api.model.PlanningMetric;
import com.smartbox.investory.retirement.api.model.PlanningMetricValue;
import com.smartbox.investory.retirement.api.model.PlanningValueKind;
import com.smartbox.investory.retirement.api.model.PlanningYearStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** One persisted planning-year aggregate. Values live in its versioned JSON state. */
@Entity
@Table(name = "retirement_planning_years")
@Getter
@Setter
public class RetirementPlanningYearEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Column(name = "planning_year", nullable = false)
  private int year;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PlanningYearStatus status;

  @Column(nullable = false, columnDefinition = "jsonb")
  private String state = "{}";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Transient private Long baselinePlanId;
  @Transient private Instant baselineCreatedAt;
  @Transient private Instant closedAt;
  @Transient private Instant reopenedAt;

  @Transient
  private Map<PlanningValueKind, Map<PlanningMetric, PlanningMetricValue>> values = emptyValues();

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  private static Map<PlanningValueKind, Map<PlanningMetric, PlanningMetricValue>> emptyValues() {
    Map<PlanningValueKind, Map<PlanningMetric, PlanningMetricValue>> result =
        new EnumMap<>(PlanningValueKind.class);
    for (PlanningValueKind kind : PlanningValueKind.values())
      result.put(kind, new EnumMap<>(PlanningMetric.class));
    return result;
  }
}
