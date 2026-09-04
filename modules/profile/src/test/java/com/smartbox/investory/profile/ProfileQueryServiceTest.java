package com.smartbox.investory.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassificationReader;
import com.smartbox.investory.investment.api.portfolio.BrokerageAssetType;
import com.smartbox.investory.investment.api.portfolio.BrokerageIncomeSnapshot;
import com.smartbox.investory.investment.api.portfolio.BrokeragePortfolioReader;
import com.smartbox.investory.investment.api.portfolio.BrokeragePositionSnapshot;
import com.smartbox.investory.investment.api.portfolio.SharedBrokeragePortfolioSnapshot;
import com.smartbox.investory.investment.api.reporting.InvestmentAnnualProjectionApi;
import com.smartbox.investory.investment.api.reporting.model.OpenPositionValue;
import com.smartbox.investory.investment.projection.InvestmentAnnualProjectionService;
import com.smartbox.investory.longterm.api.LongTermAssetProfileSummaryReader;
import com.smartbox.investory.longterm.api.LongTermAssetProjectionReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummaryModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummarySnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RentalContractProjectionModel;
import com.smartbox.investory.profile.api.ProfileComposition;
import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.smartbox.investory.profile.application.ProfileQueryService;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Profile Query Service")
class ProfileQueryServiceTest {
  @Mock BrokeragePortfolioReader brokeragePortfolioReadService;
  @Mock LongTermAssetProfileSummaryReader longTermSummaryReader;
  @Mock LongTermAssetProjectionReader longTermProjectionReader;
  @Mock BrokerageAssetClassificationReader brokerageAssetClassificationReader;
  @Mock CurrencyConversion currencyRates;
  private ProfileQueryService facade;
  private LongTermAssetProfileSummaryModel longTermSummary;
  private List<LongTermAssetProfileAssetModel> longTermAssetRows;
  private List<LongTermAssetProjectionModel> longTermProjectionInputs;
  private LongTermAssetAnnualSnapshotModel longTermAnnualSnapshot;
  private static final Long PORTFOLIO = 1L;
  private static final java.time.LocalDate DATE = java.time.LocalDate.of(2026, 6, 1);

