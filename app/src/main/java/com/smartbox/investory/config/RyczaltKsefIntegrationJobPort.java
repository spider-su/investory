package com.smartbox.investory.config;

import com.smartbox.investory.integrations.ksef.KsefSyncJobPort;
import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefApi;
import com.smartbox.investory.ryczalt.integration.ksef.KsefSyncMode;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Bridges the generic integration job to the native Ryczalt KSeF use case. */
@Slf4j
@Component
public class RyczaltKsefIntegrationJobPort implements KsefSyncJobPort {
  private static final Set<KsefSyncMode> MODES = Set.of(KsefSyncMode.SALES, KsefSyncMode.PURCHASES);

  private final RyczaltKsefApi ksef;
  private final RyczaltPeriodJpaRepository periods;

  public RyczaltKsefIntegrationJobPort(RyczaltKsefApi ksef, RyczaltPeriodJpaRepository periods) {
    this.ksef = ksef;
    this.periods = periods;
  }

  @Override
  public void sync(YearMonth month) {
    var failures = new ArrayList<Long>();
    for (long profileId : periods.findDistinctProfileIds()) {
      try {
        var result = ksef.sync(profileId, month, MODES);
        log.info(
            "KSeF sync completed: profileId={}, month={}, received={}, imported={}, duplicates={}, updated={}, failed={}",
            profileId,
            month,
            result.received(),
            result.imported(),
            result.duplicates(),
            result.updated(),
            result.failed());
      } catch (RuntimeException exception) {
        failures.add(profileId);
        log.error("KSeF sync failed: profileId={}, month={}", profileId, month, exception);
      }
    }
    if (!failures.isEmpty()) {
      throw new IllegalStateException("KSeF sync failed for profiles " + failures);
    }
  }
}
