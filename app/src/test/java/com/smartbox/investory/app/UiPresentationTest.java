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
    assertEquals("7.5 %", UiPresentation.percentage(new BigDecimal("0.07548783")));
    assertEquals("0", UiPresentation.money(BigDecimal.ZERO.setScale(8)));
    assertEquals("0.0 %", UiPresentation.percentage(BigDecimal.ZERO.setScale(8)));
    assertEquals("56,940 PLN", UiPresentation.moneyWhole(new BigDecimal("56940.444"), "PLN"));
    assertEquals("2000", UiPresentation.wholeNumberInput(new BigDecimal("2000.000000000000")));
    assertEquals("1,030", UiPresentation.money(new BigDecimal("1030.00000000")));
    assertEquals("1,030.50", UiPresentation.money(new BigDecimal("1030.50000000")));
    assertEquals("1,030.57", UiPresentation.money(new BigDecimal("1030.5678")));
    assertEquals("0", UiPresentation.money(new BigDecimal("0E-8")));
    assertEquals("38,880", UiPresentation.money(new BigDecimal("38880.00015480")));
    assertEquals("178,961.17", UiPresentation.money(new BigDecimal("178961.16944085")));
    assertEquals("7.0 %", UiPresentation.percentage(new BigDecimal("0.07")));
    assertEquals("75.0 %", UiPresentation.percentage(new BigDecimal("0.75")));
    assertEquals("2.5", UiPresentation.percentageInput(new BigDecimal("0.025")));
    assertEquals("7.0", UiPresentation.percentageInput(new BigDecimal("0.07")));
    assertEquals("1030.5", UiPresentation.moneyInput(new BigDecimal("1030.50000000")));
    assertEquals("1.03", UiPresentation.decimal(new BigDecimal("1.03000000")));
    assertEquals("2", UiPresentation.years(new BigDecimal("2")));
    assertEquals("1.2", UiPresentation.years(new BigDecimal("1.234")));
  }

  @Test
  void formatsCompactSummaryMoney() {
    assertEquals("999", UiPresentation.compactMoney(new BigDecimal("999")));
    assertEquals("1K", UiPresentation.compactMoney(new BigDecimal("1000")));
    assertEquals("1.3K", UiPresentation.compactMoney(new BigDecimal("1250")));
    assertEquals("174.8K", UiPresentation.compactMoney(new BigDecimal("174803.62")));
    assertEquals("900K", UiPresentation.compactMoney(new BigDecimal("900000")));
    assertEquals("1M", UiPresentation.compactMoney(new BigDecimal("1000000")));
    assertEquals("4.55M", UiPresentation.compactMoney(new BigDecimal("4550000")));
    assertEquals("-174.8K", UiPresentation.compactMoney(new BigDecimal("-174803.62")));
    assertEquals("1M", UiPresentation.compactMoney(new BigDecimal("999950")));
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
        "-2.0 %",
        UiPresentation.planningMetric(
            PlanningMetric.MARKET_RETURN, new BigDecimal("-0.0203"), "PLN"));
    assertEquals(
        "7.0 %",
        UiPresentation.planningMetric(PlanningMetric.EQUITY_RETURN, new BigDecimal("0.07"), "EUR"));
  }
}
