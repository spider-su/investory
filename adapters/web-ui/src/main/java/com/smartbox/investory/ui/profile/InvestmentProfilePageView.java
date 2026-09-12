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
    String expectedAnnualInvestmentResultDisplay,
    String shortTermAssetsPercentageDisplay,
    String longTermAssetsPercentageDisplay,
    String expectedAnnualReturnDisplay,
    String expectedReturnMeta,
    String historicalAnnualizedTwrDisplay,
    String historicalTwrMeta,
    String marketInvestmentResultYtdDisplay,
    String marketYtdReturnDisplay,
    String longTermPlannedIncomeYtdDisplay,
    String longTermYtdProgressDisplay,
    String longTermYtdProgressClass,
    String annualCostDisplay,
    String annualCostMeta,
    IncomeView incomeSummary,
    List<AllocationView> allocations,
    boolean allocationApproximate,
    String allocationReconciliationMessage) {

  static InvestmentProfilePageView from(
      InvestmentProfile profile,
      InvestmentDashboardApi.PerformanceKpiView performance,
      InvestmentDashboardApi.InvestmentResultView investmentResult,
      com.smartbox.investory.retirement.api.model.AnnualCostView annualCost,
      int currentMonth) {
    BigDecimal marketAnnualIncome = profile.incomeSummary().marketAnnualIncome();
    var income = profile.incomeSummary();
    boolean hasInvestmentIncome = income.investmentIncomeAvailable();
    return new InvestmentProfilePageView(
        profile.portfolioId(),
        profile.currency(),
        UiPresentation.compactMoney(profile.totalNetWorth()),
        UiPresentation.percentage(profile.marketPortfolioPercentage()) + " of net worth",
        UiPresentation.compactMoney(profile.marketPortfolioValue()),
        UiPresentation.percentage(profile.longTermAssetPercentage()) + " of net worth",
        UiPresentation.compactMoney(profile.longTermAssetValue()),
        UiPresentation.compactMoney(marketAnnualIncome),
        UiPresentation.percentage(profile.marketPortfolioPercentage()),
        UiPresentation.percentage(profile.longTermAssetPercentage()),
        hasInvestmentIncome
            ? UiPresentation.percentage(income.expectedAnnualReturn())
            : performance.expectedAnnualReturn() != null
                ? UiPresentation.percentage(performance.expectedAnnualReturn())
                : "Unavailable",
        hasInvestmentIncome
            ? "Investment performance"
            : performance.kpiStartDate() == null
                ? "Total return"
                : "Since " + performance.kpiStartDate(),
        performance.historicalAnnualizedReturn() == null
            ? "Unavailable"
            : UiPresentation.percentage(performance.historicalAnnualizedReturn()),
        performance.historyContext() == null ? "Portfolio history" : performance.historyContext(),
        money(
            hasInvestmentIncome
                ? income.investmentResultYtd()
                : investmentResult.available() ? investmentResult.amount() : null),
        hasInvestmentIncome
            ? UiPresentation.percentage(income.investmentExpectationProgress()) + " of expected"
            : performance.ytdReturn() == null
                ? ""
                : UiPresentation.percentage(performance.ytdReturn()),
        money(profile.incomeSummary().plannedLongTermIncomeToDate(currentMonth)),
        ytdProgress(
            profile.incomeSummary().plannedLongTermIncomeToDate(currentMonth),
            profile.incomeSummary().longTermAnnualIncome()),
        ytdProgressClass(
            profile.incomeSummary().plannedLongTermIncomeToDate(currentMonth),
            profile.incomeSummary().longTermAnnualIncome(),
            currentMonth),
        availableMoney(annualCost.available(), annualCost.amount()),
        annualCost.available() ? "planned · " + annualCost.year() : "No retirement plan",
        IncomeView.from(profile.incomeSummary(), marketAnnualIncome),
        profile.allocations().stream()
            .map(AllocationView::from)
            .sorted(Comparator.comparing(AllocationView::percentage).reversed())
            .toList(),
        profile.allocationReconciliation().percentagesApproximate(),
        profile.allocationReconciliation().percentagesApproximate()
            ? "Source totals differ; shares use classified values."
            : "");
  }

  private static String money(BigDecimal amount) {
    return amount == null ? "—" : UiPresentation.compactMoney(amount);
  }

  private static String ytdProgress(BigDecimal amount, BigDecimal annualReference) {
    if (amount == null || annualReference == null || annualReference.signum() == 0) return "";
    return UiPresentation.percentage(
        amount.divide(annualReference, 8, java.math.RoundingMode.HALF_UP));
  }

  private static String ytdProgressClass(
      BigDecimal amount, BigDecimal annualReference, int currentMonth) {
    if (amount == null || annualReference == null || annualReference.signum() == 0) {
      return "iv-ytd-progress--unavailable";
    }
    BigDecimal expectedToDate =
        annualReference
            .multiply(BigDecimal.valueOf(currentMonth))
            .divide(BigDecimal.valueOf(12), 8, java.math.RoundingMode.HALF_UP);
    return amount.compareTo(expectedToDate) >= 0
        ? "iv-ytd-progress--positive"
        : "iv-ytd-progress--warning";
  }

  private static String availableMoney(boolean available, BigDecimal amount) {
    return available && amount != null ? UiPresentation.compactMoney(amount) : "—";
  }

  record IncomeView(
      String incomeBaseCompactDisplay,
      String marketIncomeYtdCompactDisplay,
      String marketAnnualIncomeCompactDisplay,
      String marketNetYieldDisplay,
      String longTermAnnualIncomeCompactDisplay,
      String longTermNetYieldDisplay,
      String combinedAnnualIncomeCompactDisplay,
      String combinedNetYieldDisplay) {

    static IncomeView from(ProfileIncomeSummary income, BigDecimal marketAnnualIncome) {
      BigDecimal combinedAnnualIncome = marketAnnualIncome.add(income.longTermAnnualIncome());
      return new IncomeView(
          income.investmentIncomeBase() == null
              ? "—"
              : UiPresentation.compactMoney(income.investmentIncomeBase()),
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
              ? allocation.liquidity() == com.smartbox.investory.profile.api.model.Liquidity.LIQUID
                  ? "Long-term asset · available"
                  : "Long-term asset · locked"
              : "Short-term asset · liquid",
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
