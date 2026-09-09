package com.smartbox.investory.investment.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.reporting.InvestmentAnnualProjectionApi;
import com.smartbox.investory.shared.projection.ProjectionSource;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Investment annual projection")
class InvestmentAnnualProjectionServiceTest {
  private final InvestmentAnnualProjectionService service = new InvestmentAnnualProjectionService();

  @Test
  void projectsReturnAfterContributionAndCapsWithdrawalAtAvailableCapital() {
    var result =
        service.project(
            new InvestmentAnnualProjectionApi.ProjectionRequest(
                2030, bd("1000"), bd("250"), bd("0.10"), bd("4000"), ProjectionSource.PROJECTED));

    assertThat(result.year()).isEqualTo(2030);
    assertThat(result.annualReturnAmount()).isEqualByComparingTo("100");
    assertThat(result.withdrawal()).isEqualByComparingTo("1350");
    assertThat(result.endValue()).isEqualByComparingTo("0");
    assertThat(result.source()).isEqualTo(ProjectionSource.PROJECTED);
  }

  @Test
  void normalizesNullsAndRejectsNegativeContributionAndWithdrawal() {
    var result =
        service.project(
            new InvestmentAnnualProjectionApi.ProjectionRequest(
                2031, null, bd("-50"), null, bd("-10"), null));

    assertThat(result.startValue()).isEqualByComparingTo("0");
    assertThat(result.externalContribution()).isEqualByComparingTo("0");
    assertThat(result.annualReturnAmount()).isEqualByComparingTo("0");
    assertThat(result.withdrawal()).isEqualByComparingTo("0");
    assertThat(result.endValue()).isEqualByComparingTo("0");
    assertThat(result.source()).isEqualTo(ProjectionSource.PROJECTED);
  }

  @Test
  void capitalProjectionPreservesRequestedAndActualWithdrawalSeparately() {
    InvestmentAnnualProjectionApi.CapitalProjection result =
        service.projectCapital(
            new InvestmentAnnualProjectionApi.CapitalRequest(
                2032, bd("1000"), bd("200"), bd("0.05"), bd("500"), ProjectionSource.ACTUAL));

    assertThat(result.availableForWithdrawal()).isEqualByComparingTo("1250");
    assertThat(result.requestedWithdrawal()).isEqualByComparingTo("500");
    assertThat(result.actualWithdrawal()).isEqualByComparingTo("500");
    assertThat(result.unfundedRequest()).isEqualByComparingTo("0");
    assertThat(result.endValue()).isEqualByComparingTo("750");
    assertThat(result.annualIncome()).isEqualByComparingTo("0");
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
