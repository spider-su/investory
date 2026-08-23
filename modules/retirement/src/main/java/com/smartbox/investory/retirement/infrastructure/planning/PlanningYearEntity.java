package com.smartbox.investory.retirement.infrastructure.planning;

import com.smartbox.investory.retirement.planning.PlanningYearStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "planning_years",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_planning_years_portfolio_year",
            columnNames = {"portfolio_id", "planning_year"}))
@Getter
@Setter
public class PlanningYearEntity {
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

  @Column(name = "baseline_plan_id")
  private Long baselinePlanId;

  @Column(name = "baseline_revision_id")
  private Long baselineRevisionId;

  @Column(name = "baseline_created_at")
  private Instant baselineCreatedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "reopened_at")
  private Instant reopenedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
