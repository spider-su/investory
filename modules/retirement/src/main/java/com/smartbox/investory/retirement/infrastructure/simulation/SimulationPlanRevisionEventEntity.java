package com.smartbox.investory.retirement.infrastructure.simulation;

import com.smartbox.investory.retirement.simulation.SimulationEventType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/** Event snapshot owned by one immutable plan revision. */
@Entity
@Immutable
@Table(name = "simulation_plan_revision_events")
@Getter
@Setter
public class SimulationPlanRevisionEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "revision_id", nullable = false)
  private Long revisionId;

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

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }
}
