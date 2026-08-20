package com.smartbox.investory.investment.infrastructure.integration.persistence;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "integration_jobs")
public class IntegrationJob {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "integration_instance_id", nullable = false)
  private Long integrationInstanceId;

  @Column(name = "job_type", nullable = false, length = 128)
  private String jobType;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false, length = 128)
  private String cron;

  @Column(nullable = false, length = 64)
  private String timezone = "Europe/Warsaw";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "parameters_json", nullable = false, columnDefinition = "jsonb")
  private String parametersJson = "{}";

  @Column(name = "last_started_at")
  private ZonedDateTime lastStartedAt;

  @Column(name = "last_completed_at")
  private ZonedDateTime lastCompletedAt;

  @Column(name = "last_status", length = 32)
  private String lastStatus;

  @Column(name = "last_error")
  private String lastError;
}
