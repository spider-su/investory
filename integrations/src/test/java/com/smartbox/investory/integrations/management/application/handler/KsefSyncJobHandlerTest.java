package com.smartbox.investory.integrations.management.application.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.ksef.KsefSyncJobPort;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobEntity;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobContext;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class KsefSyncJobHandlerTest {
  private final KsefSyncJobPort sync = mock(KsefSyncJobPort.class);
  private final IntegrationJobEntity job = mock(IntegrationJobEntity.class);
  private final IntegrationJobContext context =
      new IntegrationJobContext(
          mock(IntegrationInstanceEntity.class), job, ZonedDateTime.parse("2026-09-24T01:00:00Z"));

  @Test
  void disabledSchedulerDoesNotCallSync() {
    new KsefSyncJobHandler(sync, false).execute(context);
    verifyNoInteractions(sync);
  }

  @Test
  void enabledSchedulerUsesConfiguredJobTimezone() {
    when(job.getTimezone()).thenReturn(ZoneId.of("Europe/Warsaw").getId());
    new KsefSyncJobHandler(sync, true).execute(context);
    verify(sync).sync(java.time.YearMonth.of(2026, 9));
  }
}
