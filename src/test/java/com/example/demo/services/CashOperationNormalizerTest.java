package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.services.CashOperationNormalizer.NormalizedCashOperation;
import com.example.demo.services.CashOperationNormalizer.NormalizedCategory;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CashOperationNormalizerTest {

  private final CashOperationNormalizer normalizer = new CashOperationNormalizer();

  @Test
  void normalize_classifiesExplicitTransferInOutAsInternalNotExternal() {
    CashOperation out = cash(1L, 50290466L, CashOperationType.DEPOSIT, -5200.0, CurrencyType.PLN);
    out.setComment("Transfer out operation on account with id 50290466");
    out.setDate(ZonedDateTime.parse("2025-02-03T13:34:00Z"));

    CashOperation in = cash(2L, 51551301L, CashOperationType.DEPOSIT, 5200.0, CurrencyType.PLN);
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
    CashOperation pln = cash(10L, 50290466L, CashOperationType.TRANSFER, -20000.0, CurrencyType.PLN);
    pln.setComment(
        "Currency conversion, PLN to USD from TA: 50290466 to: 51499241, Exchange rate:0.250206");
    pln.setDate(ZonedDateTime.parse("2026-01-10T12:00:00Z"));

    CashOperation usd = cash(11L, 51499241L, CashOperationType.TRANSFER, 5004.12, CurrencyType.USD);
    usd.setComment(
        "Currency conversion, PLN to USD from TA: 50290466 to: 51499241, Exchange rate:0.250206");
    usd.setDate(ZonedDateTime.parse("2026-01-10T12:01:00Z"));

    List<NormalizedCashOperation> rows = normalizer.normalize(List.of(pln, usd));

    assertTrue(rows.stream().allMatch(row -> row.normalizedCategory() == NormalizedCategory.FX_CONVERSION));
    assertTrue(rows.stream().allMatch(NormalizedCashOperation::internalTransfer));
    assertTrue(rows.stream().allMatch(NormalizedCashOperation::fxConversion));
    assertFalse(rows.stream().anyMatch(NormalizedCashOperation::externalFlow));
    assertEquals(rows.get(0).transferGroupId(), rows.get(1).transferGroupId());
  }

  @Test
  void normalize_classifiesDividendAndTaxReversalsBySign() {
    CashOperation negativeDividend = cash(20L, 51993106L, CashOperationType.DIVIDEND, -12.34, CurrencyType.USD);
    negativeDividend.setComment("Dividend correction");
    CashOperation positiveTax = cash(21L, 51993106L, CashOperationType.WITHHOLDING_TAX, 15.0, CurrencyType.USD);
    positiveTax.setComment("Tax reversal");

    List<NormalizedCashOperation> rows = normalizer.normalize(List.of(negativeDividend, positiveTax));

    assertEquals(NormalizedCategory.DIVIDEND_REVERSAL, rows.get(0).normalizedCategory());
    assertTrue(rows.get(0).reversal());
    assertEquals(NormalizedCategory.WITHHOLDING_TAX_REVERSAL, rows.get(1).normalizedCategory());
    assertTrue(rows.get(1).reversal());
  }

  @Test
  void normalize_pairsZeroNetSubaccountTransfers() {
    CashOperation left = cash(30L, 51548444L, CashOperationType.SUBACCOUNT_TRANSFER, -1250.0, CurrencyType.EUR);
    left.setComment("Transfer from 51548444 to 51551130");
    left.setDate(ZonedDateTime.parse("2024-11-30T00:48:00Z"));
    CashOperation right = cash(31L, 51548444L, CashOperationType.SUBACCOUNT_TRANSFER, 1250.0, CurrencyType.EUR);
    right.setComment("Transfer from 51548444 to 51551130");
    right.setDate(ZonedDateTime.parse("2024-11-30T00:49:00Z"));

    List<NormalizedCashOperation> rows = normalizer.normalize(List.of(left, right));

    assertTrue(rows.stream().allMatch(row -> row.normalizedCategory() == NormalizedCategory.INTERNAL_BOOKKEEPING));
    assertEquals(rows.get(0).transferGroupId(), rows.get(1).transferGroupId());
    assertFalse(rows.get(0).externalFlow());
    assertFalse(rows.get(1).externalFlow());
  }

  private static CashOperation cash(
      Long id, Long accountId, CashOperationType type, double amount, CurrencyType currency) {
    CashOperation operation = new CashOperation();
    operation.setId(id);
    operation.setAccount(accountId);
    operation.setType(type);
    operation.setAmount(amount);
    operation.setCurrency(currency);
    operation.setDate(ZonedDateTime.parse("2026-01-01T00:00:00Z"));
    return operation;
  }
}
