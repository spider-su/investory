package com.smartbox.investory.application.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.application.longterm.LongTermAssetService;
import com.smartbox.investory.application.longterm.LongTermAssetSummary;
import com.smartbox.investory.infrastructure.longterm.LongTermAssetType;
import com.smartbox.investory.infrastructure.repository.AssetRepository;
import com.smartbox.investory.services.models.OpenPositionValue;
import com.smartbox.investory.services.portfolio.read.BrokeragePortfolioReadService;
import com.smartbox.investory.services.portfolio.read.BrokeragePositionSnapshot;
import com.smartbox.investory.services.portfolio.read.SharedBrokeragePortfolioSnapshot;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
  @Mock BrokeragePortfolioReadService brokeragePortfolioReadService;
  @Mock LongTermAssetService longTermAssets;
  @Mock AssetRepository assetRepository;
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
            assetRepository,
            currencyRates,
            Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void combinesMarketAndManualValuesAndIncome() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.PLN, 2000, 500, 100, 20, List.of(position("KNOWN", 1500)));
    when(brokeragePortfolioReadService.currentSharedSnapshot()).thenReturn(market);
    when(assetRepository.findBySymbol("KNOWN")).thenReturn(Optional.empty());
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetService.AggregateSummary(
                CurrencyType.PLN,
                new BigDecimal("3000"),
                new BigDecimal("400"),
                new BigDecimal("100"),
                new BigDecimal("300"),
                new BigDecimal("40"),
                new BigDecimal("260")));
    when(longTermAssets.list(PORTFOLIO, DATE))
        .thenReturn(List.of(summary(LongTermAssetType.REAL_ESTATE, "3000", "260")));
    InvestmentProfile profile = facade.loadProfile(PORTFOLIO);
    assertEquals(new BigDecimal("5000.0"), profile.totalNetWorth());
    assertEquals(new BigDecimal("2000.0"), profile.liquidAssets());
    assertEquals(new BigDecimal("3000"), profile.illiquidAssets());
    assertEquals(new BigDecimal("380.0"), profile.totalInvestmentIncome());
  }

  @Test
  void unknownMarketSecurityMapsToOther() {
    SharedBrokeragePortfolioSnapshot market =
        snapshot(CurrencyType.PLN, 100, 0, 0, 0, List.of(position("UNKNOWN", 100)));
    when(brokeragePortfolioReadService.currentSharedSnapshot()).thenReturn(market);
    when(assetRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetService.AggregateSummary(
                CurrencyType.PLN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO));
    when(longTermAssets.list(PORTFOLIO, DATE)).thenReturn(List.of());
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
    when(assetRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetService.AggregateSummary(
                CurrencyType.USD,
                new BigDecimal("400"),
                new BigDecimal("40"),
                BigDecimal.ZERO,
                new BigDecimal("40"),
                BigDecimal.ZERO,
                new BigDecimal("40")));
    when(longTermAssets.list(PORTFOLIO, DATE))
        .thenReturn(List.of(summary(LongTermAssetType.REAL_ESTATE, "100", "10", CurrencyType.PLN)));
    when(currencyRates.convertToBaseCurrency(
            new BigDecimal("100"), CurrencyType.USD, CurrencyType.PLN, DATE))
        .thenReturn(new BigDecimal("400"));

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
    verify(currencyRates)
        .convertToBaseCurrency(new BigDecimal("100"), CurrencyType.USD, CurrencyType.PLN, DATE);
  }

  @Test
  void contractualManualBondIsNotReportedAsSpendableLiquidity() {
    SharedBrokeragePortfolioSnapshot market = snapshot(CurrencyType.PLN, 0, 0, 0, 0, List.of());
    when(brokeragePortfolioReadService.currentSharedSnapshot()).thenReturn(market);
    when(longTermAssets.aggregate(PORTFOLIO, DATE))
        .thenReturn(
            new LongTermAssetService.AggregateSummary(
                CurrencyType.PLN,
                new BigDecimal("200000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO));
    when(longTermAssets.list(PORTFOLIO, DATE))
        .thenReturn(List.of(summary(LongTermAssetType.BOND, "200000", "0")));
    when(longTermAssets.projectionInputs(PORTFOLIO, DATE))
        .thenReturn(
            List.of(
                new com.smartbox.investory.application.longterm.LongTermAssetProjectionInput(
                    1L,
                    "Bond",
                    LongTermAssetType.BOND,
                    CurrencyType.PLN,
                    new BigDecimal("200000"),
                    List.of(),
                    java.time.LocalDate.of(2028, 2, 28),
                    new BigDecimal("200000"),
                    com.smartbox.investory.infrastructure.longterm.InterestTreatment.CAPITALIZE,
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
            new LongTermAssetService.AggregateSummary(
                CurrencyType.USD,
                new BigDecimal("400"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO));
    when(longTermAssets.list(PORTFOLIO, DATE))
        .thenReturn(List.of(summary(LongTermAssetType.REAL_ESTATE, "100", "0", CurrencyType.PLN)));
    when(longTermAssets.projectionInputs(PORTFOLIO, DATE))
        .thenReturn(
            List.of(
                new com.smartbox.investory.application.longterm.LongTermAssetProjectionInput(
                    1L,
                    "Property",
                    LongTermAssetType.REAL_ESTATE,
                    CurrencyType.PLN,
                    new BigDecimal("100"),
                    List.of(
                        new com.smartbox.investory.application.longterm.LongTermAssetProjectionInput
                            .Period(
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
    when(currencyRates.convertToBaseCurrency(
            any(BigDecimal.class),
            org.mockito.ArgumentMatchers.eq(CurrencyType.USD),
            org.mockito.ArgumentMatchers.eq(CurrencyType.PLN),
            org.mockito.ArgumentMatchers.eq(DATE)))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0, BigDecimal.class).multiply(new BigDecimal("4")));

    var period = facade.loadProfile(PORTFOLIO).longTermAssets().getFirst().periods().getFirst();

    assertEquals(new BigDecimal("40"), period.annualIncome());
    assertEquals(new BigDecimal("16"), period.annualExpense());
    assertEquals(new BigDecimal("0.01"), period.annualReturnRate());
    verify(currencyRates, never())
        .convertToBaseCurrency(new BigDecimal("0.01"), CurrencyType.USD, CurrencyType.PLN, DATE);
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

  private static LongTermAssetSummary summary(LongTermAssetType type, String value, String income) {
    return summary(type, value, income, CurrencyType.PLN);
  }

  private static LongTermAssetSummary summary(
      LongTermAssetType type, String value, String income, CurrencyType currency) {
    BigDecimal v = new BigDecimal(value);
    BigDecimal i = new BigDecimal(income);
    return new LongTermAssetSummary(
        1L,
        "Asset",
        type,
        currency,
        v,
        i,
        BigDecimal.ZERO,
        i,
        BigDecimal.ZERO,
        i,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        BigDecimal.ZERO);
  }
}
