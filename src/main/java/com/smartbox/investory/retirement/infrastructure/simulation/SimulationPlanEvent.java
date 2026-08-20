package com.smartbox.investory.retirement.infrastructure.simulation;

import com.smartbox.investory.retirement.simulation.SimulationEventType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "simulation_plan_events")
@Getter
@Setter
public class SimulationPlanEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "simulation_plan_id", nullable = false)
  private Long simulationPlanId;

  @Column(name = "event_year", nullable = false)
  private int year;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, precision = 30, scale = 12)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private SimulationEventType type;

  @Column(length = 1023)
  private String notes;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
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
