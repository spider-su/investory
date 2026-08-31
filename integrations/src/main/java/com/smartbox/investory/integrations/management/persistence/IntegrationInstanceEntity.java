package com.smartbox.investory.integrations.management.persistence;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import jakarta.persistence.*;
import java.time.ZonedDateTime;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "integration_instances")
public class IntegrationInstanceEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_id")
  private Long ownerId;

  @Column(name = "plugin_id", nullable = false, length = 128)
  private String pluginId;

  @Enumerated(EnumType.STRING)
  @Column(name = "plugin_type", nullable = false, length = 32)
  private IntegrationType pluginType;

  @Column(nullable = false)
  private boolean enabled;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
  private String configJson = "{}";

  @Column(name = "created_at", nullable = false)
  private ZonedDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private ZonedDateTime updatedAt;

  @Column(name = "last_test_at")
  private ZonedDateTime lastTestAt;

  @Column(name = "last_test_status", length = 32)
  private String lastTestStatus;

  @Column(name = "last_test_message", length = 500)
  private String lastTestMessage;
}
