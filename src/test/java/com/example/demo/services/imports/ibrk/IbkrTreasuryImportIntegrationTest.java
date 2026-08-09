package com.example.demo.services.imports.ibrk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.services.imports.ImportExecutionResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@ActiveProfiles("test-fast")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IbkrTreasuryImportIntegrationTest {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("investory_ibkr_test")
          .withUsername("investory")
          .withPassword("investory");

  private static final String TREASURY_ROWS =
      String.join(
          "\n",
          "Transaction History,Header,Date,Account,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount,Commission,Net Amount,Currency",
          "Transaction History,Data,2025-03-26,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8 02/28/26,1000,100.41049125,USD,-1004.10,-5.00,-1009.10,USD",
          "Transaction History,Data,2025-04-17,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8 02/28/26,1000,100.484375,USD,-1004.84,-5.00,-1009.84,USD",
          "Transaction History,Data,2025-05-06,Alex&Olga,T 4 5/8 02/28/26,Buy,T 4 5/8 02/28/26,8000,100.42611625,USD,-8034.09,-5.00,-8039.09,USD",
          "Transaction History,Data,2025-08-31,Alex&Olga,Bond coupon,Investment Interest Paid,T 4 5/8 02/28/26,-,-,-,,-,-1.00,USD",
          "Transaction History,Data,2025-09-01,Alex&Olga,Bond coupon,Investment Interest Paid,T 4 5/8 02/28/26,-,-,-,,-,-2.00,USD",
          "Transaction History,Data,2025-10-01,Alex&Olga,Bond coupon,Investment Interest Paid,T 4 5/8 02/28/26,-,-,-,,-,-3.00,USD",
          "Transaction History,Data,2025-08-31,Alex&Olga,Bond coupon,Investment Interest Received,T 4 5/8 02/28/26,-,-,-,,-,4.50,USD",
          "Transaction History,Data,2025-09-01,Alex&Olga,Bond coupon,Investment Interest Received,T 4 5/8 02/28/26,-,-,-,,-,5.50,USD",
          "Transaction History,Data,2026-02-27,Alex&Olga,\"(US91282CKB62) Full Call / Early Redemption for USD 1.00 per Bond (T 4 5/8 02/28/26, T 4 5/8 02/28/26, US91282CKB62)\",Corporate Action,T 4 5/8 02/28/26,-,-,-,10000.00,0,10000.00,USD");

  @Autowired private AssetRepository assetRepository;
  @Autowired private CashOperationRepository cashOperationRepository;
  @Autowired private ClosedPositionRepository closedPositionRepository;
  @Autowired private OpenedPositionRepository openedPositionRepository;
  @Autowired private IbkrImportService ibkrImportService;

  @BeforeAll
  static void migrateDatabase() {
    POSTGRES.start();
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:sql/migration")
        .load()
        .migrate();
  }

  @AfterAll
  static void stopDatabase() {
    POSTGRES.stop();
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

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
        operations.stream().filter(operation -> operation.getType() == CashOperationType.TRANSFER).count());

    double importedCash = operations.stream().mapToDouble(CashOperation::getAmount).sum();
    assertEquals(-54.03, importedCash, 0.000001);

    List<ClosedPosition> closed = closedPositionRepository.findClosedByAssetId(treasury.getId());
    assertEquals(3, closed.size());
    assertEquals(10000.0, closed.stream().mapToDouble(ClosedPosition::getVolume).sum(), 0.000001);
    assertEquals(10000.0, closed.stream().mapToDouble(ClosedPosition::getSaleValue).sum(), 0.000001);
    assertEquals(0, openedPositionRepository.findOpenByAssetId(treasury.getId()).size());
  }
}
