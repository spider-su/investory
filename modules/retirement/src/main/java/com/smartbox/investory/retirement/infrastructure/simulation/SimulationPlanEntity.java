package com.smartbox.investory.retirement.infrastructure.simulation;

import com.smartbox.investory.retirement.api.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "simulation_plans",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_simulation_plans_portfolio_name",
            columnNames = {"portfolio_id", "name"}))
@Getter
@Setter
public class SimulationPlanEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "current_revision_id")
  private Long currentRevisionId;

  @Column(name = "archived", nullable = false)
  private boolean archived;

  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Column(nullable = false)
  private String name;

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
