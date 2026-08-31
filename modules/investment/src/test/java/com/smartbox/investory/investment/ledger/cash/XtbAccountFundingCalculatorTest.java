package com.smartbox.investory.investment.ledger.cash;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class XtbAccountFundingCalculatorTest {

  private final XtbAccountFundingCalculator calculator =
      new XtbAccountFundingCalculator(new CashOperationNormalizer());

  @Test
  void pairedTransferFromDisplayedAccountCountsOnceAsNegativeFunding() {
    Map<Long, Double> effects =
        calculate(
            transfer(1L, 100L, -1000, "Transfer from 100 to 200"),
            transfer(2L, 100L, 1000, "Transfer from 100 to 200"));

    assertEquals(-1000.0, effects.get(100L));
  }

  @Test
  void pairedTransferToDisplayedAccountCountsOnceAsPositiveFunding() {
    Map<Long, Double> effects =
        calculate(
            transfer(1L, 100L, -1000, "Transfer from 200 to 100"),
            transfer(2L, 100L, 1000, "Transfer from 200 to 100"));

    assertEquals(1000.0, effects.get(100L));
  }

  @Test
  void multipleDirectionsNetWithoutDoubleCounting() {
    Map<Long, Double> effects =
        calculate(
            transfer(1L, 100L, -1000, "Transfer from 100 to 200"),
            transfer(2L, 100L, 1000, "Transfer from 100 to 200"),
            transfer(3L, 100L, -250, "Transfer from 200 to 100"),
            transfer(4L, 100L, 250, "Transfer from 200 to 100"));

    assertEquals(-750.0, effects.get(100L));
  }

  @Test
  void knownXtbUsdReconciliationAdjustmentsAreRepresentedExactly() {
    CashOperationEntity firstOut =
        transfer(1L, 51499241L, -801.47, "Transfer from 51993106 to 51499241");
    CashOperationEntity firstIn =
        transfer(2L, 51499241L, 801.47, "Transfer from 51993106 to 51499241");
    CashOperationEntity secondOut =
        transfer(3L, 51993106L, -995.31, "Transfer from 51499241 to 51993106");
    CashOperationEntity secondIn =
        transfer(4L, 51993106L, 995.31, "Transfer from 51499241 to 51993106");

    Map<Long, Double> effects =
        calculator.calculate(
            List.of(firstOut, firstIn, secondOut, secondIn),
            Map.of(
                51499241L, account(51499241L, "XTB", CurrencyType.USD),
                51993106L, account(51993106L, "XTB", CurrencyType.USD)));

    assertEquals(801.47, effects.get(51499241L), 0.000001);
    assertEquals(995.31, effects.get(51993106L), 0.000001);
  }

  @Test
  void nonXtbAndNonUsdAccountsAreUnaffected() {
    CashOperationEntity operation = transfer(1L, 100L, -1000, "Transfer from 100 to 200");
    CashOperationEntity pair = transfer(2L, 100L, 1000, "Transfer from 100 to 200");
    CashOperationEntity eurOperation = transfer(3L, 200L, -500, "Transfer from 200 to 300");
    CashOperationEntity eurPair = transfer(4L, 200L, 500, "Transfer from 200 to 300");
    eurOperation.setCurrency(CurrencyType.EUR);
    eurPair.setCurrency(CurrencyType.EUR);
    AccountEntity ibkr = account(100L, "IBKR", CurrencyType.USD);
    AccountEntity xtbEur = account(200L, "XTB", CurrencyType.EUR);

    assertEquals(
        Map.of(),
        calculator.calculate(
            List.of(operation, pair, eurOperation, eurPair), Map.of(100L, ibkr, 200L, xtbEur)));
  }

  private Map<Long, Double> calculate(CashOperationEntity... operations) {
    return calculator.calculate(
        List.of(operations), Map.of(100L, account(100L, "XTB", CurrencyType.USD)));
  }

  private static AccountEntity account(Long id, String provider, CurrencyType currency) {
    AccountEntity account = new AccountEntity();
    account.setId(id);
    account.setProvider(provider);
    account.setCurrency(currency);
    return account;
  }

  private static CashOperationEntity transfer(
      long id, long accountId, double amount, String comment) {
    CashOperationEntity operation = new CashOperationEntity();
    operation.setId(id);
    operation.setAccount(accountId);
    operation.setType(CashOperationType.SUBACCOUNT_TRANSFER);
    operation.setAmount(amount);
    operation.setCurrency(CurrencyType.USD);
    operation.setComment(comment);
    operation.setDate(ZonedDateTime.parse("2026-01-01T12:00:00Z").plusMinutes(id));
    return operation;
  }
}
