package com.smartbox.investory.ryczalt.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.ryczalt.calculation.InputChange;
import com.smartbox.investory.ryczalt.persistence.RyczaltNativeMonthInputEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltNativeMonthInputJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodLifecycleService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RyczaltNativeMonthInputServiceTest {
  private final RyczaltNativeMonthInputJpaRepository inputs = mock();
  private final RyczaltPeriodLifecycleService lifecycle = mock();
  private final RyczaltNativeMonthInputService service =
      new RyczaltNativeMonthInputService(inputs, lifecycle);

  @Test
  void sameValuesDoNotInvalidate() {
    var command = command(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    var entity = new RyczaltNativeMonthInputEntity(7L, YearMonth.of(2026, 9), command);
    when(inputs.findByProfileIdAndYearAndMonth(7L, 2026, 9)).thenReturn(Optional.of(entity));

    service.save(7L, YearMonth.of(2026, 9), command);

    verify(inputs).save(entity);
    verifyNoInteractions(lifecycle);
  }

  @Test
  void scaleOnlyAmountChangesDoNotInvalidate() {
    var oldCommand = command(new BigDecimal("10.0"), new BigDecimal("20.00"), new BigDecimal("30.000"));
    var newCommand = command(new BigDecimal("10.00"), new BigDecimal("20.0"), new BigDecimal("30.00"));
    var entity = new RyczaltNativeMonthInputEntity(7L, YearMonth.of(2026, 9), oldCommand);
    when(inputs.findByProfileIdAndYearAndMonth(7L, 2026, 9)).thenReturn(Optional.of(entity));

    service.save(7L, YearMonth.of(2026, 9), newCommand);

    verify(inputs).save(entity);
    verifyNoInteractions(lifecycle);
  }

  @Test
  void changesInvalidateOnlyTheirCalculationAreas() {
    var oldCommand = command(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    var newCommand = command(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30"));
    var entity = new RyczaltNativeMonthInputEntity(7L, YearMonth.of(2026, 9), oldCommand);
    when(inputs.findByProfileIdAndYearAndMonth(7L, 2026, 9)).thenReturn(Optional.of(entity));

    service.save(7L, YearMonth.of(2026, 9), newCommand);

    verify(lifecycle)
        .invalidate(7L, YearMonth.of(2026, 9), InputChange.ZUS_INPUT_CHANGED, "native-month-input");
    verify(lifecycle)
        .invalidate(
            7L,
            YearMonth.of(2026, 9),
            InputChange.RYCZALT_DEDUCTIONS_CHANGED,
            "native-month-input");
    verify(lifecycle)
        .invalidate(
            7L, YearMonth.of(2026, 9), InputChange.VAT_ADJUSTMENT_CHANGED, "native-month-input");
  }

  private RyczaltNativeMonthInputService.Command command(
      BigDecimal ytdRevenue, BigDecimal deductions, BigDecimal vatAdjustments) {
    return new RyczaltNativeMonthInputService.Command(
        true,
        false,
        "JDG",
        false,
        ytdRevenue,
        BigDecimal.ZERO,
        null,
        null,
        deductions,
        BigDecimal.ZERO,
        vatAdjustments);
  }
}
