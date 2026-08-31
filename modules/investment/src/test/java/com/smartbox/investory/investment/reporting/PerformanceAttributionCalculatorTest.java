package com.smartbox.investory.investment.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.reporting.model.PerformanceAttribution;
import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Performance Attribution Calculator")
class PerformanceAttributionCalculatorTest {
  @DisplayName("pure Unrealized Or Fx Movement Remains Explicit Residual")
  @Test
  void pureUnrealizedOrFxMovementRemainsExplicitResidual() {
    PerformanceAttribution attribution =
        PerformanceAttributionCalculator.from(result("10", "0", "0", "0", "0", "0"));

    assertThat(attribution.unrealizedProfitLoss()).isNull();
    assertThat(attribution.fxEffect()).isNull();
    assertThat(attribution.residual()).isEqualByComparingTo("10");
    assertThat(attribution.residualMaterial()).isTrue();
    assertThat(attribution.reconcilesWithinTolerance()).isFalse();
  }

  @DisplayName("fee And Tax Effects Use Positive Expense Presentation")
  @Test
  void feeAndTaxEffectsUsePositiveExpensePresentation() {
    PerformanceAttribution attribution =
        PerformanceAttributionCalculator.from(result("-3", "0", "0", "0", "3", "0"));

    assertThat(attribution.fees()).isEqualByComparingTo("3");
    assertThat(attribution.residual()).isZero();
    assertThat(attribution.reconcilesWithinTolerance()).isTrue();
  }

  @DisplayName("mixed Canonical Components Reconcile Without Double Counting")
  @Test
  void mixedCanonicalComponentsReconcileWithoutDoubleCounting() {
    PerformanceAttribution attribution =
        PerformanceAttributionCalculator.from(result("4", "5", "2", "1", "3", "1"));

    assertThat(attribution.totalAttributedResult()).isEqualByComparingTo("4");
    assertThat(attribution.residual()).isZero();
    assertThat(attribution.reconcilesWithinTolerance()).isTrue();
  }

  private static PerformanceResult result(
      String investment,
      String realized,
      String dividends,
      String interest,
      String fees,
      String taxes) {
    return new PerformanceResult(
        new PerformancePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
        CurrencyType.USD,
        bd("100"),
        bd("100"),
        bd("0"),
        bd("0"),
        bd("0"),
        bd(investment),
        bd(realized),
        null,
        bd(dividends),
        bd(interest),
        bd(fees),
        bd(taxes),
        null,
        ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "test"),
        ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "test"),
        null);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
