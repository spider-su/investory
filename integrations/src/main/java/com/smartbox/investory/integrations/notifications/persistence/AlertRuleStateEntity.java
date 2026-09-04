package com.smartbox.investory.integrations.notifications.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notification_alert_state")
public class AlertRuleStateEntity {
  @Id
  @Column(name = "rule_code", length = 64)
  private String ruleCode;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "incident_sequence", nullable = false)
  private long incidentSequence;
}
