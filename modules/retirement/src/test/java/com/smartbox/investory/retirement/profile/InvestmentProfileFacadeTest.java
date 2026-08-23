package com.smartbox.investory.retirement.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.accounting.model.OpenPositionValue;
import com.smartbox.investory.investment.api.BrokerageAssetClassificationReader;
import com.smartbox.investory.investment.api.BrokeragePortfolioReader;
import com.smartbox.investory.investment.api.BrokeragePositionSnapshot;
import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.investment.api.SharedBrokeragePortfolioSnapshot;
import com.smartbox.investory.investment.application.InvestmentAnnualProjectionService;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummaryModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.application.service.LongTermAnnualProjectionService;
import com.smartbox.investory.retirement.api.InvestmentProfileFacade;
import com.smartbox.investory.retirement.planning.PlanningBaseline;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentProfileFacadeTest {
  @Mock BrokeragePortfolioReader brokeragePortfolioReadService;
  @Mock LongTermAssetProfileReader longTermAssets;
  @Mock LongTermAssetAnnualSnapshotReader longTermAnnualFacts;
  @Mock BrokerageAssetClassificationReader brokerageAssetClassificationReader;
  @Mock CurrencyConversion currencyRates;
  private InvestmentProfileFacade facade;
  private static final Long PORTFOLIO = 1L;
  private static final java.time.LocalDate DATE = java.time.LocalDate.of(2026, 6, 1);

  @BeforeEach
  void setUp() {
    facade =
        new InvestmentProfileFacade(
            brokeragePortfolioReadService,
            longTermAssets,
            longTermAnnualFacts,
            brokerageAssetClassificationReader,
            currencyRates,
            Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void combinesMarketAndManualValuesAndIncome() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.USD, 2000, 500, 100, 20, List.of(position("KNOWN", 1500)));
    when(brokeragePortfolioReadService.currentSharedSnapshot()).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbol("KNOWN")).thenReturn(Optional.empty());
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetProfileSummaryModel(
                CurrencyType.USD, new BigDecimal("3000"), new BigDecimal("260")));
    when(longTermAssets.list(PORTFOLIO, DATE))
        .thenReturn(List.of(summary(LongTermAssetTypeModel.REAL_ESTATE, "3000", "260")));
    when(longTermAnnualFacts.currentAnnualSnapshot(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetAnnualSnapshotModel(
                null, new BigDecimal("260"), null, BigDecimal.ZERO, null, null));
    InvestmentProfile profile = facade.loadProfile(PORTFOLIO);
    assertEquals(new BigDecimal("5000.0"), profile.totalNetWorth());
    assertEquals(new BigDecimal("2000.0"), profile.liquidAssets());
    assertEquals(new BigDecimal("3000"), profile.illiquidAssets());
    assertEquals(new BigDecimal("380.0"), profile.totalInvestmentIncome());
    assertEquals(new BigDecimal("260"), profile.currentRentalIncome());
  }

  @Test
  void unknownMarketSecurityMapsToOther() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.USD, 100, 0, 0, 0, List.of(position("UNKNOWN", 100)));
    when(brokeragePortfolioReadService.currentSharedSnapshot()).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetProfileSummaryModel(
                CurrencyType.USD, BigDecimal.ZERO, BigDecimal.ZERO));
    when(longTermAssets.list(PORTFOLIO, DATE)).thenReturn(List.of());
    when(longTermAnnualFacts.currentAnnualSnapshot(PORTFOLIO, DATE))
        .thenReturn(new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null));
    ProfileAllocation other =
        facade.loadProfile(PORTFOLIO).allocations().stream()
            .filter(a -> a.bucket() == EconomicBucket.OTHER)
            .findFirst()
            .orElseThrow();
    assertEquals(new BigDecimal("100.0"), other.value());
  }

  @Test
  void convertsForeignManualAssetOnceAndAllocationsReconcile() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.USD, 1000, 200, 0, 0, List.of(position("UNKNOWN", 800)));
    when(brokeragePortfolioReadService.currentSharedSnapshot()).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetProfileSummaryModel(
                CurrencyType.USD, new BigDecimal("400"), new BigDecimal("40")));
    when(longTermAssets.list(PORTFOLIO, DATE))
        .thenReturn(
            List.of(summary(LongTermAssetTypeModel.REAL_ESTATE, "400", "10", CurrencyType.USD)));

    InvestmentProfile profile = facade.loadProfile(PORTFOLIO);

    assertEquals(new BigDecimal("400"), profile.longTermAssetValue());
    assertEquals(new BigDecimal("1400.0"), profile.totalNetWorth());
    assertEquals(new BigDecimal("400"), profile.illiquidAssets());
    assertEquals(new BigDecimal("1000.0"), profile.liquidAssets());
    assertEquals(
        new BigDecimal("1.00000000"),
        profile.allocations().stream()
            .map(ProfileAllocation::percentage)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    verify(currencyRates, never()).convertToBaseCurrency(any(), any(), any(), any());
  }

  @Test
  void contractualManualBondIsNotReportedAsSpendableLiquidity() {
    SharedBrokeragePortfolioSnapshot market = snapshot(CurrencyType.USD, 0, 0, 0, 0, List.of());
    when(brokeragePortfolioReadService.currentSharedSnapshot()).thenReturn(market);
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetProfileSummaryModel(
                CurrencyType.USD, new BigDecimal("200000"), BigDecimal.ZERO));
    when(longTermAssets.list(PORTFOLIO, DATE))
        .thenReturn(List.of(summary(LongTermAssetTypeModel.BOND, "200000", "0")));
    when(longTermAssets.projectionInputs(PORTFOLIO, DATE))
        .thenReturn(
            List.of(
                new LongTermAssetProjectionModel(
                    1L,
                    "Bond",
                    LongTermAssetTypeModel.BOND,
                    CurrencyType.USD,
                    new BigDecimal("200000"),
                    List.of(),
                    java.time.LocalDate.of(2028, 2, 28),
                    new BigDecimal("200000"),
                    com.smartbox.investory.longterm.api.model.InterestTreatmentModel.CAPITALIZE,
                    BigDecimal.ZERO)));

    InvestmentProfile profile = facade.loadProfile(PORTFOLIO);

    assertEquals(0, new BigDecimal("200000").compareTo(profile.totalNetWorth()));
    assertEquals(0, BigDecimal.ZERO.compareTo(profile.liquidAssets()));
    assertEquals(0, new BigDecimal("200000").compareTo(profile.illiquidAssets()));
  }

  @Test
  void convertsMonetaryProjectionFieldsButNeverTheDimensionlessReturnRate() {
    SharedBrokeragePortfolioSnapshot market = snapshot(CurrencyType.USD, 0, 0, 0, 0, List.of());
    when(brokeragePortfolioReadService.currentSharedSnapshot()).thenReturn(market);
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetProfileSummaryModel(
                CurrencyType.USD, new BigDecimal("400"), BigDecimal.ZERO));
    when(longTermAssets.list(PORTFOLIO, DATE))
        .thenReturn(
            List.of(summary(LongTermAssetTypeModel.REAL_ESTATE, "100", "0", CurrencyType.USD)));
    when(longTermAssets.projectionInputs(PORTFOLIO, DATE))
        .thenReturn(
            List.of(
                new LongTermAssetProjectionModel(
                    1L,
                    "Property",
                    LongTermAssetTypeModel.REAL_ESTATE,
                    CurrencyType.USD,
                    new BigDecimal("100"),
                    List.of(
                        new LongTermAssetProjectionModel.Period(
                            DATE,
                            null,
                            new BigDecimal("10"),
                            new BigDecimal("4"),
                            new BigDecimal("0.01"))),
                    null,
                    null,
                    null,
                    BigDecimal.ZERO,
                    new BigDecimal("20"))));
    var period = facade.loadProfile(PORTFOLIO).longTermAssets().getFirst().periods().getFirst();

    assertEquals(new BigDecimal("10"), period.annualIncome());
    assertEquals(new BigDecimal("4"), period.annualExpense());
    assertEquals(new BigDecimal("0.01"), period.annualReturnRate());
    verify(currencyRates, never()).convertToBaseCurrency(any(), any(), any(), any());
  }

  @Test
  void preservesRentalContractsForRetirementProjection() {
    SharedBrokeragePortfolioSnapshot market = snapshot(CurrencyType.USD, 0, 0, 0, 0, List.of());
    RentalContractModel contract = new RentalContractModel(3L, DATE, null, List.of());
    when(brokeragePortfolioReadService.currentSharedSnapshot()).thenReturn(market);
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetProfileSummaryModel(
                CurrencyType.USD, new BigDecimal("400"), BigDecimal.ZERO));
    when(longTermAssets.list(PORTFOLIO, DATE))
        .thenReturn(List.of(summary(LongTermAssetTypeModel.REAL_ESTATE, "400", "0")));
    when(longTermAssets.projectionInputs(PORTFOLIO, DATE))
        .thenReturn(
            List.of(
                new LongTermAssetProjectionModel(
                    1L,
                    "Property",
                    LongTermAssetTypeModel.REAL_ESTATE,
                    CurrencyType.USD,
                    new BigDecimal("400"),
                    List.of(),
                    List.of(contract),
                    null,
                    null,
                    null,
                    new BigDecimal("0.085"),
                    new BigDecimal("1500"),
                    false)));

    ProjectedLongTermAsset projected = facade.loadProfile(PORTFOLIO).longTermAssets().getFirst();

    assertEquals(List.of(contract), projected.rentalContracts());
  }

  @Test
  void separatesBrokerageCashReserveFromInvestmentPositions() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.USD, 600000, 100000, 0, 0, List.of(position("ETF", 500000)));
    when(brokeragePortfolioReadService.currentSharedSnapshot()).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbol("ETF"))
        .thenReturn(
            Optional.of(
                new com.smartbox.investory.investment.api.BrokerageAssetClassification(
                    "ETF", "ETF")));
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetProfileSummaryModel(
                CurrencyType.USD, new BigDecimal("3986000"), new BigDecimal("38880")));
    when(longTermAssets.list(PORTFOLIO, DATE))
        .thenReturn(
            List.of(
                summary(LongTermAssetTypeModel.REAL_ESTATE, "3500000", "0"),
                summary(LongTermAssetTypeModel.BOND, "486000", "38880")));
    when(longTermAnnualFacts.currentAnnualSnapshot(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetAnnualSnapshotModel(
                null,
                BigDecimal.ZERO,
                new BigDecimal("486000"),
                new BigDecimal("38880"),
                null,
                null));
    when(longTermAssets.projectionInputs(PORTFOLIO, DATE))
        .thenReturn(
            List.of(
                new LongTermAssetProjectionModel(
                    9L,
                    "Bond",
                    LongTermAssetTypeModel.BOND,
                    CurrencyType.USD,
                    new BigDecimal("486000"),
                    List.of(
                        new LongTermAssetProjectionModel.Period(
                            DATE, null, new BigDecimal("38880"), BigDecimal.ZERO, BigDecimal.ZERO)),
                    LocalDate.of(2028, 12, 31),
                    new BigDecimal("486000"),
                    com.smartbox.investory.longterm.api.model.InterestTreatmentModel.PAY_OUT,
                    BigDecimal.ZERO)));

    InvestmentProfile profile = facade.loadProfile(PORTFOLIO);
    PlanningBaseline baseline = PlanningBaseline.fromProfile(profile, 2026);

    assertEquals(new BigDecimal("100000.0"), profile.retirementReserve());
    assertEquals(new BigDecimal("500000.0"), profile.investmentCapital());
    assertEquals(new BigDecimal("100000.0"), baseline.reserve());
    assertEquals(new BigDecimal("500000.0"), baseline.investmentCapital());
    assertEquals(new BigDecimal("38880"), profile.currentBondIncome());
    assertEquals(
        new BigDecimal("486000"),
        baseline.longTermPlanningState().assets().getFirst().currentValue());

    var investment =
        new InvestmentAnnualProjectionService()
            .project(
                new InvestmentAnnualProjectionApi.ProjectionRequest(
                    2027,
                    profile.investmentCapital(),
                    BigDecimal.ZERO,
                    new BigDecimal("0.085"),
                    BigDecimal.ZERO,
                    InvestmentAnnualProjectionApi.Source.PROJECTED));
    org.assertj.core.api.Assertions.assertThat(investment.annualReturnAmount())
        .isEqualByComparingTo("42500");
    var longTerm =
        new LongTermAnnualProjectionService()
            .plan(
                new com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi.PlanningRequest(
                    2027, BigDecimal.ZERO, profile.longTermPlanningState()));
    assertEquals(
        new BigDecimal("38880"),
        longTerm.plannedCashFlows().stream()
            .filter(
                flow ->
                    flow.kind()
                        == com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi
                            .CashFlowKind.FIXED_INCOME)
            .map(
                com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi.PlannedCashFlow
                    ::annualAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  @Test
  void carriesAllActiveBondPeriodsIntoTheFrozenPlanningState() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.USD, 600000, 100000, 0, 0, List.of(position("ETF", 500000)));
    when(brokeragePortfolioReadService.currentSharedSnapshot()).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbol("ETF"))
        .thenReturn(
            Optional.of(
                new com.smartbox.investory.investment.api.BrokerageAssetClassification(
                    "ETF", "ETF")));
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetProfileSummaryModel(
                CurrencyType.USD, new BigDecimal("900000"), new BigDecimal("48000")));
    when(longTermAssets.list(PORTFOLIO, DATE))
        .thenReturn(
            java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> summary(LongTermAssetTypeModel.BOND, "150000", "8000"))
                .toList());
    when(longTermAnnualFacts.currentAnnualSnapshot(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetAnnualSnapshotModel(
                null,
                BigDecimal.ZERO,
                new BigDecimal("900000"),
                new BigDecimal("48000"),
                null,
                null));
    when(longTermAssets.projectionInputs(PORTFOLIO, DATE))
        .thenReturn(
            java.util.stream.IntStream.range(0, 6)
                .mapToObj(
                    i ->
                        new LongTermAssetProjectionModel(
                            i + 1L,
                            "Bond " + i,
                            LongTermAssetTypeModel.BOND,
                            CurrencyType.USD,
                            new BigDecimal("150000"),
                            List.of(
                                new LongTermAssetProjectionModel.Period(
                                    DATE,
                                    null,
                                    new BigDecimal("8000"),
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO)),
                            LocalDate.of(2028, 12, 31),
                            new BigDecimal("150000"),
                            com.smartbox.investory.longterm.api.model.InterestTreatmentModel
                                .PAY_OUT,
                            BigDecimal.ZERO))
                .toList());

    InvestmentProfile profile = facade.loadProfile(PORTFOLIO);
    var quote =
        new LongTermAnnualProjectionService()
            .quote(
                new com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi.PlanningRequest(
                    2027, BigDecimal.ZERO, profile.longTermPlanningState()));

    assertEquals(6, profile.longTermPlanningState().assets().size());
    assertEquals(
        new BigDecimal("48000"),
        quote.plannedCashFlows().stream()
            .filter(
                flow ->
                    flow.kind()
                        == com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi
                            .CashFlowKind.FIXED_INCOME)
            .map(
                com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi.PlannedCashFlow
                    ::annualAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  private static OpenPositionValue position(String symbol, double value) {
    return new OpenPositionValue(symbol, 1, value, value, value, value, 0d, CurrencyType.PLN, 100);
  }

  private static SharedBrokeragePortfolioSnapshot snapshot(
      CurrencyType currency,
      double balance,
      double cash,
      double dividends,
      double interest,
      List<OpenPositionValue> positions) {
    return new SharedBrokeragePortfolioSnapshot(
        currency,
        java.math.BigDecimal.valueOf(balance),
        java.math.BigDecimal.valueOf(cash),
        java.math.BigDecimal.valueOf(dividends),
        java.math.BigDecimal.valueOf(interest),
        positions.stream()
            .map(
                position ->
                    new BrokeragePositionSnapshot(
                        position.getSymbol(), java.math.BigDecimal.valueOf(position.getValue())))
            .toList());
  }

  private static LongTermAssetProfileAssetModel summary(
      LongTermAssetTypeModel type, String value, String income) {
    return summary(type, value, income, CurrencyType.USD);
  }

  private static LongTermAssetProfileAssetModel summary(
      LongTermAssetTypeModel type, String value, String income, CurrencyType currency) {
    BigDecimal v = new BigDecimal(value);
    return new LongTermAssetProfileAssetModel(type, currency, v);
  }
}
