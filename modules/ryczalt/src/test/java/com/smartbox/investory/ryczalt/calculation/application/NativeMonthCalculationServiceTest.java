package com.smartbox.investory.ryczalt.calculation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.ryczalt.calculation.vat.VatCalculationInput;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculationInput;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationPersistenceAdapter;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NativeMonthCalculationServiceTest {
  private final RyczaltPeriodJpaRepository periods = mock(RyczaltPeriodJpaRepository.class);
  private final NativeMonthInputAggregator inputAggregator = mock(NativeMonthInputAggregator.class);
  private final RyczaltCalculationPersistenceAdapter calculations =
      mock(RyczaltCalculationPersistenceAdapter.class);
  private final RyczaltObligationJpaRepository obligations =
      mock(RyczaltObligationJpaRepository.class);

  @Test
  void calculatesAllTaxesAndCreatesThreeObligationsForNewMonth() {
    YearMonth month = YearMonth.of(2026, 9);
    RyczaltPeriodEntity period = mock(RyczaltPeriodEntity.class);
    when(period.id()).thenReturn(10L);
    when(period.getYear()).thenReturn(2026);
    when(period.getMonth()).thenReturn(9);
    when(period.getStatus()).thenReturn(PeriodStatus.OPEN);
    when(periods.findByProfileIdAndYearAndMonth(7L, 2026, 9)).thenReturn(Optional.of(period));
    when(obligations.findByProfileIdAndPeriodIdOrderByTypeAsc(7L, period.id()))
        .thenReturn(List.of());
    when(calculations.saveCurrent(any(), any(Long.TYPE), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> mock(RyczaltCalculationEntity.class));

    var service =
        new NativeMonthCalculationService(periods, inputAggregator, calculations, obligations);
    var result =
        service.calculate(
            7L,
            month,
            new NativeMonthCalculationInput(
                Map.of(new BigDecimal("0.12"), new BigDecimal("10000.00")),
                new VatCalculationInput(
                    new BigDecimal("2300.00"), BigDecimal.ZERO, new BigDecimal("100.00")),
                new ZusCalculationInput(
                    true,
                    false,
                    "JDG",
                    false,
                    new BigDecimal("10000.00"),
                    new BigDecimal("2000.00")),
                BigDecimal.ZERO));

    assertEquals(new BigDecimal("947"), result.ryczalt());
    assertEquals(0, result.vat().compareTo(new BigDecimal("2200.00")));
    assertEquals(result.zusResult().total(), result.zus());
    verify(calculations, times(3))
        .saveCurrent(any(), any(Long.TYPE), any(), any(), any(), any(), any());
    verify(obligations, times(3)).save(any());
    verify(periods).save(period);
  }
}
