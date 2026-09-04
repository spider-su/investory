package com.smartbox.investory.testsupport.happyinvestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import org.junit.jupiter.api.Test;

class HappyInvestorScenarioTest {
  @Test
  void exposesMigrationBackedIdentityAndSemanticInvariants() {
    HappyInvestorContext investor = HappyInvestorScenario.create();

    assertEquals(HappyInvestorExpected.ACCOUNT_COUNT, investor.accounts().size());
    assertEquals(HappyInvestorTestData.XTB_USD_ACCOUNT_ID, investor.xtbUsd().getId());
    assertEquals("XTB USD investment account", investor.xtbUsd().getName());
    assertEquals(HappyInvestorTestData.XTB_PLN_ACCOUNT_ID, investor.xtbPln().getId());
    assertEquals("XTB PLN investment account", investor.xtbPln().getName());
    assertEquals(HappyInvestorTestData.XTB_EUR_ACCOUNT_ID, investor.xtbEur().getId());
    assertEquals("XTB EUR cash-only account", investor.xtbEur().getName());
    assertTrue(investor.xtbEur().isCashOnly());
    assertEquals(HappyInvestorTestData.WIG20_ETF_SYMBOL, investor.wig20Etf().getSymbol());
    assertEquals(HappyInvestorTestData.TREASURY_2026.symbol(), investor.treasury2026().getSymbol());
    assertEquals("United States Treasury 4 5/8 02/28/26", investor.treasury2026().getName());
    assertEquals("BOND", investor.treasury2026().getAssetType());
    assertEquals(HappyInvestorTestData.TREASURY_2033.symbol(), investor.treasury2033().getSymbol());
    assertEquals("United States Treasury 4 3/8 07/31/33", investor.treasury2033().getName());
    assertEquals("BOND", investor.treasury2033().getAssetType());
    assertEquals(HappyInvestorTestData.NATGAS_SYMBOL, investor.natgas().getSymbol());
    assertTrue(
        investor.closedPositions().stream()
            .anyMatch(
                position ->
                    HappyInvestorTestData.NATGAS_SYMBOL.equals(position.getSymbol())
                        && "RESULT_ONLY".equals(position.getSettlementModel().name())
                        && position.getCloseTime() != null
                        && position.getAccount().equals(investor.xtbUsd().getId())));
    assertEquals(25, investor.simulation().horizonYears());
    assertEquals(HappyInvestorTestData.INFLATION, investor.simulation().inflationRate());
    assertEquals(
        HappyInvestorTestData.EUR_USD_AT_HISTORY_START.multiply(
            HappyInvestorTestData.USD_PLN_AT_HISTORY_START),
        HappyInvestorTestData.eurPlnAtHistoryStart());
    assertEquals(
        HappyInvestorTestData.EUR_PLN_AT_HISTORY_START,
        HappyInvestorTestData.eurPlnAtHistoryStart());
    assertTrue(
        HappyInvestorTestData.PLN_USD_TRANSFER_AMOUNT
                .subtract(
                    HappyInvestorTestData.PLN_USD_TRANSFER_RATE.multiply(
                        new java.math.BigDecimal("500")))
                .abs()
                .compareTo(new java.math.BigDecimal("0.0000000000001"))
            < 0);
    assertFalse(investor.openPositions().stream().anyMatch(p -> "SPY.US".equals(p.getSymbol())));
    assertTrue(
        investor.openPositions().stream()
            .anyMatch(
                p ->
                    "VWRA.UK".equals(p.getSymbol())
                        && p.getAccount().equals(investor.ibkrUsd().getId())));
    assertTrue(
        investor.openPositions().stream()
            .anyMatch(
                p ->
                    "VWRA.UK".equals(p.getSymbol())
                        && p.getAccount().equals(investor.xtbPln().getId())));
  }

