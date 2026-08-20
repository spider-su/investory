package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.retirement.profile.EconomicBucket;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundingAllocatorTest {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  @Test
  void defaultOrderConsumesCashThenBondsThenStocks() {
    FundingAllocator.Result result = allocate(SimulationAssumptions.DEFAULT_FUNDING_ORDER, 25);

    assertAmounts(result, "0", "5", "30");
    assertEquals(ZERO, result.unfundedAmount());
  }

  @Test
  void customCashStocksBondsOrderConsumesStocksBeforeBonds() {
    FundingAllocator.Result result =
        allocate(List.of(FundingSource.CASH, FundingSource.STOCKS, FundingSource.BONDS), 25);

    assertAmounts(result, "0", "20", "15");
    assertEquals(new BigDecimal("15"), result.stocksWithdrawal());
  }

  @Test
  void customBondsCashStocksOrderConsumesBondsFirst() {
    FundingAllocator.Result result =
        allocate(List.of(FundingSource.BONDS, FundingSource.CASH, FundingSource.STOCKS), 25);

    assertAmounts(result, "5", "0", "30");
  }

  @Test
  void insufficientSourcesPreserveShortfall() {
    FundingAllocator.Result result = allocate(SimulationAssumptions.DEFAULT_FUNDING_ORDER, 70);

    assertAmounts(result, "0", "0", "0");
    assertEquals(new BigDecimal("10"), result.unfundedAmount());
  }

  @Test
  void duplicateAndEmptyOrdersAreRejected() {
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(null, 40, 40);

    assertThrows(
        IllegalArgumentException.class,
        () -> assumptions.withFundingOrder(List.of(FundingSource.CASH, FundingSource.CASH)));
    assertThrows(IllegalArgumentException.class, () -> assumptions.withFundingOrder(List.of()));
  }

  private static FundingAllocator.Result allocate(List<FundingSource> order, int amount) {
    EnumMap<EconomicBucket, BigDecimal> balances = new EnumMap<>(EconomicBucket.class);
    balances.put(EconomicBucket.LIQUID_CASH, new BigDecimal("10"));
    balances.put(EconomicBucket.FIXED_INCOME, new BigDecimal("20"));
    balances.put(EconomicBucket.EQUITY, new BigDecimal("30"));
    return FundingAllocator.fund(
        balances,
        BigDecimal.valueOf(amount),
        SimulationAssumptions.defaults(null, 40, 40).withFundingOrder(order));
  }

  private static void assertAmounts(
      FundingAllocator.Result result, String cash, String bonds, String stocks) {
    assertEquals(new BigDecimal(cash), result.balances().get(EconomicBucket.LIQUID_CASH));
    assertEquals(new BigDecimal(bonds), result.balances().get(EconomicBucket.FIXED_INCOME));
    assertEquals(new BigDecimal(stocks), result.balances().get(EconomicBucket.EQUITY));
  }
}
