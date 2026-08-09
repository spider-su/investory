package com.example.demo.services.imports.xtb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.services.imports.ImportExecutionResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.TimeZone;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
class XtbImportIntegrationTest {

  private static final long ACCOUNT_ID = 51729109L;
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("investory_xtb_test")
          .withUsername("investory")
          .withPassword("investory");

  @Autowired private AssetRepository assetRepository;
  @Autowired private CashOperationRepository cashOperationRepository;
  @Autowired private ClosedPositionRepository closedPositionRepository;
  @Autowired private OpenedPositionRepository openedPositionRepository;
  @Autowired private XtbImportV2Service xtbImportV2Service;

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
  void usesConfiguredAccountCurrencyKeepsExcludedHistoryAndPreservesUtc() throws Exception {
    Asset excludedAsset = assetRepository.findBySymbol("ALE.PL").orElseThrow();
    Asset quotedAsset = assetRepository.findBySymbol("META.US").orElseThrow();
    assertTrue(Boolean.TRUE.equals(excludedAsset.getExcludeFromImport()));

    TimeZone originalTimeZone = TimeZone.getDefault();
    ImportExecutionResult result;
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
      result =
          xtbImportV2Service.importWorkbook(
              new ByteArrayInputStream(workbookBytes()),
              "IKE_51729109_2025-12-31_2026-07-31.xlsx");
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }

    assertEquals(3, result.rowsTotal());
    assertEquals(3, result.rowsApplied());
    assertEquals(0, result.rowsFailed());
    assertTrue(result.details().contains("cash=2 closed=1 open=2"));

    List<CashOperation> cash = cashOperationRepository.findAllByAccount(ACCOUNT_ID);
    assertEquals(2, cash.size());
    assertTrue(cash.stream().allMatch(operation -> operation.getCurrency() == CurrencyType.PLN));
    assertEquals(
        0,
        cash.stream()
            .map(CashOperation::getAmountValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .compareTo(BigDecimal.valueOf(-300)));
    assertEquals(
        ZonedDateTime.of(LocalDateTime.of(2025, 12, 5, 15, 28, 56, 294_000_000), ZoneOffset.UTC),
        cash.stream().filter(operation -> operation.getId() == 9001L).findFirst().orElseThrow().getDate());
    assertEquals(
        ZonedDateTime.of(LocalDateTime.of(2026, 7, 10, 10, 0), ZoneOffset.UTC),
        cash.stream().filter(operation -> operation.getId() == 9002L).findFirst().orElseThrow().getDate());

    List<OpenedPosition> opened = openedPositionRepository.findOpenByAssetId(excludedAsset.getId());
    assertEquals(3, opened.stream().mapToDouble(OpenedPosition::getVolume).sum(), 0.000001);
    assertTrue(opened.stream().allMatch(position -> position.getAssetId().equals(excludedAsset.getId())));
    assertTrue(opened.stream().allMatch(position -> position.getPriceCurrency() == CurrencyType.PLN));
    assertTrue(opened.stream().allMatch(position -> position.getCostCurrency() == CurrencyType.PLN));
    assertTrue(opened.stream().allMatch(position -> position.getProfitCurrency() == CurrencyType.PLN));
    assertTrue(opened.stream().allMatch(position -> position.getCommissionCurrency() == CurrencyType.PLN));

    List<ClosedPosition> closed = closedPositionRepository.findClosedByAssetId(quotedAsset.getId());
    assertEquals(1, closed.size());
    ClosedPosition position = closed.getFirst();
    assertEquals(CurrencyType.USD, position.getPriceCurrency());
    assertEquals(CurrencyType.PLN, position.getCostCurrency());
    assertEquals(CurrencyType.PLN, position.getProfitCurrency());
    assertEquals(CurrencyType.PLN, position.getCommissionCurrency());
    assertEquals(
        ZonedDateTime.of(LocalDateTime.of(2025, 12, 5, 15, 28, 56, 294_000_000), ZoneOffset.UTC),
        position.getOpenTime());
    assertEquals(
        ZonedDateTime.of(LocalDateTime.of(2026, 7, 10, 10, 0), ZoneOffset.UTC),
        position.getCloseTime());
    assertNotNull(position.getAssetId());
  }

  @Test
  void treatsXtbThreePlaceholderAsMissingTickerOnCashOnlyRows() throws Exception {
    ImportExecutionResult result =
        xtbImportV2Service.importWorkbook(
            new ByteArrayInputStream(cashOnlyPlaceholderWorkbookBytes()),
            "PLN_50290466_2025-12-31_2026-07-31.xlsx");

    assertEquals(1, result.rowsTotal());
    assertEquals(1, result.rowsApplied());
    assertEquals(0, result.rowsFailed());

    CashOperation deposit =
        cashOperationRepository.findAllByAccount(50290466L).stream()
            .filter(operation -> operation.getId() == 99101L)
            .findFirst()
            .orElseThrow();

    assertEquals(CurrencyType.PLN, deposit.getCurrency());
    assertEquals(0, deposit.getAmountValue().compareTo(BigDecimal.valueOf(14_200)));
    assertNull(deposit.getSymbol());
    assertNull(deposit.getAssetId());

    assertTrue(
        assetRepository.findBySymbol("3").isEmpty(),
        "XTB N/A placeholder must never create or require an asset named '3'");
  }

