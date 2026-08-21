package com.smartbox.investory.longterm.application;
import com.smartbox.investory.longterm.application.service.LongTermAssetCalculator;

import static org.junit.jupiter.api.Assertions.*;

import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LongTermAssetCalculatorTest {
  private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

  @Test
  void monthlyCashFlowIsAnnualized() {
    LongTermAssetCashFlowEntity f = flow(CashFlowType.RENT, "2650", Frequency.MONTHLY);
    assertEquals(new BigDecimal("31800"), LongTermAssetCalculator.annualAmount(f));
  }

  @Test
  void annualCashFlowIsUnchanged() {
    LongTermAssetCashFlowEntity f = flow(CashFlowType.PROPERTY_TAX, "320", Frequency.ANNUAL);
    assertEquals(new BigDecimal("320"), LongTermAssetCalculator.annualAmount(f));
  }

  @Test
  void cashFlowPeriodSelectionUsesInclusiveBounds() {
    LongTermAssetCashFlowEntity f = flow(CashFlowType.RENT, "1", Frequency.MONTHLY);
    f.setValidFrom(DATE);
    f.setValidTo(DATE.plusMonths(1));
    assertTrue(LongTermAssetCalculator.applies(f, DATE));
    assertTrue(LongTermAssetCalculator.applies(f, DATE.plusMonths(1)));
    assertFalse(LongTermAssetCalculator.applies(f, DATE.plusMonths(2)));
  }

  @Test
  void overlappingPeriodsAreRejected() {
    LongTermAssetCalculator.Period a = period(DATE, DATE.plusMonths(2));
    LongTermAssetCalculator.Period b = period(DATE.plusMonths(1), null);
    assertTrue(a.overlaps(b));
  }

  @Test
  void zeroValueYieldIsZero() {
    assertEquals(
        BigDecimal.ZERO, LongTermAssetCalculator.ratio(new BigDecimal("10"), BigDecimal.ZERO));
  }

  private static LongTermAssetCashFlowEntity flow(CashFlowType type, String amount, Frequency frequency) {
    LongTermAssetCashFlowEntity f = new LongTermAssetCashFlowEntity();
    f.setType(type);
    f.setAmount(new BigDecimal(amount));
    f.setFrequency(frequency);
    f.setValidFrom(DATE);
    return f;
  }

  private static LongTermAssetCalculator.Period period(LocalDate from, LocalDate to) {
    return new LongTermAssetCalculator.Period() {
      public LocalDate from() {
        return from;
      }

      public LocalDate to() {
        return to;
      }
    };
  }
}
