package com.smartbox.investory.retirement.infrastructure.annualcost;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "retirement_annual_cost_years",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_retirement_annual_cost_years_plan_year",
            columnNames = {"plan_id", "year"}))
@Getter
@Setter
public class RetirementAnnualCostYearEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "plan_id", nullable = false)
  private Long planId;

  @Column(name = "year", nullable = false)
  private int year;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }
}
