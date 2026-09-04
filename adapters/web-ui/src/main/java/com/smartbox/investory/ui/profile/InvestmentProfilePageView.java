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
import java.util.Comparator;
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
    String marketAnnualIncomeDisplay,
    String shortTermAssetsPercentageDisplay,
    String longTermAssetsPercentageDisplay,
    String marketAnnualizedReturnDisplay,
    String marketKpiMeta,
    String marketReceivedYtdDisplay,
    String marketReceivedYtdProgressDisplay,
    String marketReceivedYtdProgressClass,
    String longTermReceivedYtdDisplay,
    String longTermReceivedYtdProgressDisplay,
    String longTermReceivedYtdProgressClass,
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
    BigDecimal marketAnnualIncome =
        performance.annualizedIncome() == null
            ? profile.incomeSummary().marketAnnualIncome()
            : performance.annualizedIncome();
    return new InvestmentProfilePageView(
        profile.portfolioId(),
        profile.currency(),
        UiPresentation.compactMoney(profile.totalNetWorth()),
        UiPresentation.percentage(profile.marketPortfolioPercentage()) + " of net worth",
        UiPresentation.compactMoney(
            marketValueAtYearStart(profile.marketPortfolioValue(), investmentResult)),
        UiPresentation.percentage(profile.longTermAssetPercentage()) + " of net worth",
        UiPresentation.compactMoney(profile.longTermAssetValue()),
        UiPresentation.compactMoney(marketAnnualIncome),
        UiPresentation.percentage(profile.marketPortfolioPercentage()),
        UiPresentation.percentage(profile.longTermAssetPercentage()),
        performance.available() && performance.annualizedReturn() != null
            ? UiPresentation.percentage(performance.annualizedReturn())
            : "Unavailable",
        performance.kpiStartDate() == null ? "Total return" : "Since " + performance.kpiStartDate(),
        ytdMoney(
            investmentResult.available() ? investmentResult.amount() : null, marketAnnualIncome),
        ytdProgress(
            investmentResult.available() ? investmentResult.amount() : null,
            marketAnnualIncome,
            currentMonth),
        ytdProgressClass(
            investmentResult.available() ? investmentResult.amount() : null,
            marketAnnualIncome,
            currentMonth),
        ytdMoney(
            profile.incomeSummary().longTermIncomeToDate(currentMonth),
            profile.incomeSummary().longTermAnnualIncome()),
        ytdProgress(
            profile.incomeSummary().longTermIncomeToDate(currentMonth),
            profile.incomeSummary().longTermAnnualIncome(),
            currentMonth),
        ytdProgressClass(
            profile.incomeSummary().longTermIncomeToDate(currentMonth),
            profile.incomeSummary().longTermAnnualIncome(),
            currentMonth),
        availableMoney(annualCost.available(), annualCost.amount()),
        annualCost.available() ? "planned · " + annualCost.year() : "No retirement plan",
        IncomeView.from(profile.incomeSummary(), marketAnnualIncome),
        profile.allocations().stream()
            .map(allocation -> AllocationView.from(allocation, profile.incomeSummary()))
            .sorted(Comparator.comparing(AllocationView::percentage).reversed())
            .toList());
  }

  private static BigDecimal marketValueAtYearStart(
      BigDecimal currentMarketValue, InvestmentDashboardApi.InvestmentResultView investmentResult) {
    return investmentResult.available() && investmentResult.amount() != null
        ? currentMarketValue.subtract(investmentResult.amount())
        : currentMarketValue;
  }

  private static String ytdMoney(BigDecimal amount, BigDecimal annualIncome) {
    return amount == null ? "—" : UiPresentation.compactMoney(amount);
  }

  private static String ytdProgress(BigDecimal amount, BigDecimal annualIncome, int month) {
    if (amount == null) return "";
    BigDecimal progress =
        annualIncome == null || annualIncome.signum() == 0
            ? BigDecimal.ZERO
            : amount.divide(annualIncome, 8, java.math.RoundingMode.HALF_UP);
    return UiPresentation.percentage(progress);
  }

  private static String ytdProgressClass(BigDecimal amount, BigDecimal annualIncome, int month) {
    if (amount == null) return "iv-ytd-progress--unavailable";
    BigDecimal plannedToDate =
        annualIncome == null
            ? BigDecimal.ZERO
            : annualIncome
                .multiply(BigDecimal.valueOf(month))
                .divide(BigDecimal.valueOf(12), 8, java.math.RoundingMode.HALF_UP);
    return amount.compareTo(plannedToDate) >= 0
        ? "iv-ytd-progress--positive"
        : "iv-ytd-progress--warning";
  }

  private static String availableMoney(boolean available, BigDecimal amount) {
    return available && amount != null ? UiPresentation.compactMoney(amount) : "—";
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
      return from(income, income.marketAnnualIncome());
    }

    static IncomeView from(ProfileIncomeSummary income, BigDecimal marketAnnualIncome) {
      BigDecimal combinedAnnualIncome = marketAnnualIncome.add(income.longTermAnnualIncome());
      return new IncomeView(
          UiPresentation.compactMoney(income.marketIncomeYtd()),
          UiPresentation.compactMoney(marketAnnualIncome),
          UiPresentation.percentage(income.marketNetYield()),
          UiPresentation.compactMoney(income.longTermAnnualIncome()),
          UiPresentation.percentage(income.longTermNetYield()),
          UiPresentation.compactMoney(combinedAnnualIncome),
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
      String yieldDisplay,
      String allocationCssClass,
      String legendLabel) {

    static AllocationView from(ProfileAllocation allocation, ProfileIncomeSummary income) {
      String bucketLabel = UiPresentation.bucket(allocation.bucket());
      String percentageDisplay = UiPresentation.percentage(allocation.percentage());
      String yieldDisplay =
          UiPresentation.percentage(
              allocation.assetHorizon() == AssetHorizon.LONG_TERM
                  ? income.longTermNetYield()
                  : income.marketNetYield());
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
          yieldDisplay,
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
