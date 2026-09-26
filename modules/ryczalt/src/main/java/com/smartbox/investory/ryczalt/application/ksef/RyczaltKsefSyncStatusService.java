package com.smartbox.investory.ryczalt.application.ksef;

import java.time.YearMonth;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RyczaltKsefSyncStatusService {
  private final JdbcTemplate jdbc;

  public RyczaltKsefSyncStatusService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void set(long profileId, YearMonth month, String status, String errorCode) {
    jdbc.update(
        """
        INSERT INTO investory.ryczalt_ksef_sync_status(profile_id, period_year, period_month, status, error_code)
        VALUES (?, ?, ?, ?, ?) ON CONFLICT (profile_id, period_year, period_month) DO UPDATE SET
          status=EXCLUDED.status, error_code=EXCLUDED.error_code, updated_at=CURRENT_TIMESTAMP
        """,
        profileId,
        month.getYear(),
        month.getMonthValue(),
        status,
        errorCode);
  }
}
