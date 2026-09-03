package com.smartbox.investory.ui.profile;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.presentation.UiPresentation;
import java.math.BigDecimal;
import java.util.List;

/** Presentation-only profile model consumed by the investment profile template. */
record InvestmentProfilePageView(
    Long portfolioId,
    CurrencyType currency,
    String totalNetWorthCompactDisplay,
    String marketPortfolioMeta,
    String marketPortfolioValueCompactDisplay,
    String longTermAssetMeta,
    String longTermAssetValueCompactDisplay,
    String shortTermAssetsPercentageDisplay,
    String longTermAssetsPercentageDisplay,
    String marketAnnualizedReturnDisplay,
    String marketKpiMeta,
    String marketReceivedYtdDisplay,
    String longTermReceivedYtdDisplay,
    String annualCostDisplay,
    String annualCostMeta,
    IncomeView incomeSummary,
    List<AllocationView> allocations) {

  static InvestmentProfilePageView from(InvestmentProfile profile) {
    return from(
        profile,
        new InvestmentDashboardApi.PerformanceKpiView(false, null, "Unavailable", null),
        InvestmentDashboardApi.InvestmentResultView.unavailable(profile.currency()),
        com.smartbox.investory.retirement.api.model.AnnualCostView.unavailable(
            profile.currency(), 0),
        12);
  }

  static InvestmentProfilePageView from(
      InvestmentProfile profile,
      InvestmentDashboardApi.PerformanceKpiView performance,
      InvestmentDashboardApi.InvestmentResultView investmentResult,
      com.smartbox.investory.retirement.api.model.AnnualCostView annualCost,
      int currentMonth) {
    return new InvestmentProfilePageView(
        profile.portfolioId(),
        profile.currency(),
        UiPresentation.compactMoney(profile.totalNetWorth()),
        UiPresentation.percentage(profile.marketPortfolioPercentage()) + " of net worth",
        UiPresentation.compactMoney(profile.marketPortfolioValue()),
        UiPresentation.percentage(profile.longTermAssetPercentage()) + " of net worth",
        UiPresentation.compactMoney(profile.longTermAssetValue()),
        UiPresentation.percentage(profile.marketPortfolioPercentage()),
        UiPresentation.percentage(profile.longTermAssetPercentage()),
        performance.available() && performance.annualizedReturn() != null
            ? UiPresentation.percentage(performance.annualizedReturn())
            : "Unavailable",
        performance.kpiStartDate() == null
            ? "Total return"
            : "KPI start · " + performance.kpiStartDate(),
        availableWholeMoney(investmentResult.available(), investmentResult.amount()),
        plannedYtdMoney(profile.incomeSummary().longTermIncomeToDate(currentMonth)),
        availableMoney(annualCost.available(), annualCost.amount()),
        annualCost.available() ? "planned · " + annualCost.year() : "No retirement plan",
        IncomeView.from(profile.incomeSummary()),
        profile.allocations().stream().map(AllocationView::from).toList());
  }

  private static String plannedYtdMoney(BigDecimal amount) {
    return amount == null ? "—" : UiPresentation.compactMoney(amount);
  }

  private static String availableMoney(boolean available, BigDecimal amount) {
    return available && amount != null ? UiPresentation.compactMoney(amount) : "—";
  }

  private static String availableWholeMoney(boolean available, BigDecimal amount) {
    return available && amount != null ? UiPresentation.wholeNumber(amount) : "—";
  }

  record IncomeView(
      String marketIncomeYtdCompactDisplay,
      String marketAnnualIncomeCompactDisplay,
      String marketNetYieldDisplay,
      String longTermAnnualIncomeCompactDisplay,
      String longTermNetYieldDisplay,
      String combinedAnnualIncomeCompactDisplay,
      String combinedNetYieldDisplay) {

    static IncomeView from(ProfileIncomeSummary income) {
      return new IncomeView(
          UiPresentation.compactMoney(income.marketIncomeYtd()),
          UiPresentation.compactMoney(income.marketAnnualIncome()),
          UiPresentation.percentage(income.marketNetYield()),
          UiPresentation.compactMoney(income.longTermAnnualIncome()),
          UiPresentation.percentage(income.longTermNetYield()),
          UiPresentation.compactMoney(income.combinedAnnualIncome()),
          UiPresentation.percentage(income.combinedNetYield()));
    }
  }

  record AllocationView(
      EconomicBucket bucket,
      BigDecimal percentage,
      boolean nonZero,
      String bucketLabel,
      String horizonLabel,
      String compactValueDisplay,
      String percentageDisplay,
      String allocationCssClass,
      String legendLabel) {

    static AllocationView from(ProfileAllocation allocation) {
      String bucketLabel = UiPresentation.bucket(allocation.bucket());
      String percentageDisplay = UiPresentation.percentage(allocation.percentage());
      return new AllocationView(
          allocation.bucket(),
          allocation.percentage(),
          allocation.isNonZero(),
          bucketLabel,
          allocation.assetHorizon() == AssetHorizon.LONG_TERM
              ? "Long-term asset"
              : "Short-term asset",
          UiPresentation.compactMoney(allocation.value()),
          percentageDisplay,
          cssClass(allocation.bucket()),
          bucketLabel + " · " + percentageDisplay);
    }

    private static String cssClass(EconomicBucket bucket) {
      return switch (bucket) {
        case EQUITY -> "iv-allocation--equity";
        case REAL_ESTATE -> "iv-allocation--real-estate";
        case FIXED_INCOME -> "iv-allocation--fixed-income";
        case LIQUID_CASH -> "iv-allocation--cash";
        case OTHER -> "iv-allocation--other";
      };
    }
  }
}
