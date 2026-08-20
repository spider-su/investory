package com.smartbox.investory.integration.notifications.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "drawdown_alert_state")
public class DrawdownAlertState {

  public static final long SINGLETON_ID = 1L;

  @Id private Long id = SINGLETON_ID;

  @Column(name = "peak_equity", nullable = false, precision = 30, scale = 12)
  private BigDecimal peakEquity = BigDecimal.ZERO;

  @Column(name = "last_alert_at")
  private ZonedDateTime lastAlertAt;

  public double getPeakEquity() {
    return peakEquity == null ? 0.0 : peakEquity.doubleValue();
  }

  public void setPeakEquity(double peakEquity) {
    this.peakEquity = BigDecimal.valueOf(peakEquity);
  }
}
