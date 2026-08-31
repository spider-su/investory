package com.smartbox.investory.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.application.planning.PlanningMetric;
import com.smartbox.investory.application.profile.EconomicBucket;
import com.smartbox.investory.infrastructure.longterm.CashFlowType;
import com.smartbox.investory.infrastructure.longterm.InterestTreatment;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlanningPresentationTest {
  @Test
  void formatsMoneyPercentAndZero() {
    assertEquals("56,940.44", PlanningPresentation.money(new BigDecimal("56940.444")));
    assertEquals("7.5%", PlanningPresentation.percentage(new BigDecimal("0.07548783")));
    assertEquals("0", PlanningPresentation.money(BigDecimal.ZERO.setScale(8)));
    assertEquals("0.0%", PlanningPresentation.percentage(BigDecimal.ZERO.setScale(8)));
    assertEquals("56,940 PLN", PlanningPresentation.moneyWhole(new BigDecimal("56940.444"), "PLN"));
    assertEquals(
        "2000", PlanningPresentation.wholeNumberInput(new BigDecimal("2000.000000000000")));
    assertEquals("1,030", PlanningPresentation.money(new BigDecimal("1030.00000000")));
    assertEquals("1,030.50", PlanningPresentation.money(new BigDecimal("1030.50000000")));
    assertEquals("1,030.57", PlanningPresentation.money(new BigDecimal("1030.5678")));
    assertEquals("7.0%", PlanningPresentation.percentage(new BigDecimal("0.07")));
    assertEquals("75.0%", PlanningPresentation.percentage(new BigDecimal("0.75")));
    assertEquals("2.5", PlanningPresentation.percentageInput(new BigDecimal("0.025")));
    assertEquals("7.0", PlanningPresentation.percentageInput(new BigDecimal("0.07")));
    assertEquals("1030.5", PlanningPresentation.moneyInput(new BigDecimal("1030.50000000")));
    assertEquals("1.03", PlanningPresentation.decimal(new BigDecimal("1.03000000")));
    assertEquals("2.0", PlanningPresentation.years(new BigDecimal("2")));
    assertEquals("1.2", PlanningPresentation.years(new BigDecimal("1.234")));
  }

  @Test
  void labelsPlanningEnumsForPeople() {
    assertEquals("Fixed income", PlanningPresentation.bucket(EconomicBucket.FIXED_INCOME));
    assertEquals("Parking rent", PlanningPresentation.cashFlowType(CashFlowType.PARKING_RENT));
    assertEquals(
        "Accumulative", PlanningPresentation.interestTreatment(InterestTreatment.CAPITALIZE));
    assertEquals("Distributed", PlanningPresentation.interestTreatment(InterestTreatment.PAY_OUT));
  }

  @Test
  void formatsHistoricalMetricsByDeclaredUnit() {
    assertEquals(
        "45,000",
        PlanningPresentation.planningMetric(PlanningMetric.CORE_SPENDING, new BigDecimal("45000")));
    assertEquals(
        "45,000 PLN",
        PlanningPresentation.planningMetric(
            PlanningMetric.CORE_SPENDING, new BigDecimal("45000"), "PLN"));
    assertEquals(
        "-2.0%",
        PlanningPresentation.planningMetric(
            PlanningMetric.MARKET_RETURN, new BigDecimal("-0.0203"), "PLN"));
    assertEquals(
        "7.0%",
        PlanningPresentation.planningMetric(
            PlanningMetric.EQUITY_RETURN, new BigDecimal("0.07"), "EUR"));
  }
}
