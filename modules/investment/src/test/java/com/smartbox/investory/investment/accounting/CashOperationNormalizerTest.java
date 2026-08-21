package com.smartbox.investory.investment.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.investment.accounting.CashOperationNormalizer.NormalizedCashOperation;
import com.smartbox.investory.investment.accounting.CashOperationNormalizer.NormalizedCategory;
import com.smartbox.investory.investment.infrastructure.persistence.CashOperationEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CashOperationNormalizerTest {

  private final CashOperationNormalizer normalizer = new CashOperationNormalizer();

  @Test
  void normalize_classifiesExplicitTransferInOutAsInternalNotExternal() {
    CashOperationEntity out = cash(1L, 50290466L, CashOperationType.DEPOSIT, -5200.0, CurrencyType.PLN);
    out.setComment("Transfer out operation on account with id 50290466");
    out.setDate(ZonedDateTime.parse("2025-02-03T13:34:00Z"));

    CashOperationEntity in = cash(2L, 51551301L, CashOperationType.DEPOSIT, 5200.0, CurrencyType.PLN);
    in.setComment("Transfer in operation on account with id 51551301");
    in.setDate(ZonedDateTime.parse("2025-02-03T13:34:52Z"));

    List<NormalizedCashOperation> rows = normalizer.normalize(List.of(out, in));

    assertEquals(NormalizedCategory.INTERNAL_TRANSFER_OUT, rows.get(0).normalizedCategory());
    assertEquals(NormalizedCategory.INTERNAL_TRANSFER_IN, rows.get(1).normalizedCategory());
    assertFalse(rows.get(0).externalFlow());
    assertFalse(rows.get(1).externalFlow());
    assertNotNull(rows.get(0).transferGroupId());
    assertEquals(rows.get(0).transferGroupId(), rows.get(1).transferGroupId());
  }

  @Test
  void normalize_classifiesCurrencyConversionAsInternalFx() {
    CashOperationEntity pln =
        cash(10L, 50290466L, CashOperationType.TRANSFER, -20000.0, CurrencyType.PLN);
    pln.setComment(
        "Currency conversion, PLN to USD from TA: 50290466 to: 51499241, Exchange rate:0.250206");
    pln.setDate(ZonedDateTime.parse("2026-01-10T12:00:00Z"));

    CashOperationEntity usd = cash(11L, 51499241L, CashOperationType.TRANSFER, 5004.12, CurrencyType.USD);
    usd.setComment(
        "Currency conversion, PLN to USD from TA: 50290466 to: 51499241, Exchange rate:0.250206");
    usd.setDate(ZonedDateTime.parse("2026-01-10T12:01:00Z"));

    List<NormalizedCashOperation> rows = normalizer.normalize(List.of(pln, usd));

    assertTrue(
        rows.stream()
            .allMatch(row -> row.normalizedCategory() == NormalizedCategory.FX_CONVERSION));
    assertTrue(rows.stream().allMatch(NormalizedCashOperation::internalTransfer));
    assertTrue(rows.stream().allMatch(NormalizedCashOperation::fxConversion));
    assertFalse(rows.stream().anyMatch(NormalizedCashOperation::externalFlow));
    assertEquals(rows.get(0).transferGroupId(), rows.get(1).transferGroupId());
  }

  @Test
  void normalize_classifiesDividendAndTaxReversalsBySign() {
    CashOperationEntity negativeDividend =
        cash(20L, 51993106L, CashOperationType.DIVIDEND, -12.34, CurrencyType.USD);
    negativeDividend.setComment("Dividend correction");
    CashOperationEntity positiveTax =
        cash(21L, 51993106L, CashOperationType.WITHHOLDING_TAX, 15.0, CurrencyType.USD);
    positiveTax.setComment("Tax reversal");

    List<NormalizedCashOperation> rows =
        normalizer.normalize(List.of(negativeDividend, positiveTax));

    assertEquals(NormalizedCategory.DIVIDEND_REVERSAL, rows.get(0).normalizedCategory());
    assertTrue(rows.get(0).reversal());
    assertEquals(NormalizedCategory.WITHHOLDING_TAX_REVERSAL, rows.get(1).normalizedCategory());
    assertTrue(rows.get(1).reversal());
  }

  @Test
  void normalize_pairsZeroNetSubaccountTransfers() {
    CashOperationEntity left =
        cash(30L, 51548444L, CashOperationType.SUBACCOUNT_TRANSFER, -1250.0, CurrencyType.EUR);
    left.setComment("Transfer from 51548444 to 51551130");
    left.setDate(ZonedDateTime.parse("2024-11-30T00:48:00Z"));
    CashOperationEntity right =
        cash(31L, 51548444L, CashOperationType.SUBACCOUNT_TRANSFER, 1250.0, CurrencyType.EUR);
    right.setComment("Transfer from 51548444 to 51551130");
    right.setDate(ZonedDateTime.parse("2024-11-30T00:49:00Z"));

    List<NormalizedCashOperation> rows = normalizer.normalize(List.of(left, right));

    assertTrue(
        rows.stream()
            .allMatch(row -> row.normalizedCategory() == NormalizedCategory.INTERNAL_BOOKKEEPING));
    assertEquals(rows.get(0).transferGroupId(), rows.get(1).transferGroupId());
    assertFalse(rows.get(0).externalFlow());
    assertFalse(rows.get(1).externalFlow());
  }

  @Test
  void normalize_doesNotTreatUnexplainedNegativeDepositAsExternalWithdrawal() {
    CashOperationEntity negativeDeposit =
        cash(40L, 51499241L, CashOperationType.DEPOSIT, -42.50, CurrencyType.USD);
    negativeDeposit.setComment("manual correction without transfer hint");

    NormalizedCashOperation row = normalizer.normalize(List.of(negativeDeposit)).getFirst();

    assertNotEquals(NormalizedCategory.EXTERNAL_WITHDRAWAL, row.normalizedCategory());
    assertFalse(row.externalFlow());
  }

  @Test
  void normalize_doesNotTreatPositiveWithdrawalAsExternalDeposit() {
    CashOperationEntity positiveWithdrawal =
        cash(41L, 51499241L, CashOperationType.WITHDRAWAL, 42.50, CurrencyType.USD);
    positiveWithdrawal.setComment("withdrawal reversal");

    NormalizedCashOperation row = normalizer.normalize(List.of(positiveWithdrawal)).getFirst();

    assertNotEquals(NormalizedCategory.EXTERNAL_DEPOSIT, row.normalizedCategory());
    assertFalse(row.externalFlow());
  }

  @Test
  void normalize_zeroDepositDoesNotBecomeNormalCapitalFlow() {
    CashOperationEntity zeroDeposit =
        cash(42L, 51499241L, CashOperationType.DEPOSIT, 0.0, CurrencyType.USD);
    zeroDeposit.setComment("zero adjustment");

    NormalizedCashOperation row = normalizer.normalize(List.of(zeroDeposit)).getFirst();

    assertEquals(CashOperationNormalizer.EconomicDirection.NEUTRAL, row.economicDirection());
    assertNotEquals(NormalizedCategory.EXTERNAL_DEPOSIT, row.normalizedCategory());
    assertFalse(row.externalFlow());
  }

  @Test
  void normalize_unmatchedInternalTransferRemainsVisibleAndUnpaired() {
    CashOperationEntity transferOut =
        cash(43L, 50290466L, CashOperationType.DEPOSIT, -5200.0, CurrencyType.PLN);
    transferOut.setComment("Transfer out operation on account with id 50290466");
    transferOut.setDate(ZonedDateTime.parse("2025-02-03T13:34:00Z"));

    NormalizedCashOperation row = normalizer.normalize(List.of(transferOut)).getFirst();

    assertEquals(NormalizedCategory.INTERNAL_TRANSFER_OUT, row.normalizedCategory());
    assertNull(row.relatedOperationId());
    assertNull(row.transferGroupId());
  }

  @Test
  void normalize_transferBetweenAccountsUsesAccountCluesNotOnlyAmountAndTime() {
    CashOperationEntity legA =
        cash(50L, 51499241L, CashOperationType.TRANSFER, -1000.0, CurrencyType.USD);
    legA.setComment("Transfer from 51499241 to 51993106");
    legA.setDate(ZonedDateTime.parse("2026-01-10T10:00:00Z"));

    CashOperationEntity legB = cash(51L, 51993106L, CashOperationType.TRANSFER, 1000.0, CurrencyType.USD);
    legB.setComment("Transfer from 51499241 to 51993106");
    legB.setDate(ZonedDateTime.parse("2026-01-10T10:01:00Z"));

    CashOperationEntity unrelated =
        cash(52L, 53582946L, CashOperationType.TRANSFER, 1000.0, CurrencyType.USD);
    unrelated.setComment("Transfer from 11111111 to 22222222");
    unrelated.setDate(ZonedDateTime.parse("2026-01-10T10:02:00Z"));

    List<NormalizedCashOperation> rows = normalizer.normalize(List.of(legA, unrelated, legB));

    assertEquals(NormalizedCategory.INTERNAL_TRANSFER_OUT, rows.get(0).normalizedCategory());
    assertEquals(NormalizedCategory.INTERNAL_TRANSFER_IN, rows.get(2).normalizedCategory());
    assertEquals(rows.get(0).transferGroupId(), rows.get(2).transferGroupId());
    assertNull(rows.get(1).transferGroupId());
  }

  @Test
  void normalize_subaccountTransferPairingIsInputOrderIndependent() {
    CashOperationEntity left =
        cash(60L, 51548444L, CashOperationType.SUBACCOUNT_TRANSFER, -1250.0, CurrencyType.EUR);
    left.setComment("Transfer from 51548444 to 51551130");
    left.setDate(ZonedDateTime.parse("2024-11-30T00:48:00Z"));
    CashOperationEntity right =
        cash(61L, 51548444L, CashOperationType.SUBACCOUNT_TRANSFER, 1250.0, CurrencyType.EUR);
    right.setComment("Transfer from 51548444 to 51551130");
    right.setDate(ZonedDateTime.parse("2024-11-30T00:49:00Z"));

    List<NormalizedCashOperation> forward = normalizer.normalize(List.of(left, right));
    List<NormalizedCashOperation> backward = normalizer.normalize(List.of(right, left));

    assertNotNull(forward.get(0).transferGroupId());
    assertEquals(forward.get(0).transferGroupId(), forward.get(1).transferGroupId());
    assertNotNull(backward.get(0).transferGroupId());
    assertEquals(backward.get(0).transferGroupId(), backward.get(1).transferGroupId());
  }

  @Test
  void normalize_negativeInterestBecomesInterestReversal() {
    CashOperationEntity interest =
        cash(70L, 51499241L, CashOperationType.FREE_FUNDS_INTEREST, -1.23, CurrencyType.USD);

    NormalizedCashOperation row = normalizer.normalize(List.of(interest)).getFirst();

    assertEquals(NormalizedCategory.INTEREST_REVERSAL, row.normalizedCategory());
    assertTrue(row.reversal());
  }

  @Test
  void normalize_ibkrBondRedemptionIsSettlementNotFunding() {
    CashOperationEntity redemption =
        cash(75L, 17959259L, CashOperationType.TRANSFER, 10_000.0, CurrencyType.USD);
    redemption.setComment("(US91282CKB62) Full Call / Early Redemption for USD 1.00 per Bond");

    NormalizedCashOperation row = normalizer.normalize(List.of(redemption)).getFirst();

    assertEquals(NormalizedCategory.BOND_REDEMPTION, row.normalizedCategory());
    assertFalse(row.externalFlow());
    assertFalse(row.internalTransfer());
    assertTrue(row.tradeCashFlow());
  }

  @Test
  void normalize_feeCorrectionsBecomeFeeReversals() {
    CashOperationEntity commissionRefund =
        cash(71L, 51499241L, CashOperationType.CORRECTION, 5.00, CurrencyType.USD);
    commissionRefund.setComment("Commission Refund");

    CashOperationEntity secFeeAdjustment =
        cash(72L, 51499241L, CashOperationType.CORRECTION, 0.01, CurrencyType.USD);
    secFeeAdjustment.setComment("corr Sec Fee adj");

    List<NormalizedCashOperation> rows =
        normalizer.normalize(List.of(commissionRefund, secFeeAdjustment));

    assertEquals(NormalizedCategory.FEE, rows.get(0).normalizedCategory());
    assertTrue(rows.get(0).reversal());
    assertEquals(NormalizedCategory.FEE, rows.get(1).normalizedCategory());
    assertTrue(rows.get(1).reversal());
  }

  private static CashOperationEntity cash(
      Long id, Long accountId, CashOperationType type, double amount, CurrencyType currency) {
    CashOperationEntity operation = new CashOperationEntity();
    operation.setId(id);
    operation.setAccount(accountId);
    operation.setType(type);
    operation.setAmount(amount);
    operation.setCurrency(currency);
    operation.setDate(ZonedDateTime.parse("2026-01-01T00:00:00Z"));
    return operation;
  }
}
