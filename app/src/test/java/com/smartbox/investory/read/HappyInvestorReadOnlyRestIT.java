package com.smartbox.investory.read;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorDashboardFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorLongTermFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorPlanFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorProfileFacts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

/** Read-only REST contracts for the persisted HappyInvestor snapshot. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "investory.time.fixed-instant=2025-12-31T12:00:00Z")
@AutoConfigureMockMvc
class HappyInvestorReadOnlyRestIT extends FastDatabaseTest {
  private static final String ADMIN = "admin";

  @Autowired private MockMvc mvc;

  @Test
  void profileExposesTheCompleteCanonicalWholeWealthSnapshot() throws Exception {
    mvc.perform(
            get("/api/v1/portfolios/1/profile")
                .with(SecurityMockMvcRequestPostProcessors.user(ADMIN).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.portfolioId").value(1))
        .andExpect(jsonPath("$.currency").value("PLN"))
        .andExpect(
            jsonPath("$.marketPortfolioValue")
                .value(HappyInvestorProfileFacts.MARKET_PORTFOLIO_VALUE.doubleValue()))
        .andExpect(
            jsonPath("$.longTermAssetValue")
                .value(HappyInvestorProfileFacts.LONG_TERM_ASSET_VALUE.doubleValue()))
        .andExpect(
            jsonPath("$.totalNetWorth")
                .value(HappyInvestorProfileFacts.TOTAL_NET_WORTH.doubleValue()))
        .andExpect(
            jsonPath("$.liquidAssets").value(HappyInvestorProfileFacts.LIQUID_ASSETS.doubleValue()))
        .andExpect(
            jsonPath("$.illiquidAssets")
                .value(HappyInvestorProfileFacts.ILLIQUID_ASSETS.doubleValue()))
        .andExpect(jsonPath("$.allocations", org.hamcrest.Matchers.hasSize(5)))
        .andExpect(jsonPath("$.allocations[0].bucket").value("EQUITY"))
        .andExpect(jsonPath("$.allocations[0].liquidity").value("LIQUID"))
        .andExpect(jsonPath("$.allocations[0].assetHorizon").value("SHORT_TERM"))
        .andExpect(
            jsonPath("$.currentRentalIncome")
                .value(HappyInvestorProfileFacts.CURRENT_RENTAL_INCOME.doubleValue()))
        .andExpect(
            jsonPath("$.currentBondIncome")
                .value(HappyInvestorProfileFacts.CURRENT_BOND_INCOME.doubleValue()))
        .andExpect(
            jsonPath("$.retirementReserve")
                .value(HappyInvestorProfileFacts.RETIREMENT_RESERVE.doubleValue()))
        .andExpect(
            jsonPath("$.investmentCapital")
                .value(HappyInvestorProfileFacts.INVESTMENT_CAPITAL.doubleValue()))
        .andExpect(
            jsonPath("$.incomeSummary.marketIncomeYtd")
                .value(HappyInvestorProfileFacts.MARKET_INCOME_YTD.doubleValue()))
        .andExpect(
            jsonPath("$.incomeSummary.marketAnnualIncome")
                .value(HappyInvestorProfileFacts.MARKET_ANNUAL_INCOME.doubleValue()))
        .andExpect(
            jsonPath("$.incomeSummary.marketNetYield")
                .value(HappyInvestorProfileFacts.MARKET_NET_YIELD.doubleValue()))
        .andExpect(
            jsonPath("$.incomeSummary.longTermAnnualIncome")
                .value(HappyInvestorProfileFacts.LONG_TERM_ANNUAL_INCOME.doubleValue()))
        .andExpect(
            jsonPath("$.incomeSummary.longTermNetYield")
                .value(HappyInvestorProfileFacts.LONG_TERM_NET_YIELD.doubleValue()))
        .andExpect(
            jsonPath("$.incomeSummary.combinedAnnualIncome")
                .value(HappyInvestorProfileFacts.COMBINED_ANNUAL_INCOME.doubleValue()))
        .andExpect(
            jsonPath("$.incomeSummary.combinedNetYield")
                .value(HappyInvestorProfileFacts.COMBINED_NET_YIELD.doubleValue()))
        .andExpect(jsonPath("$.longTermPlanningState").exists())
        .andExpect(jsonPath("$.allocationReconciliation.shortTerm").exists())
        .andExpect(jsonPath("$.allocationReconciliation.longTerm").exists())
        .andExpect(
            jsonPath("$.allocationReconciliation.shortTerm.classifiedValue").value(141326.867325))
        .andExpect(jsonPath("$.allocationReconciliation.shortTerm.authoritativeValue").value(0))
        .andExpect(jsonPath("$.allocationReconciliation.longTerm.classifiedValue").value(1020000))
        .andExpect(
            jsonPath("$.allocationReconciliation.longTerm.authoritativeValue").value(1010000));
  }

  @Test
  void longTermPageAndRentalDetailExposeAllCanonicalFields() throws Exception {
    mvc.perform(
            get("/api/v1/portfolios/1/long-term-assets")
                .param("date", "2025-12-31")
                .with(SecurityMockMvcRequestPostProcessors.user(ADMIN).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assets").isArray())
        .andExpect(jsonPath("$.aggregate").exists())
        .andExpect(jsonPath("$.assets", org.hamcrest.Matchers.hasSize(6)))
        .andExpect(jsonPath("$.aggregate.totalCurrentValue").value(1_010_000))
        .andExpect(
            jsonPath("$.aggregate.annualEconomics.grossAnnualIncome")
                .value(HappyInvestorLongTermFacts.AGGREGATE_GROSS_ANNUAL.doubleValue()))
        .andExpect(
            jsonPath("$.aggregate.annualEconomics.annualTax")
                .value(HappyInvestorLongTermFacts.AGGREGATE_TAX_ANNUAL.doubleValue()))
        .andExpect(
            jsonPath("$.aggregate.annualEconomics.netAnnualIncomeAfterTax")
                .value(HappyInvestorLongTermFacts.AGGREGATE_NET_ANNUAL.doubleValue()))
        .andExpect(jsonPath("$.assets[0].annualEconomics.grossAnnualIncome").value(38_400))
        .andExpect(jsonPath("$.assets[0].annualEconomics.annualTax").value(3_264))
        .andExpect(jsonPath("$.assets[0].annualEconomics.netAnnualIncomeAfterTax").value(35_136))
        .andExpect(jsonPath("$.assets[1].id").value(HappyInvestorLongTermFacts.APARTMENT_B_ID))
        .andExpect(jsonPath("$.assets[1].realEstatePlanning.taxBase").value(3_000))
        .andExpect(jsonPath("$.assets[1].annualEconomics.annualTax").value(3_060))
        .andExpect(jsonPath("$.assets[2].id").value(HappyInvestorLongTermFacts.CASH_RESERVE_ID))
        .andExpect(jsonPath("$.assets[2].currentValue").value(50_000))
        .andExpect(jsonPath("$.assets[3].id").value(HappyInvestorLongTermFacts.FAMILY_CAR_ID))
        .andExpect(jsonPath("$.assets[3].annualEconomics.grossAnnualIncome").value(0))
        .andExpect(jsonPath("$.assets[4].id").value(HappyInvestorLongTermFacts.RESERVE_DEPOSIT_ID))
        .andExpect(jsonPath("$.assets[4].maturityDate").value("2027-08-01"))
        .andExpect(jsonPath("$.assets[4].annualEconomics.annualTax").value(380))
        .andExpect(jsonPath("$.assets[4].annualEconomics.netAnnualIncomeAfterTax").value(1_620))
        .andExpect(jsonPath("$.assets[5].id").value(HappyInvestorLongTermFacts.TREASURY_ID))
        .andExpect(jsonPath("$.assets[5].maturityDate").value("2026-02-28"))
        .andExpect(jsonPath("$.assets[5].bondPlanning.interestTreatment").value("PAY_OUT"))
        .andExpect(jsonPath("$.assets[5].bondPlanning.netInterest").value(374.625));

    mvc.perform(
            get("/api/v1/portfolios/1/long-term-assets/9402/details")
                .param("date", "2025-12-31")
                .with(SecurityMockMvcRequestPostProcessors.user(ADMIN).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset.id").value(HappyInvestorLongTermFacts.APARTMENT_A_ID))
        .andExpect(jsonPath("$.asset.name").value("Apartment A"))
        .andExpect(jsonPath("$.asset.currency").value("PLN"))
        .andExpect(jsonPath("$.asset.currentValue").value(400_000))
        .andExpect(jsonPath("$.summary").exists())
        .andExpect(jsonPath("$.bondDetails").doesNotExist())
        .andExpect(jsonPath("$.depositDetails").doesNotExist())
        .andExpect(jsonPath("$.valuationPeriods").isArray())
        .andExpect(jsonPath("$.expectedPropertyGrowth").value(0.025))
        .andExpect(jsonPath("$.contracts[0].id").value(9501))
        .andExpect(jsonPath("$.contracts[0].monthlyTaxBase").value(3_200))
        .andExpect(jsonPath("$.contracts[0].terms[0].amount").value(3_200))
        .andExpect(jsonPath("$.contracts[0].terms[0].frequency").value("MONTHLY"));
  }

  @Test
  void dashboardExposesCompleteReadModelForMaxAndYtd() throws Exception {
    for (String period : new String[] {"MAX", "YTD"}) {
      mvc.perform(
              post("/api/v1/investment/dashboard/query")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"accountIds\":[],\"benchmarkAccountsSubmitted\":false,"
                          + "\"period\":\""
                          + period
                          + "\",\"portfolioId\":1}")
                  .with(SecurityMockMvcRequestPostProcessors.user(ADMIN).roles("ADMIN")))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.overview.balance")
                  .value(HappyInvestorDashboardFacts.BALANCE.doubleValue()))
          .andExpect(jsonPath("$.overview.totalProfit").value(0))
          .andExpect(
              jsonPath("$.overview.netDeposits")
                  .value(HappyInvestorDashboardFacts.NET_DEPOSITS.doubleValue()))
          .andExpect(jsonPath("$.overview.baseCurrency").value("PLN"))
          .andExpect(
              jsonPath("$.overview.exchangeRates.USD")
                  .value(HappyInvestorDashboardFacts.USD_PER_PLN.doubleValue()))
          .andExpect(
              jsonPath("$.overview.exchangeRates.EUR")
                  .value(HappyInvestorDashboardFacts.EUR_PER_PLN.doubleValue()))
          .andExpect(jsonPath("$.overview.accountBalances", org.hamcrest.Matchers.hasSize(3)))
          .andExpect(jsonPath("$.overview.accountBalances[0].accountId").value(17_959_259))
          .andExpect(jsonPath("$.overview.accountBalances[0].netDeposit").value(97_000))
          .andExpect(jsonPath("$.overview.accountBalances[1].accountId").value(51_499_241))
          .andExpect(jsonPath("$.overview.accountBalances[1].netDeposit").value(3_000))
          .andExpect(jsonPath("$.overview.accountBalances[2].accountId").value(51_551_301))
          .andExpect(jsonPath("$.overview.accountBalances[2].netDeposit").value(3_000))
          .andExpect(
              jsonPath("$.overview.accountBalancesTotal.baseNetDeposit")
                  .value(HappyInvestorDashboardFacts.NET_DEPOSITS.doubleValue()))
          .andExpect(
              jsonPath("$.cashFlow.deposits")
                  .value(HappyInvestorDashboardFacts.DEPOSITS.doubleValue()))
          .andExpect(
              jsonPath("$.cashFlow.withdrawals")
                  .value(HappyInvestorDashboardFacts.WITHDRAWALS.doubleValue()))
          .andExpect(jsonPath("$.cashFlow.cash").value(0))
          .andExpect(jsonPath("$.cashFlow.realizedProfit").value(0))
          .andExpect(jsonPath("$.cashFlow.dividends").value(0))
          .andExpect(jsonPath("$.cashFlow.dividendTax").value(0))
          .andExpect(jsonPath("$.cashFlow.interest").value(0))
          .andExpect(jsonPath("$.cashFlow.dividendGainers").isEmpty())
          .andExpect(jsonPath("$.positions.openPositionValues", org.hamcrest.Matchers.hasSize(2)))
          .andExpect(jsonPath("$.positions.openPositionValues[0].symbol").value("AAPL.US"))
          .andExpect(
              jsonPath("$.positions.openPositionValues[0].value")
                  .value(HappyInvestorDashboardFacts.APPLE_VALUE.doubleValue()))
          .andExpect(
              jsonPath("$.positions.openPositionValues[0].unrealized")
                  .value(HappyInvestorDashboardFacts.APPLE_UNREALIZED.doubleValue()))
          .andExpect(jsonPath("$.positions.openPositionValues[1].symbol").value("TSLA.US"))
          .andExpect(
              jsonPath("$.positions.openPositionValues[1].value")
                  .value(HappyInvestorDashboardFacts.TESLA_VALUE.doubleValue()))
          .andExpect(
              jsonPath("$.positions.openPositionValues[1].unrealized")
                  .value(HappyInvestorDashboardFacts.TESLA_UNREALIZED.doubleValue()))
          .andExpect(
              jsonPath("$.positions.openPositionValuesTotal.value")
                  .value(HappyInvestorDashboardFacts.OPEN_POSITIONS_VALUE.doubleValue()))
          .andExpect(
              jsonPath("$.positions.openPositionValuesTotal.unrealized")
                  .value(HappyInvestorDashboardFacts.OPEN_POSITIONS_UNREALIZED.doubleValue()))
          .andExpect(jsonPath("$.performance.summary.portfolioReturnPct").value(0))
          .andExpect(
              jsonPath("$.performance.summary.timeWeightedReturn.status")
                  .value("INSUFFICIENT_DATA"))
          .andExpect(
              jsonPath("$.performance.summary.moneyWeightedReturn.status")
                  .value("INSUFFICIENT_DATA"))
          .andExpect(jsonPath("$.performance.topGainers[0].symbol").value("AAPL.US"))
          .andExpect(jsonPath("$.performance.topGainers[1].symbol").value("TSLA.US"))
          .andExpect(jsonPath("$.performance.topLosers").isEmpty())
          .andExpect(
              jsonPath("$.overview.assetAllocation.totalValue")
                  .value(HappyInvestorDashboardFacts.OPEN_POSITIONS_VALUE.doubleValue()))
          .andExpect(jsonPath("$.overview.assetAllocation.buckets[0].name").value("Equity"))
          .andExpect(jsonPath("$.overview.assetAllocation.buckets[0].weightPct").value(100))
          .andExpect(jsonPath("$.risk.periodLabel").value("Current snapshot"))
          .andExpect(jsonPath("$.risk.warnings[0]").value("Exposure data unavailable"))
          .andExpect(jsonPath("$.dataQuality.state").value("CRITICAL"))
          .andExpect(jsonPath("$.dataQuality.issues").isEmpty())
          .andExpect(jsonPath("$.selectedPeriod").value(period))
          .andExpect(jsonPath("$.periods", org.hamcrest.Matchers.hasSize(8)))
          .andExpect(jsonPath("$.navigation.portfolioId").value(1))
          .andExpect(jsonPath("$.navigation.accountIds").isEmpty());
    }
  }

  @Test
  void investmentAssetDetailExposesEveryNestedReadOnlyField() throws Exception {
    mvc.perform(
            get("/api/v1/investment/assets/TSLA.US")
                .param("portfolioId", "1")
                .param("period", "MAX")
                .with(SecurityMockMvcRequestPostProcessors.user(ADMIN).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1001))
        .andExpect(jsonPath("$.symbol").value("TSLA.US"))
        .andExpect(jsonPath("$.name").exists())
        .andExpect(jsonPath("$.ticker").value("TSLA"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.marketPrice").value(403.840))
        .andExpect(jsonPath("$.marketPriceUsd").value(403.840))
        .andExpect(jsonPath("$.holdings").isArray())
        .andExpect(jsonPath("$.totalQuantity").value(1.0))
        .andExpect(jsonPath("$.totalMarketValue").value(403.840))
        .andExpect(jsonPath("$.totalUnrealizedProfitLoss").value(203.83999999999997))
        .andExpect(jsonPath("$.totalRealizedProfitLoss").value(0.0))
        .andExpect(jsonPath("$.transactions").isArray())
        .andExpect(jsonPath("$.dividends").isArray())
        .andExpect(jsonPath("$.performance").exists());
  }

  @Test
  void persistedPlanDetailsExposeAllCanonicalAssumptionsAndBaseline() throws Exception {
    mvc.perform(
            get("/api/v1/retirement/portfolios/1/plans/" + HappyInvestorPlanFacts.SEED_PLAN_ID)
                .with(SecurityMockMvcRequestPostProcessors.user(ADMIN).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(9201))
        .andExpect(jsonPath("$.name").value(HappyInvestorPlanFacts.NAME))
        .andExpect(jsonPath("$.currentRevisionId").value(9202))
        .andExpect(jsonPath("$.currentRevision.id").value(9202))
        .andExpect(jsonPath("$.currentRevision.revisionNumber").value(1))
        .andExpect(jsonPath("$.assumptions.currentAge").value(40))
        .andExpect(jsonPath("$.assumptions.endAge").value(85))
        .andExpect(jsonPath("$.assumptions.startYear").value(2024))
        .andExpect(jsonPath("$.assumptions.retirementAge").value(60))
        .andExpect(jsonPath("$.assumptions.annualLivingExpenses").value(36_000))
        .andExpect(jsonPath("$.assumptions.annualDiscretionaryExpenses").value(6_000))
        .andExpect(jsonPath("$.assumptions.annualEmploymentIncome").value(90_000))
        .andExpect(jsonPath("$.assumptions.annualPreRetirementContribution").value(12_000))
        .andExpect(jsonPath("$.assumptions.annualPension").value(24_000))
        .andExpect(jsonPath("$.assumptions.pensionStartAge").value(67))
        .andExpect(jsonPath("$.assumptions.inflationRate").value(0.025))
        .andExpect(jsonPath("$.assumptions.fixedIncomeReturnRate").value(0.035))
        .andExpect(jsonPath("$.assumptions.equityReturnRate").value(0.07))
        .andExpect(jsonPath("$.assumptions.rentalIncomeGrowthSpread").value(0.025))
        .andExpect(jsonPath("$.assumptions.spendingGrowthSpread").value(0.035))
        .andExpect(jsonPath("$.assumptions.futureEvents").isArray())
        .andExpect(jsonPath("$.assumptions.fundingOrder").isArray())
        .andExpect(jsonPath("$.assumptions.expenseProfile").isArray())
        .andExpect(jsonPath("$.baseline.asOfYear").value(2025))
        .andExpect(jsonPath("$.baseline.reserve").value(50_000))
        .andExpect(jsonPath("$.baseline.investmentCapital").value(159_307.015664))
        .andExpect(jsonPath("$.baseline.longTermCapital").value(970_000))
        .andExpect(jsonPath("$.baseline.rentalAnnualIncome").value(74_400))
        .andExpect(jsonPath("$.baseline.longTermAnnualIncome").value(74_400));
  }

  @Test
  void persistedPlanProjectionExposesScenarioMetadataAndYears() throws Exception {
    mvc.perform(
            post("/api/v1/retirement/portfolios/1/projections")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":9201,\"defaultCurrentAge\":40,\"defaultEndAge\":85}")
                .with(SecurityMockMvcRequestPostProcessors.user(ADMIN).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.portfolioId").value(1))
        .andExpect(jsonPath("$.planId").value(HappyInvestorPlanFacts.SEED_PLAN_ID))
        .andExpect(jsonPath("$.currency").value("PLN"))
        .andExpect(jsonPath("$.endAge").value(HappyInvestorPlanFacts.END_AGE))
        .andExpect(jsonPath("$.scenarios").isMap())
        .andExpect(jsonPath("$.scenarios.BASE").exists())
        .andExpect(jsonPath("$.scenarios.CONSERVATIVE").exists())
        .andExpect(jsonPath("$.scenarios.OPTIMISTIC").exists())
        .andExpect(jsonPath("$.scenarios.BASE.years").isArray())
        .andExpect(jsonPath("$.scenarios.CONSERVATIVE.years").isArray())
        .andExpect(jsonPath("$.scenarios.OPTIMISTIC.years").isArray())
        .andExpect(jsonPath("$.scenarios.BASE.years[0].year").isNumber())
        .andExpect(jsonPath("$.scenarios.BASE.years[0].age").isNumber());
  }
}
