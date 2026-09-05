package com.smartbox.investory.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassificationReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.shared.assets.AssetEconomicCategory;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfilePlanningCalculatorTest {
  @Test
  void mapsProjectionFactsIntoProjectedAssets() {
    var date = LocalDate.of(2026, 6, 1);
    var input =
        new LongTermAssetProjectionModel(
            1L,
            "Property",
            AssetEconomicCategory.REAL_ESTATE,
            CurrencyType.USD,
            new BigDecimal("100"),
            List.of(
                new LongTermAssetProjectionModel.Period(
                    date,
                    null,
                    new BigDecimal("10"),
                    new BigDecimal("4"),
                    new BigDecimal("0.01"),
                    null,
                    false)),
            List.of(),
            null,
            false);

    var state =
        new ProfilePlanningCalculator(
                new ProfileAllocationCalculator(mock(BrokerageAssetClassificationReader.class)))
            .state(List.of(input), date);

    assertThat(state.assets())
        .singleElement()
        .satisfies(
            asset -> {
              assertThat(asset.bucket()).isEqualTo(EconomicBucket.REAL_ESTATE);
              assertThat(asset.periods())
                  .singleElement()
                  .satisfies(
                      period -> {
                        assertThat(period.annualIncome()).isEqualByComparingTo("10");
                        assertThat(period.annualExpense()).isEqualByComparingTo("4");
                        assertThat(period.annualReturnRate()).isEqualByComparingTo("0.01");
                      });
            });
  }

  @Test
  void trustsLongTermFundingAvailabilityWithoutReinterpretingMaturity() {
    var date = LocalDate.of(2026, 6, 1);
    var unavailableCash = cash(1L, date.minusDays(1), false);
    var availableCash = cash(2L, date.plusDays(1), true);

    var assets =
        new ProfilePlanningCalculator(
                new ProfileAllocationCalculator(mock(BrokerageAssetClassificationReader.class)))
            .state(List.of(unavailableCash, availableCash), date)
            .assets();

    assertThat(assets.get(0).liquidity()).isEqualTo(Liquidity.ILLIQUID);
    assertThat(assets.get(1).liquidity()).isEqualTo(Liquidity.LIQUID);
  }

  private static LongTermAssetProjectionModel cash(
      Long id, LocalDate maturityDate, boolean fundingAvailable) {
    return new LongTermAssetProjectionModel(
        id,
        "Cash " + id,
        AssetEconomicCategory.LIQUID_CASH,
        CurrencyType.USD,
        new BigDecimal("100"),
        List.of(),
        List.of(),
        maturityDate,
        fundingAvailable);
  }
}
