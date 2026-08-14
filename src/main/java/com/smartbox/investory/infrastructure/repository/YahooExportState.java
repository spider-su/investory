package com.smartbox.investory.infrastructure.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "yahoo_export_state")
public class YahooExportState {
  @Id private Integer id;

  @Column(name = "exported_at", nullable = false)
  private ZonedDateTime exportedAt;

  @Column(name = "portfolio_fingerprint", nullable = false, length = 64)
  private String portfolioFingerprint;

  @Column(name = "position_count", nullable = false)
  private Integer positionCount;
}
