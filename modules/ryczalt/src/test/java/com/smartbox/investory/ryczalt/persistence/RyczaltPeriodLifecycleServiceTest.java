package com.smartbox.investory.ryczalt.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.ryczalt.application.port.RyczaltAuditEventWriter;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RyczaltPeriodLifecycleServiceTest {
  private final RyczaltPeriodJpaRepository periods = mock(RyczaltPeriodJpaRepository.class);
  private final RyczaltCalculationJpaRepository calculations =
      mock(RyczaltCalculationJpaRepository.class);
  private final RyczaltObligationJpaRepository obligations =
      mock(RyczaltObligationJpaRepository.class);
  private final RyczaltAuditEventWriter auditEvents = mock(RyczaltAuditEventWriter.class);

  @Test
  void freezeRequiresCurrentCalculationForEveryTaxType() {
    RyczaltPeriodEntity period = mock(RyczaltPeriodEntity.class);
    when(period.id()).thenReturn(10L);
    when(period.getStatus()).thenReturn(PeriodStatus.CALCULATED);
    when(periods.findByProfileIdAndYearAndMonth(7L, 2026, 1)).thenReturn(Optional.of(period));
    when(calculations.findByProfileIdAndPeriodIdAndTypeAndCurrentTrue(
            7L, 10L, CalculationType.RYCZALT))
        .thenReturn(Optional.empty());

    var service =
        new RyczaltPeriodLifecycleService(periods, calculations, obligations, auditEvents);

    assertThrows(
        IllegalStateException.class,
        () -> service.freeze(7L, YearMonth.of(2026, 1), "tester", "complete period"));
  }
}
