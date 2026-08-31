package com.smartbox.investory.ui.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.investment.InvestmentDashboardClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

@DisplayName("Investment Profile Controller")
class InvestmentProfileControllerTest {

  @DisplayName("composes Actual Income And Planned Cost From Owning Module Clients")
  @Test
  void composesActualIncomeAndPlannedCostFromOwningModuleClients() {
    ProfileClient profiles = mock(ProfileClient.class);
    InvestmentDashboardClient investment = mock(InvestmentDashboardClient.class);
    RetirementProfileClient retirement = mock(RetirementProfileClient.class);
    InvestmentProfile profile = profile();
    when(profiles.loadProfile(7L)).thenReturn(profile);
    when(investment.loadPerformanceKpi(7L))
        .thenReturn(
            new InvestmentDashboardApi.PerformanceKpiView(
                true, new BigDecimal("0.281"), "+28.1%", "2026-01-01"));
    when(investment.investmentResult(7L))
        .thenReturn(
            new InvestmentDashboardApi.InvestmentResultView(
                true, new BigDecimal("20483"), CurrencyType.PLN));
    when(retirement.currentYearAnnualCost(7L, CurrencyType.PLN))
        .thenReturn(
            new com.smartbox.investory.retirement.api.model.AnnualCostView(
                true, new BigDecimal("42000"), CurrencyType.PLN, 2026, 11L));
    var controller =
        new InvestmentProfileController(
            profiles,
            investment,
            retirement,
            Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC));
    var model = new ConcurrentModel();

    String template = controller.profile(7L, model);

    assertThat(template).isEqualTo("investment-profile");
    assertThat(model.getAttribute("profileMarketYtdIncome")).isEqualTo("3.3K");
    assertThat(model.getAttribute("profileMarketInvestmentResult")).isEqualTo("20,483");
    assertThat(model.getAttribute("profileLongTermYtdIncome")).isEqualTo("8.0K");
    assertThat(model.getAttribute("profileAnnualCost")).isEqualTo("42.0K");
    assertThat(model.getAttribute("profileAnnualCostMeta")).isEqualTo("planned · 2026");
    assertThat(model.getAttribute("profileMarketAnnualizedReturn")).isEqualTo("28.1%");
    verify(investment).investmentResult(7L);
    verify(retirement).currentYearAnnualCost(7L, CurrencyType.PLN);
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        7L,
        CurrencyType.PLN,
        new BigDecimal("168000"),
        new BigDecimal("1260000"),
        new BigDecimal("1428000"),
        new BigDecimal("168000"),
        new BigDecimal("1260000"),
        List.of(),
        null,
        null,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (new BigDecimal("168000") == null ? java.math.BigDecimal.ZERO : new BigDecimal("168000")),
        new BigDecimal("168000")
            .subtract(
                (new BigDecimal("168000") == null
                    ? java.math.BigDecimal.ZERO
                    : new BigDecimal("168000")))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            new BigDecimal("3300"),
            new BigDecimal("168000"),
            new BigDecimal("12000"),
            new BigDecimal("1260000"),
            BigDecimal.ZERO,
            new BigDecimal("1428000")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }
}
