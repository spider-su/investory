package com.smartbox.investory.ui.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import com.smartbox.investory.ui.investment.InvestmentDashboardClient;
import com.smartbox.investory.ui.investment.InvestmentPerformanceKpi;
import com.smartbox.investory.ui.investment.InvestmentResult;
import com.smartbox.investory.ui.retirement.simulation.FinancialLandscapePageAssembler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    PortfolioContextReader contexts = mock(PortfolioContextReader.class);
    InvestmentProfile profile = profile();
    when(profiles.loadProfile(7L)).thenReturn(profile);
    when(investment.loadPerformanceKpi(7L))
        .thenReturn(
            new InvestmentPerformanceKpi(
                true,
                new BigDecimal("0.173"),
                "+17.3%",
                "2026-01-01",
                null,
                "Unavailable",
                new BigDecimal("0.281"),
                "+28.1%",
                null,
                "Benchmark estimate"));
    when(investment.investmentResultYtd(7L))
        .thenReturn(new InvestmentResult(true, new BigDecimal("20483"), CurrencyType.PLN));
    when(retirement.currentYearAnnualCost(7L, CurrencyType.PLN))
        .thenReturn(
            new com.smartbox.investory.retirement.api.model.AnnualCostView(
                true, new BigDecimal("42000"), CurrencyType.PLN, 2026, 11L));
    var controller =
        new InvestmentProfileController(
            profiles,
            investment,
            retirement,
            contexts,
            Runnable::run,
            Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC),
            mock(FinancialLandscapePageAssembler.class));
    var model = new ConcurrentModel();

    String template = controller.profile(7L, model);

    assertThat(template).isEqualTo("investment-profile");
    var page = (InvestmentProfilePageView) model.getAttribute("profile");
    assertThat(page).isNotNull();
    assertThat(page.portfolioId()).isEqualTo(7L);
    assertThat(page.marketInvestmentResultYtdDisplay()).isEqualTo("20.5K");
    assertThat(page.longTermPlannedIncomeYtdDisplay()).isEqualTo("8.00K");
    assertThat(page.annualCostDisplay()).isEqualTo("42.0K");
    assertThat(page.annualCostMeta()).isEqualTo("planned · 2026");
    assertThat(page.expectedAnnualInvestmentResultDisplay()).isEqualTo("2.67K");
    assertThat(page.expectedAnnualReturnDisplay()).isEqualTo("1.6%");
    assertThat(page.marketYtdReturnDisplay()).isEqualTo("43.4% of forecast annual result");
    assertThat(page.allocationApproximate()).isFalse();
    verify(investment).investmentResultYtd(7L);
    verify(retirement).currentYearAnnualCost(7L, CurrencyType.PLN);
  }

  @DisplayName("reuses investment results already present in profile")
  @Test
  void reusesInvestmentResultsAlreadyPresentInProfile() {
    ProfileClient profiles = mock(ProfileClient.class);
    InvestmentDashboardClient investment = mock(InvestmentDashboardClient.class);
    RetirementProfileClient retirement = mock(RetirementProfileClient.class);
    PortfolioContextReader contexts = mock(PortfolioContextReader.class);
    InvestmentProfile profile = profileWithInvestmentIncome();
    when(profiles.loadProfile(7L)).thenReturn(profile);
    when(retirement.currentYearAnnualCost(7L, CurrencyType.PLN))
        .thenReturn(
            new com.smartbox.investory.retirement.api.model.AnnualCostView(
                true, new BigDecimal("42000"), CurrencyType.PLN, 2026, 11L));
    var controller =
        new InvestmentProfileController(
            profiles,
            investment,
            retirement,
            contexts,
            Runnable::run,
            Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC),
            mock(FinancialLandscapePageAssembler.class));

    String template = controller.profile(7L, new ConcurrentModel());

    assertThat(template).isEqualTo("investment-profile");
    verifyNoInteractions(investment);
  }

  @DisplayName("loads profile and annual cost in parallel")
  @Test
  void loadsProfileAndAnnualCostInParallel() throws Exception {
    ProfileClient profiles = mock(ProfileClient.class);
    InvestmentDashboardClient investment = mock(InvestmentDashboardClient.class);
    RetirementProfileClient retirement = mock(RetirementProfileClient.class);
    PortfolioContextReader contexts = mock(PortfolioContextReader.class);
    CountDownLatch started = new CountDownLatch(2);
    when(contexts.findById(7L))
        .thenReturn(Optional.of(new PortfolioContext(7L, CurrencyType.PLN, CurrencyType.PLN)));
    when(profiles.loadProfile(7L))
        .thenAnswer(
            invocation -> {
              started.countDown();
              assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
              return profileWithInvestmentIncome();
            });
    when(retirement.currentYearAnnualCost(7L, CurrencyType.PLN))
        .thenAnswer(
            invocation -> {
              started.countDown();
              assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
              return new com.smartbox.investory.retirement.api.model.AnnualCostView(
                  true, new BigDecimal("42000"), CurrencyType.PLN, 2026, 11L);
            });
    try (var executor = Executors.newFixedThreadPool(2)) {
      var controller =
          new InvestmentProfileController(
              profiles,
              investment,
              retirement,
              contexts,
              executor,
              Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC),
              mock(FinancialLandscapePageAssembler.class));

      String template = controller.profile(7L, new ConcurrentModel());

      assertThat(template).isEqualTo("investment-profile");
    }
    verifyNoInteractions(investment);
  }

  private static InvestmentProfile profileWithInvestmentIncome() {
    InvestmentProfile base = profile();
    var income =
        new com.smartbox.investory.profile.api.model.ProfileIncomeSummary(
            new BigDecimal("1000"),
            new BigDecimal("1200"),
            new BigDecimal("0.06"),
            new BigDecimal("12000"),
            new BigDecimal("0.1"),
            new BigDecimal("13200"),
            new BigDecimal("0.11"),
            new BigDecimal("20000"),
            new BigDecimal("1400"),
            new BigDecimal("0.07"),
            new BigDecimal("1000"),
            new BigDecimal("933"),
            new BigDecimal("0.714"),
            true,
            new BigDecimal("810"),
            new BigDecimal("972"),
            new BigDecimal("12972"));
    return new InvestmentProfile(
        base.portfolioId(),
        base.currency(),
        base.marketPortfolioValue(),
        base.longTermAssetValue(),
        base.totalNetWorth(),
        base.liquidAssets(),
        base.illiquidAssets(),
        base.allocations(),
        base.currentRentalIncome(),
        base.currentBondIncome(),
        base.longTermPlanningState(),
        base.retirementReserve(),
        base.investmentCapital(),
        income,
        base.allocationReconciliation());
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
