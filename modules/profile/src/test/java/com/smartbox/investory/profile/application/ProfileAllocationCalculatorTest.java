package com.smartbox.investory.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.api.portfolio.*;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.profile.api.model.*;
import com.smartbox.investory.shared.assets.AssetEconomicCategory;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileAllocationCalculatorTest {
  @Test
  void classifiesBrokeragePositionsAndReportsAuthoritativeDelta() {
    var classifications = mock(BrokerageAssetClassificationReader.class);
    when(classifications.findBySymbols(any()))
        .thenReturn(Map.of("ETF", new BrokerageAssetClassification("ETF", BrokerageAssetType.ETF)));
    var calculator = new ProfileAllocationCalculator(classifications);
    var market =
        new SharedBrokeragePortfolioSnapshot(
            CurrencyType.USD,
            new BigDecimal("50"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(new BrokeragePositionSnapshot("ETF", new BigDecimal("100"))));

    var values = calculator.values(market, List.of(), BigDecimal.ZERO, (value, ignored) -> value);
    var allocations = calculator.allocations(values);
    var reconciliation =
        calculator.reconciliation(values, AssetHorizon.SHORT_TERM, new BigDecimal("50"));

    assertThat(allocations).extracting(ProfileAllocation::bucket).contains(EconomicBucket.EQUITY);
    assertThat(reconciliation.delta()).isEqualByComparingTo("-50");
    assertThat(reconciliation.balanced()).isFalse();
  }

  @Test
  void mapsUnknownBrokeragePositionsToOther() {
    var classifications = mock(BrokerageAssetClassificationReader.class);
    when(classifications.findBySymbols(any())).thenReturn(Map.of());
    var calculator = new ProfileAllocationCalculator(classifications);
    var market =
        new SharedBrokeragePortfolioSnapshot(
            CurrencyType.USD,
            new BigDecimal("100"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(new BrokeragePositionSnapshot("UNKNOWN", new BigDecimal("100"))));

    var allocations =
        calculator.allocations(calculator.values(market, List.of(), BigDecimal.ZERO, (v, c) -> v));

    assertThat(allocations)
        .extracting(ProfileAllocation::bucket)
        .containsExactly(EconomicBucket.OTHER);
  }

  @Test
  void treatsContractualBondsAsLocked() {
    var calculator =
        new ProfileAllocationCalculator(mock(BrokerageAssetClassificationReader.class));
    var bond =
        new LongTermAssetProfileAssetModel(
            AssetEconomicCategory.FIXED_INCOME, CurrencyType.USD, new BigDecimal("200000"), false);

    assertThat(calculator.liquidity(bond.category(), bond.fundingAvailable()))
        .isEqualTo(Liquidity.ILLIQUID);
  }

  @Test
  void treatsCashReserveAsLiquidRatherThanContractual() {
    var calculator =
        new ProfileAllocationCalculator(mock(BrokerageAssetClassificationReader.class));

    var reserve =
        new LongTermAssetProfileAssetModel(
            AssetEconomicCategory.LIQUID_CASH, CurrencyType.USD, new BigDecimal("300"), true);

    assertThat(calculator.liquidity(reserve.category(), reserve.fundingAvailable()))
        .isEqualTo(Liquidity.LIQUID);
  }

  @Test
  void reportsZeroSharesWhenSignedAllocationsNetToZero() {
    var calculator =
        new ProfileAllocationCalculator(mock(BrokerageAssetClassificationReader.class));
    var values =
        Map.of(
            new ProfileAllocationCalculator.AllocationKey(
                EconomicBucket.EQUITY, AssetHorizon.SHORT_TERM, Liquidity.LIQUID),
            new BigDecimal("100"),
            new ProfileAllocationCalculator.AllocationKey(
                EconomicBucket.LIQUID_CASH, AssetHorizon.SHORT_TERM, Liquidity.LIQUID),
            new BigDecimal("-100"));

    assertThat(calculator.allocations(values))
        .allSatisfy(allocation -> assertThat(allocation.percentage()).isZero());
  }

  @Test
  void mapsEveryLongTermEconomicCategoryToProfileVocabulary() {
    var calculator =
        new ProfileAllocationCalculator(mock(BrokerageAssetClassificationReader.class));

    assertThat(calculator.classify(AssetEconomicCategory.REAL_ESTATE))
        .isEqualTo(EconomicBucket.REAL_ESTATE);
    assertThat(calculator.classify(AssetEconomicCategory.FIXED_INCOME))
        .isEqualTo(EconomicBucket.FIXED_INCOME);
    assertThat(calculator.classify(AssetEconomicCategory.LIQUID_CASH))
        .isEqualTo(EconomicBucket.LIQUID_CASH);
    assertThat(calculator.classify(AssetEconomicCategory.PERSONAL_ASSET))
        .isEqualTo(EconomicBucket.OTHER);
  }
}