  @BeforeEach
  void setUp() {
    longTermSummary =
        new LongTermAssetProfileSummaryModel(CurrencyType.USD, BigDecimal.ZERO, BigDecimal.ZERO);
    longTermAssetRows = List.of();
    longTermProjectionInputs = List.of();
    longTermAnnualSnapshot =
        new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null);
    lenient()
        .when(longTermSummaryReader.summary(PORTFOLIO, DATE))
        .thenAnswer(
            ignored ->
                new LongTermAssetProfileSummarySnapshotModel(
                    longTermSummary, longTermAssetRows, longTermAnnualSnapshot));
    lenient()
        .when(longTermProjectionReader.projectionInputs(PORTFOLIO, DATE))
        .thenAnswer(ignored -> longTermProjectionInputs);
    facade =
        new ProfileQueryService(
            brokeragePortfolioReadService,
            longTermSummaryReader,
            longTermProjectionReader,
            brokerageAssetClassificationReader,
            currencyRates,
            Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @DisplayName("combines Market And Manual Values And Income")
  @Test
  void combinesMarketAndManualValuesAndIncome() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.USD, 2000, 500, 100, 20, List.of(position("KNOWN", 1500)));
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbols(any())).thenReturn(Map.of());
    longTermSummary =
        new LongTermAssetProfileSummaryModel(
            CurrencyType.USD, new BigDecimal("3000"), new BigDecimal("260"));
    longTermAssetRows = List.of(summary(LongTermAssetType.REAL_ESTATE, "3000", "260"));
    longTermAnnualSnapshot =
        new LongTermAssetAnnualSnapshotModel(
            null, new BigDecimal("260"), null, BigDecimal.ZERO, null, null);
    InvestmentProfile profile = ProfileComposition.load(facade, facade, PORTFOLIO);
    assertEquals(new BigDecimal("5000.0"), profile.totalNetWorth());
    assertEquals(new BigDecimal("2000.0"), profile.liquidAssets());
    assertEquals(new BigDecimal("3000"), profile.illiquidAssets());
    assertEquals(new BigDecimal("548.15789474"), profile.incomeSummary().combinedAnnualIncome());
    assertEquals(new BigDecimal("260"), profile.currentRentalIncome());
    verify(longTermSummaryReader, org.mockito.Mockito.atLeastOnce()).summary(PORTFOLIO, DATE);
  }

  @DisplayName("keeps Classified Values And Reports The Exact Source Delta")
  @Test
  void keepsClassifiedValuesAndReportsTheExactSourceDelta() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.USD, 50, 0, 0, 0, List.of(position("ETF", 100)));
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbols(any()))
        .thenReturn(
            Map.of(
                "ETF",
                new com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassification(
                    "ETF", BrokerageAssetType.ETF)));
    longTermSummary =
        new LongTermAssetProfileSummaryModel(CurrencyType.USD, BigDecimal.ZERO, BigDecimal.ZERO);
    longTermAssetRows = List.of();

    InvestmentProfile profile = ProfileComposition.load(facade, facade, PORTFOLIO);

    assertEquals(
        new BigDecimal("100.0"),
        profile.allocations().stream()
            .filter(allocation -> allocation.bucket() == EconomicBucket.EQUITY)
            .findFirst()
            .orElseThrow()
            .value());
    assertEquals(
        0,
        profile.allocations().stream()
            .filter(allocation -> allocation.bucket() == EconomicBucket.OTHER)
            .count());
    assertEquals(new BigDecimal("-50.0"), profile.allocationReconciliation().shortTerm().delta());
    org.assertj.core.api.Assertions.assertThat(profile.allocationReconciliation().balanced())
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(
            profile.allocationReconciliation().percentagesApproximate())
        .isTrue();
    assertEquals(
        new BigDecimal("1.00000000"),
        profile.allocations().getFirst().percentage(),
        "mismatched source totals use classified value for percentages");
  }

  @DisplayName("summary Does Not Read Projection Inputs")
  @Test
  void summaryDoesNotReadProjectionInputs() {
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO))
        .thenReturn(snapshot(CurrencyType.USD, 0, 0, 0, 0, List.of()));

    facade.loadSummary(PORTFOLIO);

    verifyNoInteractions(longTermProjectionReader);
  }

  @DisplayName("planning Does Not Read Summary Facts")
  @Test
  void planningDoesNotReadSummaryFacts() {
    facade.loadPlanning(PORTFOLIO);

    verifyNoInteractions(longTermSummaryReader, brokeragePortfolioReadService);
  }

  @DisplayName("snapshot Composition Reads Summary And Planning Once")
  @Test
  void snapshotCompositionReadsSummaryAndPlanningOnce() {
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO))
        .thenReturn(snapshot(CurrencyType.USD, 0, 0, 0, 0, List.of()));

    facade.loadProfile(PORTFOLIO);

    verify(longTermSummaryReader).summary(PORTFOLIO, DATE);
    verify(longTermProjectionReader).projectionInputs(PORTFOLIO, DATE);
  }

  @DisplayName("compares Calendar Ytd Market Income With Expected Long Term Income")
  @Test
  void comparesCalendarYtdMarketIncomeWithExpectedLongTermIncome() {
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO))
        .thenReturn(
            snapshot(CurrencyType.USD, 2000, 500, 999, 999, List.of(position("KNOWN", 1500))));
    when(brokeragePortfolioReadService.incomeForMonths(any(), any(), any()))
        .thenReturn(
            new BrokerageIncomeSnapshot(
                CurrencyType.USD,
                LocalDate.of(2026, 1, 1),
                DATE,
                new BigDecimal("1800"),
                new BigDecimal("2200"),
                new BigDecimal("100"),
                new BigDecimal("20"),
                new BigDecimal("10")));
    when(brokerageAssetClassificationReader.findBySymbols(any())).thenReturn(Map.of());
    longTermSummary =
        new LongTermAssetProfileSummaryModel(
            CurrencyType.USD, new BigDecimal("3000"), new BigDecimal("260"));
    longTermAssetRows = List.of(summary(LongTermAssetType.REAL_ESTATE, "3000", "260"));

    ProfileIncomeSummary income =
        ProfileComposition.load(facade, facade, PORTFOLIO).incomeSummary();

    assertEquals(new BigDecimal("110"), income.marketIncomeYtd());
    assertEquals(new BigDecimal("264.14473684"), income.marketAnnualIncome());
    assertEquals(new BigDecimal("0.13207237"), income.marketNetYield());
    assertEquals(new BigDecimal("524.14473684"), income.combinedAnnualIncome());
    assertEquals(new BigDecimal("0.10482895"), income.combinedNetYield());
  }

  @DisplayName("unknown Market Security Maps To Other")
  @Test
  void unknownMarketSecurityMapsToOther() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.USD, 100, 0, 0, 0, List.of(position("UNKNOWN", 100)));
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbols(any())).thenReturn(Map.of());
    longTermSummary =
        new LongTermAssetProfileSummaryModel(CurrencyType.USD, BigDecimal.ZERO, BigDecimal.ZERO);
    longTermAssetRows = List.of();
    longTermAnnualSnapshot =
        new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null);
    ProfileAllocation other =
        ProfileComposition.load(facade, facade, PORTFOLIO).allocations().stream()
            .filter(a -> a.bucket() == EconomicBucket.OTHER)
            .findFirst()
            .orElseThrow();
    assertEquals(new BigDecimal("100.0"), other.value());
  }

  @DisplayName("converts Foreign Manual Asset Once And Allocations Reconcile")
  @Test
  void convertsForeignManualAssetOnceAndAllocationsReconcile() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.USD, 1000, 200, 0, 0, List.of(position("UNKNOWN", 800)));
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbols(any())).thenReturn(Map.of());
    longTermSummary =
        new LongTermAssetProfileSummaryModel(
            CurrencyType.USD, new BigDecimal("400"), new BigDecimal("40"));
    longTermAssetRows =
        List.of(summary(LongTermAssetType.REAL_ESTATE, "400", "10", CurrencyType.USD));

    InvestmentProfile profile = ProfileComposition.load(facade, facade, PORTFOLIO);

    assertEquals(new BigDecimal("400"), profile.longTermAssetValue());
    assertEquals(new BigDecimal("1400.0"), profile.totalNetWorth());
    assertEquals(new BigDecimal("400"), profile.illiquidAssets());
    assertEquals(new BigDecimal("1000.0"), profile.liquidAssets());
    assertEquals(
        new BigDecimal("1.00000000"),
        profile.allocations().stream()
            .map(ProfileAllocation::percentage)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    assertEquals(
        0,
        profile.allocations().stream()
            .filter(allocation -> allocation.assetHorizon() == AssetHorizon.SHORT_TERM)
            .map(ProfileAllocation::value)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .compareTo(profile.marketPortfolioValue()));
    assertEquals(
        0,
        profile.allocations().stream()
            .filter(allocation -> allocation.assetHorizon() == AssetHorizon.LONG_TERM)
            .map(ProfileAllocation::value)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .compareTo(profile.longTermAssetValue()));
    verify(currencyRates, never()).convertToBaseCurrency(any(), any(), any(), any());
  }

  @DisplayName("contractual Manual Bond Is Not Reported As Spendable Liquidity")
  @Test
  void contractualManualBondIsNotReportedAsSpendableLiquidity() {
    SharedBrokeragePortfolioSnapshot market = snapshot(CurrencyType.USD, 0, 0, 0, 0, List.of());
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    longTermSummary =
        new LongTermAssetProfileSummaryModel(
            CurrencyType.USD, new BigDecimal("200000"), BigDecimal.ZERO);
    longTermAssetRows = List.of(summary(LongTermAssetType.BOND, "200000", "0"));
    longTermProjectionInputs =
        List.of(
            new LongTermAssetProjectionModel(
                1L,
                "Bond",
                LongTermAssetType.BOND,
                CurrencyType.USD,
                new BigDecimal("200000"),
                List.of(),
                List.of(),
                java.time.LocalDate.of(2028, 2, 28),
                new BigDecimal("200000"),
                com.smartbox.investory.longterm.api.model.InterestTreatment.CAPITALIZE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false));

    InvestmentProfile profile = ProfileComposition.load(facade, facade, PORTFOLIO);

    assertEquals(0, new BigDecimal("200000").compareTo(profile.totalNetWorth()));
    assertEquals(0, BigDecimal.ZERO.compareTo(profile.liquidAssets()));
    assertEquals(0, new BigDecimal("200000").compareTo(profile.illiquidAssets()));
  }

  @DisplayName("split profile composition preserves contractual bond liquidity")
  @Test
  void splitProfileCompositionPreservesContractualBondLiquidity() {
    SharedBrokeragePortfolioSnapshot market = snapshot(CurrencyType.USD, 0, 0, 0, 0, List.of());
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbols(any())).thenReturn(Map.of());
    longTermSummary =
        new LongTermAssetProfileSummaryModel(
            CurrencyType.USD, new BigDecimal("200000"), BigDecimal.ZERO);
    longTermAssetRows = List.of(summary(LongTermAssetType.BOND, "200000", "0"));
    longTermAnnualSnapshot =
        new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null);
    longTermProjectionInputs =
        List.of(
            new LongTermAssetProjectionModel(
                1L,
                "Bond",
                LongTermAssetType.BOND,
                CurrencyType.USD,
                new BigDecimal("200000"),
                List.of(),
                List.of(),
                LocalDate.of(2028, 2, 28),
                new BigDecimal("200000"),
                com.smartbox.investory.longterm.api.model.InterestTreatment.CAPITALIZE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false));
    InvestmentProfile composed = ProfileComposition.load(facade, facade, PORTFOLIO);

    assertEquals(0, BigDecimal.ZERO.compareTo(composed.liquidAssets()));
    assertEquals(0, new BigDecimal("200000").compareTo(composed.illiquidAssets()));
  }

  @DisplayName("converts Monetary Projection Fields But Never The Dimensionless Return Rate")
  @Test
  void convertsMonetaryProjectionFieldsButNeverTheDimensionlessReturnRate() {
    SharedBrokeragePortfolioSnapshot market = snapshot(CurrencyType.USD, 0, 0, 0, 0, List.of());
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    longTermSummary =
        new LongTermAssetProfileSummaryModel(
            CurrencyType.USD, new BigDecimal("400"), BigDecimal.ZERO);
    longTermAssetRows =
        List.of(summary(LongTermAssetType.REAL_ESTATE, "100", "0", CurrencyType.USD));
    longTermProjectionInputs =
        List.of(
            new LongTermAssetProjectionModel(
                1L,
                "Property",
                LongTermAssetType.REAL_ESTATE,
                CurrencyType.USD,
                new BigDecimal("100"),
                List.of(
                    new LongTermAssetProjectionModel.Period(
                        DATE,
                        null,
                        new BigDecimal("10"),
                        new BigDecimal("4"),
                        new BigDecimal("0.01"),
                        null,
                        false)),
                List.of(),
                null,
                null,
                null,
                BigDecimal.ZERO,
                new BigDecimal("20"),
                false));
    var period =
        ProfileComposition.load(facade, facade, PORTFOLIO)
            .longTermPlanningState()
            .assets()
            .getFirst()
            .periods()
            .getFirst();

    assertEquals(new BigDecimal("10"), period.annualIncome());
    assertEquals(new BigDecimal("4"), period.annualExpense());
    assertEquals(new BigDecimal("0.01"), period.annualReturnRate());
    verify(currencyRates, never()).convertToBaseCurrency(any(), any(), any(), any());
  }

  @DisplayName("preserves Rental Contracts For Retirement Projection")
  @Test
  void preservesRentalContractsForRetirementProjection() {
    SharedBrokeragePortfolioSnapshot market = snapshot(CurrencyType.USD, 0, 0, 0, 0, List.of());
    RentalContractProjectionModel contract =
        new RentalContractProjectionModel(3L, DATE, null, null, null, null, List.of());
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    longTermSummary =
        new LongTermAssetProfileSummaryModel(
            CurrencyType.USD, new BigDecimal("400"), BigDecimal.ZERO);
    longTermAssetRows = List.of(summary(LongTermAssetType.REAL_ESTATE, "400", "0"));
    longTermProjectionInputs =
        List.of(
            new LongTermAssetProjectionModel(
                1L,
                "Property",
                LongTermAssetType.REAL_ESTATE,
                CurrencyType.USD,
                new BigDecimal("400"),
                List.of(),
                List.of(contract),
                null,
                null,
                null,
                new BigDecimal("0.085"),
                new BigDecimal("1500"),
                false));

    ProjectedLongTermAsset projected =
        ProfileComposition.load(facade, facade, PORTFOLIO)
            .longTermPlanningState()
            .assets()
            .getFirst();

    assertEquals(1, projected.rentalContracts().size());
    assertEquals(contract.id(), projected.rentalContracts().getFirst().id());
    assertEquals(contract.startDate(), projected.rentalContracts().getFirst().startDate());
  }

  @DisplayName("separates Brokerage Cash Reserve From Investment Positions")
  @Test
  void separatesBrokerageCashReserveFromInvestmentPositions() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.USD, 600000, 100000, 0, 0, List.of(position("ETF", 500000)));
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbols(any()))
        .thenReturn(
            Map.of(
                "ETF",
                new com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassification(
                    "ETF", BrokerageAssetType.ETF)));
    longTermSummary =
        new LongTermAssetProfileSummaryModel(
            CurrencyType.USD, new BigDecimal("3986000"), new BigDecimal("38880"));
    longTermAssetRows =
        List.of(
            summary(LongTermAssetType.REAL_ESTATE, "3500000", "0"),
            summary(LongTermAssetType.BOND, "486000", "38880"));
    longTermAnnualSnapshot =
        new LongTermAssetAnnualSnapshotModel(
            null, BigDecimal.ZERO, new BigDecimal("486000"), new BigDecimal("38880"), null, null);
    longTermProjectionInputs =
        List.of(
            new LongTermAssetProjectionModel(
                9L,
                "Bond",
                LongTermAssetType.BOND,
                CurrencyType.USD,
                new BigDecimal("486000"),
                List.of(
                    new LongTermAssetProjectionModel.Period(
                        DATE,
                        null,
                        new BigDecimal("38880"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null,
                        false)),
                List.of(),
                LocalDate.of(2028, 12, 31),
                new BigDecimal("486000"),
                com.smartbox.investory.longterm.api.model.InterestTreatment.PAY_OUT,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false));

    InvestmentProfile profile = ProfileComposition.load(facade, facade, PORTFOLIO);
    assertEquals(new BigDecimal("100000.0"), profile.retirementReserve());
    assertEquals(new BigDecimal("500000.0"), profile.investmentCapital());
    assertEquals(new BigDecimal("38880"), profile.currentBondIncome());
    assertEquals(
        new BigDecimal("486000"),
        profile.longTermPlanningState().assets().getFirst().currentValue());

    var investment =
        new InvestmentAnnualProjectionService()
            .project(
                new InvestmentAnnualProjectionApi.ProjectionRequest(
                    2027,
                    profile.investmentCapital(),
                    BigDecimal.ZERO,
                    new BigDecimal("0.085"),
                    BigDecimal.ZERO,
                    com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED));
    org.assertj.core.api.Assertions.assertThat(investment.annualReturnAmount())
        .isEqualByComparingTo("42500");
    assertEquals(
        new BigDecimal("38880"),
        profile.longTermPlanningState().assets().stream()
            .flatMap(asset -> asset.periods().stream())
            .map(ProjectedLongTermAsset.Period::annualIncome)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  @DisplayName("carries All Active Bond Periods Into The Frozen Planning State")
  @Test
  void carriesAllActiveBondPeriodsIntoTheFrozenPlanningState() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.USD, 600000, 100000, 0, 0, List.of(position("ETF", 500000)));
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbols(any()))
        .thenReturn(
            Map.of(
                "ETF",
                new com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassification(
                    "ETF", BrokerageAssetType.ETF)));
    longTermSummary =
        new LongTermAssetProfileSummaryModel(
            CurrencyType.USD, new BigDecimal("900000"), new BigDecimal("48000"));
    longTermAssetRows =
        java.util.stream.IntStream.range(0, 6)
            .mapToObj(i -> summary(LongTermAssetType.BOND, "150000", "8000"))
            .toList();
    longTermAnnualSnapshot =
        new LongTermAssetAnnualSnapshotModel(
            null, BigDecimal.ZERO, new BigDecimal("900000"), new BigDecimal("48000"), null, null);
    longTermProjectionInputs =
        java.util.stream.IntStream.range(0, 6)
            .mapToObj(
                i ->
                    new LongTermAssetProjectionModel(
                        i + 1L,
                        "Bond " + i,
                        LongTermAssetType.BOND,
                        CurrencyType.USD,
                        new BigDecimal("150000"),
                        List.of(
                            new LongTermAssetProjectionModel.Period(
                                DATE,
                                null,
                                new BigDecimal("8000"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                null,
                                false)),
                        List.of(),
                        LocalDate.of(2028, 12, 31),
                        new BigDecimal("150000"),
                        com.smartbox.investory.longterm.api.model.InterestTreatment.PAY_OUT,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        false))
            .toList();

    InvestmentProfile profile = ProfileComposition.load(facade, facade, PORTFOLIO);
    assertEquals(6, profile.longTermPlanningState().assets().size());
    assertEquals(
        new BigDecimal("48000"),
        profile.longTermPlanningState().assets().stream()
            .flatMap(asset -> asset.periods().stream())
            .map(ProjectedLongTermAsset.Period::annualIncome)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  @DisplayName("normalizes non-USD long-term facts into the USD profile denomination")
  @Test
  void normalizesNonUsdLongTermFactsIntoUsdProfileDenomination() {
    SharedBrokeragePortfolioSnapshot market = snapshot(CurrencyType.USD, 0, 0, 0, 0, List.of());
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbols(any())).thenReturn(Map.of());
    when(currencyRates.convertToBaseCurrency(
            any(), eq(CurrencyType.USD), eq(CurrencyType.EUR), eq(DATE)))
        .thenAnswer(
            invocation ->
                ((BigDecimal) invocation.getArgument(0)).multiply(new BigDecimal("1.10")));
    longTermSummary =
        new LongTermAssetProfileSummaryModel(
            CurrencyType.EUR, new BigDecimal("1000"), new BigDecimal("40"));
    longTermAssetRows =
        List.of(summary(LongTermAssetType.REAL_ESTATE, "1000", "40", CurrencyType.EUR));

    InvestmentProfile profile = ProfileComposition.load(facade, facade, PORTFOLIO);

    assertEquals(0, new BigDecimal("1100.0").compareTo(profile.longTermAssetValue()));
    assertEquals(0, new BigDecimal("1100.0").compareTo(profile.totalNetWorth()));
    assertEquals(
        0, new BigDecimal("44.0").compareTo(profile.incomeSummary().longTermAnnualIncome()));
    verify(currencyRates, org.mockito.Mockito.atLeastOnce())
        .convertToBaseCurrency(any(), eq(CurrencyType.USD), eq(CurrencyType.EUR), eq(DATE));
  }

  @DisplayName("denominates the profile in the portfolio base currency from the market snapshot")
  @Test
  void denominatesProfileInPortfolioBaseCurrency() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.PLN, 1000, 200, 0, 0, List.of());
    when(brokeragePortfolioReadService.currentSnapshot(PORTFOLIO)).thenReturn(market);
    when(brokerageAssetClassificationReader.findBySymbols(any())).thenReturn(Map.of());
    longTermSummary =
        new LongTermAssetProfileSummaryModel(
            CurrencyType.PLN, new BigDecimal("400"), new BigDecimal("40"));
    longTermAssetRows =
        List.of(summary(LongTermAssetType.REAL_ESTATE, "400", "40", CurrencyType.PLN));

    InvestmentProfile profile = ProfileComposition.load(facade, facade, PORTFOLIO);

    assertEquals(CurrencyType.PLN, profile.currency());
    assertEquals(0, new BigDecimal("1400.0").compareTo(profile.totalNetWorth()));
    verify(currencyRates, never()).convertToBaseCurrency(any(), any(), any(), any());
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
                    new BrokeragePositionSnapshot(position.getSymbol(), position.getValue()))
            .toList());
  }

  private static LongTermAssetProfileAssetModel summary(
      LongTermAssetType type, String value, String income) {
    return summary(type, value, income, CurrencyType.USD);
  }

  private static LongTermAssetProfileAssetModel summary(
      LongTermAssetType type, String value, String income, CurrencyType currency) {
    BigDecimal v = new BigDecimal(value);
    return new LongTermAssetProfileAssetModel(type, currency, v);
  }
}
