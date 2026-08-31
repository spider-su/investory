package com.smartbox.investory.services.imports.ibrk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.infrastructure.CashOperationType;
import com.smartbox.investory.infrastructure.repository.Asset;
import com.smartbox.investory.infrastructure.repository.AssetRepository;
import com.smartbox.investory.infrastructure.repository.CashOperation;
import com.smartbox.investory.infrastructure.repository.CashOperationRepository;
import com.smartbox.investory.infrastructure.repository.ClosedPosition;
import com.smartbox.investory.infrastructure.repository.ClosedPositionRepository;
import com.smartbox.investory.infrastructure.repository.OpenedPositionRepository;
import com.smartbox.investory.services.imports.ImportExecutionResult;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class IbkrTreasuryImportIT extends FastDatabaseTest {

  private static final String TREASURY_ROWS =
      String.join(
          "\n",
          "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount,Commission,Net Amount,Currency",
          "Transaction History,Data,2025-03-26,Sample Broker User,T 4 5/8 02/28/26,Buy,T 4 5/8 02/28/26,1000,100.41049125,USD,-1004.10,-5.00,-1009.10,USD",
          "Transaction History,Data,2025-04-17,Sample Broker User,T 4 5/8 02/28/26,Buy,T 4 5/8 02/28/26,1000,100.484375,USD,-1004.84,-5.00,-1009.84,USD",
          "Transaction History,Data,2025-05-06,Sample Broker User,T 4 5/8 02/28/26,Buy,T 4 5/8 02/28/26,8000,100.42611625,USD,-8034.09,-5.00,-8039.09,USD",
          "Transaction History,Data,2025-08-31,Sample Broker User,Bond coupon,Investment Interest Paid,T 4 5/8 02/28/26,-,-,-,,-,-1.00,USD",
          "Transaction History,Data,2025-09-01,Sample Broker User,Bond coupon,Investment Interest Paid,T 4 5/8 02/28/26,-,-,-,,-,-2.00,USD",
          "Transaction History,Data,2025-10-01,Sample Broker User,Bond coupon,Investment Interest Paid,T 4 5/8 02/28/26,-,-,-,,-,-3.00,USD",
          "Transaction History,Data,2025-08-31,Sample Broker User,Bond coupon,Investment Interest Received,T 4 5/8 02/28/26,-,-,-,,-,4.50,USD",
          "Transaction History,Data,2025-09-01,Sample Broker User,Bond coupon,Investment Interest Received,T 4 5/8 02/28/26,-,-,-,,-,5.50,USD",
          "Transaction History,Data,2026-02-27,Sample Broker User,\"(US91282CKB62) Full Call / Early Redemption for USD 1.00 per Bond (T 4 5/8 02/28/26, T 4 5/8 02/28/26, US91282CKB62)\",Corporate Action,T 4 5/8 02/28/26,-,-,-,10000.00,0,10000.00,USD");

  @Autowired private AssetRepository assetRepository;
  @Autowired private CashOperationRepository cashOperationRepository;
  @Autowired private ClosedPositionRepository closedPositionRepository;
  @Autowired private OpenedPositionRepository openedPositionRepository;
  @Autowired private IbkrImportService ibkrImportService;

  @Test
  void importsAllTreasuryRowsAndReconstructsRedemption() throws Exception {
    List<Asset> treasuryAssets = assetRepository.findAllByIbrkIgnoreCase("T458022826");
    assertEquals(1, treasuryAssets.size());
    Asset treasury = treasuryAssets.getFirst();
    assertEquals("US91282CKB62", treasury.getSymbol());
    assertEquals("BOND", treasury.getAssetType());

    ImportExecutionResult result =
        ibkrImportService.importStatement(
            new ByteArrayInputStream(TREASURY_ROWS.getBytes(StandardCharsets.UTF_8)),
            "U17959259.TRANSACTIONS.20250211.20260227.csv");

    assertEquals(9, result.rowsTotal());
    assertEquals(9, result.rowsApplied());
    assertEquals(0, result.rowsFailed());
    assertTrue(result.details().contains("0 skipped"));

    List<CashOperation> operations = cashOperationRepository.findAllByAccount(17959259L);
    assertEquals(9, operations.size());
    assertEquals(
        3,
        operations.stream()
            .filter(operation -> operation.getType() == CashOperationType.STOCK_PURCHASE)
            .count());
    assertEquals(
        5,
        operations.stream()
            .filter(operation -> operation.getType() == CashOperationType.FREE_FUNDS_INTEREST)
            .count());
    assertEquals(
        1,
        operations.stream()
            .filter(operation -> operation.getType() == CashOperationType.TRANSFER)
            .count());

    double importedCash = operations.stream().mapToDouble(CashOperation::getAmount).sum();
    assertEquals(-54.03, importedCash, 0.000001);

    List<ClosedPosition> closed = closedPositionRepository.findClosedByAssetId(treasury.getId());
    assertEquals(3, closed.size());
    assertEquals(10000.0, closed.stream().mapToDouble(ClosedPosition::getVolume).sum(), 0.000001);
    assertEquals(
        10000.0, closed.stream().mapToDouble(ClosedPosition::getSaleValue).sum(), 0.000001);
    assertEquals(0, openedPositionRepository.findOpenByAssetId(treasury.getId()).size());
  }
}
