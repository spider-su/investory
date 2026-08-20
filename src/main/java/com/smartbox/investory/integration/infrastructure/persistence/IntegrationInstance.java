package com.smartbox.investory.integration.infrastructure.persistence;

import com.smartbox.investory.integration.IntegrationType;
import jakarta.persistence.*;
import java.time.ZonedDateTime;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "integration_instances")
public class IntegrationInstance {
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
}
