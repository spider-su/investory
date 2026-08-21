package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.retirement.planning.PlanningMetric;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.ui.presentation.UiPresentation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UiPresentationTest {
  @Test
  void formatsMoneyPercentAndZero() {
    assertEquals("56,940.44", UiPresentation.money(new BigDecimal("56940.444")));
    assertEquals("7.5%", UiPresentation.percentage(new BigDecimal("0.07548783")));
    assertEquals("0", UiPresentation.money(BigDecimal.ZERO.setScale(8)));
    assertEquals("0.0%", UiPresentation.percentage(BigDecimal.ZERO.setScale(8)));
    assertEquals("56,940 PLN", UiPresentation.moneyWhole(new BigDecimal("56940.444"), "PLN"));
    assertEquals("2000", UiPresentation.wholeNumberInput(new BigDecimal("2000.000000000000")));
    assertEquals("1,030", UiPresentation.money(new BigDecimal("1030.00000000")));
    assertEquals("1,030.50", UiPresentation.money(new BigDecimal("1030.50000000")));
    assertEquals("1,030.57", UiPresentation.money(new BigDecimal("1030.5678")));
    assertEquals("7.0%", UiPresentation.percentage(new BigDecimal("0.07")));
    assertEquals("75.0%", UiPresentation.percentage(new BigDecimal("0.75")));
    assertEquals("2.5", UiPresentation.percentageInput(new BigDecimal("0.025")));
    assertEquals("7.0", UiPresentation.percentageInput(new BigDecimal("0.07")));
    assertEquals("1030.5", UiPresentation.moneyInput(new BigDecimal("1030.50000000")));
    assertEquals("1.03", UiPresentation.decimal(new BigDecimal("1.03000000")));
    assertEquals("2.0", UiPresentation.years(new BigDecimal("2")));
    assertEquals("1.2", UiPresentation.years(new BigDecimal("1.234")));
  }

  @Test
  void labelsPlanningEnumsForPeople() {
    assertEquals("Fixed income", UiPresentation.bucket(EconomicBucket.FIXED_INCOME));
    assertEquals("Parking rent", UiPresentation.cashFlowType(CashFlowType.PARKING_RENT));
    assertEquals("Parking rent", UiPresentation.cashFlowType(CashFlowTypeModel.PARKING_RENT));
    assertEquals("Accumulative", UiPresentation.interestTreatment(InterestTreatment.CAPITALIZE));
    assertEquals("Distributed", UiPresentation.interestTreatment(InterestTreatment.PAY_OUT));
  }

  @Test
  void formatsHistoricalMetricsByDeclaredUnit() {
    assertEquals(
        "45,000",
        UiPresentation.planningMetric(PlanningMetric.CORE_SPENDING, new BigDecimal("45000")));
    assertEquals(
        "45,000 PLN",
        UiPresentation.planningMetric(
            PlanningMetric.CORE_SPENDING, new BigDecimal("45000"), "PLN"));
    assertEquals(
        "-2.0%",
        UiPresentation.planningMetric(
            PlanningMetric.MARKET_RETURN, new BigDecimal("-0.0203"), "PLN"));
    assertEquals(
        "7.0%",
        UiPresentation.planningMetric(PlanningMetric.EQUITY_RETURN, new BigDecimal("0.07"), "EUR"));
  }
}
