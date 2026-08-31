package com.smartbox.investory.integrations.infrastructure.integration.persistence;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "integration_secrets")
public class IntegrationSecretEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "integration_instance_id", nullable = false)
  private Long integrationInstanceId;

  @Column(name = "secret_name", nullable = false, length = 128)
  private String secretName;

  @Column(nullable = false, columnDefinition = "text")
  private String ciphertext;

  @Column(name = "key_version", nullable = false, length = 64)
  private String keyVersion;

  @Column(name = "updated_at", nullable = false)
  private ZonedDateTime updatedAt;
}