  private byte[] workbookBytes() throws Exception {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      CellStyle dateStyle = workbook.createCellStyle();
      dateStyle.setDataFormat(
          workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss.000"));

      XSSFSheet cash = workbook.createSheet("Cash Operations");
      cash.createRow(0).createCell(1).setCellValue(String.valueOf(ACCOUNT_ID));
      cash.getRow(0).createCell(0).setCellValue("Account number");
      String[] cashHeaders = {"ID", "Type", "Ticker", "Time", "Amount", "Comment"};
      writeHeader(cash.createRow(1), cashHeaders);
      Row winter = cash.createRow(2);
      winter.createCell(0).setCellValue(9001);
      winter.createCell(1).setCellValue("Stock purchase");
      winter.createCell(2).setCellValue("ALE.PL");
      setExcelDate(winter.createCell(3), dateStyle, LocalDateTime.of(2025, 12, 5, 15, 28, 56, 294_000_000));
      winter.createCell(4).setCellValue(-200);
      winter.createCell(5).setCellValue("OPEN BUY 2 @ 100");
      Row summer = cash.createRow(3);
      summer.createCell(0).setCellValue(9002);
      summer.createCell(1).setCellValue("Stock purchase");
      summer.createCell(2).setCellValue("ALE.PL");
      summer.createCell(3).setCellValue("2026-07-10 10:00:00");
      summer.createCell(4).setCellValue(-100);
      summer.createCell(5).setCellValue("OPEN BUY 1 @ 100");

      XSSFSheet closed = workbook.createSheet("Closed Positions");
      closed.createRow(0).createCell(1).setCellValue(String.valueOf(ACCOUNT_ID));
      closed.getRow(0).createCell(0).setCellValue("Account");
      String[] closedHeaders = {
        "Ticker",
        "Type",
        "Volume",
        "Open Time (UTC)",
        "Open Price",
        "Open Conversion Rate",
        "Close Time (UTC)",
        "Close Price",
        "Close Conversion Rate",
        "Purchase Value",
        "Sale Value",
        "Commission",
        "Margin",
        "Swap",
        "Profit/Loss",
        "Product",
        "Position ID"
      };
      writeHeader(closed.createRow(1), closedHeaders);
      Row closedRow = closed.createRow(2);
      closedRow.createCell(0).setCellValue("META.US");
      closedRow.createCell(1).setCellValue("BUY");
      closedRow.createCell(2).setCellValue(1);
      setExcelDate(closedRow.createCell(3), dateStyle, LocalDateTime.of(2025, 12, 5, 15, 28, 56, 294_000_000));
      closedRow.createCell(4).setCellValue(100);
      closedRow.createCell(5).setCellValue(3.6902);
      setExcelDate(closedRow.createCell(6), dateStyle, LocalDateTime.of(2026, 7, 10, 10, 0));
      closedRow.createCell(7).setCellValue(110);
      closedRow.createCell(8).setCellValue(3.6902);
      closedRow.createCell(9).setCellValue(369.02);
      closedRow.createCell(10).setCellValue(405.922);
      closedRow.createCell(11).setCellValue(-1);
      closedRow.createCell(12).setCellValue(0);
      closedRow.createCell(13).setCellValue(0);
      closedRow.createCell(14).setCellValue(35.902);
      closedRow.createCell(15).setCellValue("META");
      closedRow.createCell(16).setCellValue(99001);

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private byte[] cashOnlyPlaceholderWorkbookBytes() throws Exception {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      CellStyle dateStyle = workbook.createCellStyle();
      dateStyle.setDataFormat(
          workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss.000"));

      XSSFSheet cash = workbook.createSheet("Cash Operations");
      cash.createRow(0).createCell(0).setCellValue("Account number");
      cash.getRow(0).createCell(1).setCellValue("50290466");
      String[] cashHeaders = {
        "Type",
        "Instrument",
        "Ticker",
        "Category",
        "Time",
        "Amount",
        "ID",
        "Comment",
        "Product",
        "Position ID"
      };
      writeHeader(cash.createRow(1), cashHeaders);

      Row deposit = cash.createRow(2);
      deposit.createCell(0).setCellValue("Deposit");
      deposit.createCell(1).setCellValue("3");
      deposit.createCell(2).setCellValue("3");
      deposit.createCell(3).setCellValue("3");
      setExcelDate(deposit.createCell(4), dateStyle, LocalDateTime.of(2026, 3, 6, 11, 0));
      deposit.createCell(5).setCellValue(14_200);
      deposit.createCell(6).setCellValue(99101);
      deposit.createCell(7).setCellValue("eWallet golden placeholder regression");
      deposit.createCell(8).setCellValue("My Trades");
      deposit.createCell(9).setCellValue("3");

      XSSFSheet closed = workbook.createSheet("Closed Positions");
      closed.createRow(0).createCell(0).setCellValue("Account");
      closed.getRow(0).createCell(1).setCellValue("50290466");
      writeHeader(
          closed.createRow(1),
          new String[] {"Ticker", "Type", "Volume", "Open Time (UTC)", "Close Time (UTC)"});

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private static void writeHeader(Row row, String[] headers) {
    for (int index = 0; index < headers.length; index++) {
      row.createCell(index).setCellValue(headers[index]);
    }
  }

  private static void setExcelDate(Cell cell, CellStyle style, LocalDateTime value) {
    cell.setCellValue(DateUtil.getExcelDate(value, false));
    cell.setCellStyle(style);
  }
}
