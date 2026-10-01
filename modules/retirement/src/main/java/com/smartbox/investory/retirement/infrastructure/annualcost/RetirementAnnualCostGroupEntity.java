package com.smartbox.investory.retirement.infrastructure.annualcost;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "retirement_annual_cost_groups",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_retirement_annual_cost_groups_year_name",
            columnNames = {"year_id", "name"}))
@Getter
@Setter
public class RetirementAnnualCostGroupEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "year_id", nullable = false)
  private Long yearId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "monthly_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal monthlyAmount;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
