package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Entity
@Table(name = "ryczalt_source_reference", schema = "investory")
public class RyczaltSourceReferenceEntity {
  private static final ObjectMapper JSON = new ObjectMapper();
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @Column(name = "entity_type", nullable = false, length = 32)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private long entityId;

  @Column(nullable = false, length = 64)
  private String source;

  @Column(name = "external_id", nullable = false, length = 256)
  private String externalId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private JsonNode metadata;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected RyczaltSourceReferenceEntity() {}

  public RyczaltSourceReferenceEntity(
      long profileId,
      String entityType,
      long entityId,
      String source,
      String externalId,
      String metadata) {
    this.profileId = profileId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.source = source;
    this.externalId = externalId;
    try {
      this.metadata = metadata == null ? null : JSON.readTree(metadata);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Source metadata must contain valid JSON", exception);
    }
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public long getProfileId() {
    return profileId;
  }

  public String getEntityType() {
    return entityType;
  }

  public long getEntityId() {
    return entityId;
  }

  public String getSource() {
    return source;
  }

  public String getExternalId() {
    return externalId;
  }

  public String getMetadata() {
    return metadata == null ? null : metadata.toString();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
