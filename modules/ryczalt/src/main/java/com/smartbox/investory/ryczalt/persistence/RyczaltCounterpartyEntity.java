package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ryczalt_counterparty", schema = "investory")
public class RyczaltCounterpartyEntity extends RyczaltEntity {
  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @Column(name = "tax_identifier", length = 64)
  private String taxIdentifier;

  @Column(nullable = false, length = 2)
  private String country;

  @Column(name = "legal_name", nullable = false, length = 512)
  private String legalName;

  @Column(length = 256)
  private String alias;

  protected RyczaltCounterpartyEntity() {}

  public RyczaltCounterpartyEntity(
      long profileId, String taxIdentifier, String country, String legalName) {
    this.profileId = profileId;
    this.taxIdentifier = taxIdentifier;
    this.country = country;
    this.legalName = legalName;
  }

  public long getProfileId() {
    return profileId;
  }

  public String getTaxIdentifier() {
    return taxIdentifier;
  }

  public String getCountry() {
    return country;
  }

  public String getLegalName() {
    return legalName;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias == null || alias.isBlank() ? null : alias.trim();
  }

  public Long id() {
    return getId();
  }
}
