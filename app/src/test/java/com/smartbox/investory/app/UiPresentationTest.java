package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.retirement.api.model.PlanningMetric;
import com.smartbox.investory.ui.presentation.UiPresentation;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UI Presentation")
class UiPresentationTest {
  @DisplayName("formats Money Percent And Zero")
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
    assertEquals("0", UiPresentation.money(new BigDecimal("0E-8")));
    assertEquals("38,880", UiPresentation.money(new BigDecimal("38880.00015480")));
    assertEquals("178,961.17", UiPresentation.money(new BigDecimal("178961.16944085")));
    assertEquals("7.0%", UiPresentation.percentage(new BigDecimal("0.07")));
    assertEquals("75.0%", UiPresentation.percentage(new BigDecimal("0.75")));
    assertEquals("2.5", UiPresentation.percentageInput(new BigDecimal("0.025")));
    assertEquals("7.0", UiPresentation.percentageInput(new BigDecimal("0.07")));
    assertEquals("4.75", UiPresentation.percentageInput(new BigDecimal("0.0475")));
    assertEquals("1030.5", UiPresentation.moneyInput(new BigDecimal("1030.50000000")));
    assertEquals("1.03", UiPresentation.decimal(new BigDecimal("1.03000000")));
    assertEquals("2", UiPresentation.years(new BigDecimal("2")));
    assertEquals("1.2", UiPresentation.years(new BigDecimal("1.234")));
    assertEquals("+1.0 pp", UiPresentation.percentagePoints(new BigDecimal("1")));
    assertEquals("-2.0 pp", UiPresentation.percentagePoints(new BigDecimal("-2")));
    assertEquals("0.0 pp", UiPresentation.percentagePoints(BigDecimal.ZERO));
  }

  @DisplayName("formats Compact Summary Money")
  @Test
  void formatsCompactSummaryMoney() {
    assertEquals("999", UiPresentation.compactMoney(new BigDecimal("999")));
    assertEquals("1.0K", UiPresentation.compactMoney(new BigDecimal("1000")));
    assertEquals("1.3K", UiPresentation.compactMoney(new BigDecimal("1250")));
    assertEquals("174.8K", UiPresentation.compactMoney(new BigDecimal("174803.62")));
    assertEquals("900.0K", UiPresentation.compactMoney(new BigDecimal("900000")));
    assertEquals("1M", UiPresentation.compactMoney(new BigDecimal("1000000")));
    assertEquals("4.55M", UiPresentation.compactMoney(new BigDecimal("4550000")));
    assertEquals("-174.8K", UiPresentation.compactMoney(new BigDecimal("-174803.62")));
    assertEquals("1M", UiPresentation.compactMoney(new BigDecimal("999950")));
    assertEquals("+36.6K", UiPresentation.signedCompactMoney(new BigDecimal("36600")));
    assertEquals("−36.6K", UiPresentation.signedCompactMoney(new BigDecimal("-36600")));
  }

  @DisplayName("labels Planning Enums For People")
  @Test
  void labelsPlanningEnumsForPeople() {
    assertEquals("Fixed income", UiPresentation.bucket(EconomicBucket.FIXED_INCOME));
    assertEquals("Parking rent", UiPresentation.cashFlowType(CashFlowType.PARKING_RENT));
    assertEquals("Parking rent", UiPresentation.cashFlowType(CashFlowType.PARKING_RENT));
    assertEquals("Accumulative", UiPresentation.interestTreatment(InterestTreatment.CAPITALIZE));
    assertEquals("Distributed", UiPresentation.interestTreatment(InterestTreatment.PAY_OUT));
  }

  @DisplayName("formats Historical Metrics By Declared Unit")
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
