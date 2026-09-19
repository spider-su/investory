package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ryczalt_source_reference", schema = "investory")
public class RyczaltSourceReferenceEntity {
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

  @Column(columnDefinition = "jsonb")
  private String metadata;

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
    this.metadata = metadata;
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
    return metadata;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
