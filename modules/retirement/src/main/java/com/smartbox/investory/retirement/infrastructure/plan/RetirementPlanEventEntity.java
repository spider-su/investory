package com.smartbox.investory.retirement.infrastructure.plan;

import com.smartbox.investory.retirement.api.model.SimulationEventType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "retirement_plan_events")
@Getter
@Setter
public class RetirementPlanEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "plan_id", nullable = false)
  private Long planId;

  @Column(name = "event_year", nullable = false)
  private int year;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private SimulationEventType type;

  @Column(length = 1023)
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
