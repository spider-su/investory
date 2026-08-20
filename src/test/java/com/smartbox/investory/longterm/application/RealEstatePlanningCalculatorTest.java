package com.smartbox.investory.longterm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.longterm.api.CashFlowType;
import com.smartbox.investory.longterm.api.Frequency;
import com.smartbox.investory.longterm.infrastructure.LongTermAssetCashFlow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealEstatePlanningCalculatorTest {
  private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

  @Test
  void preservesCurrentApartmentPlanningFormula() {
    RealEstatePlanningSummary result =
        new RealEstatePlanningCalculator()
            .calculate(
                new BigDecimal("780000"),
                new BigDecimal("33600"),
                List.of(
                    flow(CashFlowType.RENT, "2650", Frequency.MONTHLY),
                        flow(CashFlowType.PARKING_RENT, "250", Frequency.MONTHLY),
                    flow(CashFlowType.ADMIN_FEE, "687", Frequency.MONTHLY),
                        flow(CashFlowType.UTILITIES, "100", Frequency.MONTHLY),
                    flow(CashFlowType.OTHER_INCOME, "75", Frequency.MONTHLY),
                        flow(CashFlowType.OTHER_EXPENSE, "400", Frequency.MONTHLY),
                    flow(CashFlowType.PROPERTY_TAX, "320", Frequency.ANNUAL),
                        flow(CashFlowType.INSURANCE, "200", Frequency.ANNUAL)),
                DATE);

    assertEquals(new BigDecimal("3687"), result.totalPaymentMonthly());
    assertEquals(new BigDecimal("2975"), result.monthlyIncome());
    assertEquals(new BigDecimal("2856.000"), result.annualTax());
    assertEquals(new BigDecimal("281.333333333333333333"), result.monthlyReduce());
    assertEquals(new BigDecimal("2693.666666666666666667"), result.netMonthlyIncome());
    assertEquals(new BigDecimal("0.041441025641"), result.incomeYield());
  }

  private static LongTermAssetCashFlow flow(CashFlowType type, String amount, Frequency frequency) {
    LongTermAssetCashFlow flow = new LongTermAssetCashFlow();
    flow.setType(type);
    flow.setAmount(new BigDecimal(amount));
    flow.setFrequency(frequency);
    flow.setValidFrom(DATE.minusDays(1));
    return flow;
  }
}
