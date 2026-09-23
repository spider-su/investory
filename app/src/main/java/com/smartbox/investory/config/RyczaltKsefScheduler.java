package com.smartbox.investory.config;

import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefApi;
import com.smartbox.investory.ryczalt.integration.ksef.KsefSyncMode;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Imports the current Ryczalt month from KSeF for every configured profile. */
@Slf4j
@Component
@ConditionalOnProperty(
    name = {"app.scheduling.enabled", "app.ryczalt.ksef.sync.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class RyczaltKsefScheduler {
  private static final Set<KsefSyncMode> MODES = Set.of(KsefSyncMode.SALES, KsefSyncMode.PURCHASES);

  private final RyczaltKsefApi ksef;
  private final RyczaltPeriodJpaRepository periods;
  private final Clock clock;
  private final ZoneId zone;

  public RyczaltKsefScheduler(
      RyczaltKsefApi ksef, RyczaltPeriodJpaRepository periods, Clock clock) {
    this(ksef, periods, clock, ZoneId.of("Europe/Warsaw"));
  }

  RyczaltKsefScheduler(
      RyczaltKsefApi ksef, RyczaltPeriodJpaRepository periods, Clock clock, ZoneId zone) {
    this.ksef = ksef;
    this.periods = periods;
    this.clock = clock;
    this.zone = zone;
  }

  @Scheduled(
      cron = "${app.ryczalt.ksef.sync.cron:0 0 2 * * *}",
      zone = "${app.ryczalt.ksef.sync.zone:Europe/Warsaw}")
  public void syncCurrentMonth() {
    YearMonth month = YearMonth.now(clock.withZone(zone));
    for (long profileId : periods.findDistinctProfileIds()) {
      try {
        var result = ksef.sync(profileId, month, MODES);
        log.info(
            "Scheduled KSeF sync completed: profileId={}, month={}, received={}, imported={}, duplicates={}, updated={}, failed={}",
            profileId,
            month,
            result.received(),
            result.imported(),
            result.duplicates(),
            result.updated(),
            result.failed());
      } catch (RuntimeException exception) {
        log.error(
            "Scheduled KSeF sync failed: profileId={}, month={}", profileId, month, exception);
      }
    }
  }
}