  @Test
  void coversEveryAccountWithAnExternalWithdrawalAndKeepsTransfersCorrelated() {
    HappyInvestorContext investor = HappyInvestorScenario.create();

    assertEquals(
        HappyInvestorExpected.WITHDRAWAL_ACCOUNT_COUNT,
        investor.ledger().stream()
            .filter(op -> op.getType() == CashOperationType.WITHDRAWAL)
            .map(op -> op.getAccount())
            .distinct()
            .count());
    assertTrue(
        investor.ledger().stream()
                .filter(op -> op.getType() == CashOperationType.TRANSFER)
                .map(op -> op.getComment())
                .distinct()
                .count()
            >= 4);
    assertTrue(
        investor.ledger().stream()
            .filter(op -> op.getType() == CashOperationType.TRANSFER)
            .map(op -> op.getComment())
            .distinct()
            .allMatch(
                ref ->
                    investor.ledger().stream().filter(op -> ref.equals(op.getComment())).count()
                        == 2));
    assertEquals(23, investor.ledger().size());
    assertEquals(
        HappyInvestorTestData.EUR_PLN_TRANSFER_AMOUNT.doubleValue(),
        amount(investor, "EUR-PLN-2024-07-31", HappyInvestorTestData.XTB_PLN_ACCOUNT_ID),
        0.000000000001);
    assertEquals(
        HappyInvestorTestData.PLN_USD_TRANSFER_PERSISTED_AMOUNT.doubleValue(),
        amount(investor, "PLN-USD-2025-03", HappyInvestorTestData.XTB_USD_ACCOUNT_ID),
        0.000000000001);
  }

  @Test
  void mirrorsTheCanonicalSnapshotIncomeAndFeeCashOperations() {
    HappyInvestorContext investor = HappyInvestorScenario.create();

    var ibkrIncome =
        investor.ledger().stream()
            .filter(op -> op.getAccount().equals(investor.ibkrUsd().getId()))
            .toList();
    assertEquals(
        1,
        ibkrIncome.stream()
            .filter(op -> op.getType() == CashOperationType.COMMISSION)
            .filter(op -> op.getAmount().doubleValue() == -1.0)
            .count());
    assertEquals(
        1,
        ibkrIncome.stream()
            .filter(op -> op.getType() == CashOperationType.DIVIDEND)
            .filter(op -> op.getAmount().doubleValue() == 120.0)
            .count());
    assertEquals(
        1,
        ibkrIncome.stream()
            .filter(op -> op.getType() == CashOperationType.WITHHOLDING_TAX)
            .filter(op -> op.getAmount().doubleValue() == -22.8)
            .count());
    assertEquals(
        1,
        ibkrIncome.stream()
            .filter(op -> op.getType() == CashOperationType.FREE_FUNDS_INTEREST)
            .filter(op -> op.getAmount().doubleValue() == 231.25)
            .count());
    assertEquals(
        1,
        ibkrIncome.stream()
            .filter(op -> op.getType() == CashOperationType.FREE_FUNDS_INTEREST_TAX)
            .filter(op -> op.getAmount().doubleValue() == -43.9375)
            .count());
  }

  @Test
  void mirrorsTheCanonicalNatgasSettlement() {
    HappyInvestorContext investor = HappyInvestorScenario.create();

    var natgas =
        investor.ledger().stream()
            .filter(op -> HappyInvestorTestData.NATGAS_SYMBOL.equals(op.getSymbol()))
            .toList();
    assertEquals(2, natgas.size());
    assertTrue(
        natgas.stream()
            .anyMatch(
                op ->
                    op.getType() == CashOperationType.CLOSE_TRADE
                        && op.getAmount().compareTo(HappyInvestorTestData.NATGAS_CLOSE_TRADE)
                            == 0));
    assertTrue(
        natgas.stream()
            .anyMatch(
                op ->
                    op.getType() == CashOperationType.SWAP
                        && op.getAmount().compareTo(HappyInvestorTestData.NATGAS_SWAP) == 0));
    assertEquals(1, investor.closedPositions().size());
    var position = investor.closedPositions().getFirst();
    assertEquals(HappyInvestorTestData.NATGAS_CLOSE_DATE, position.getCloseTime().toLocalDate());
    assertEquals(0, HappyInvestorTestData.NATGAS_NET_RESULT.compareTo(position.getProfit()));
    assertEquals(0, HappyInvestorTestData.NATGAS_SWAP.compareTo(position.getSwap()));
  }

  private static double amount(HappyInvestorContext investor, String comment, long accountId) {
    return investor.ledger().stream()
        .filter(op -> comment.equals(op.getComment()) && op.getAccount().equals(accountId))
        .findFirst()
        .orElseThrow()
        .getAmount()
        .doubleValue();
  }
}
