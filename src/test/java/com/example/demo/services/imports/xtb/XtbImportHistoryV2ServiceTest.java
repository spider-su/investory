package com.example.demo.services.imports.xtb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionSettlementModel;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetPriceHistoryRepository;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.infrastructure.repository.account.Account;
import com.example.demo.infrastructure.repository.account.AccountRepository;
import com.example.demo.services.AssetCatalogService;
import com.example.demo.services.PositionSettlementModelService;
import com.example.demo.services.imports.ImportExecutionResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XtbImportHistoryV2ServiceTest {

  @Mock private ClosedPositionRepository closedPositionRepository;
  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private CashOperationRepository cashOperationRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AccountRepository accountRepository;
  @Captor private ArgumentCaptor<Iterable<OpenedPosition>> openedPositionsCaptor;
  @Captor private ArgumentCaptor<Iterable<ClosedPosition>> closedPositionsCaptor;

  private XtbImportV2Service xtbImportV2Service;

  @BeforeEach
  void setUp() {
    Map<String, Asset> storedAssets = new HashMap<>();
    AtomicLong assetIds = new AtomicLong(1000);
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllBySymbolIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenAnswer(
            invocation ->
                invocation.<java.util.Collection<String>>getArgument(0).stream()
                    .map(
                        symbol ->
                            storedAssets.computeIfAbsent(
                                symbol, key -> catalogAsset(key, assetIds.incrementAndGet())))
                    .toList());
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllByTickerIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenAnswer(
            invocation -> {
              java.util.Collection<String> tickers = invocation.getArgument(0);
              return storedAssets.values().stream()
                  .filter(asset -> tickers.contains(asset.getTicker()))
                  .toList();
            });
    xtbImportV2Service =
        new XtbImportV2Service(
            closedPositionRepository,
            openedPositionRepository,
            cashOperationRepository,
            assetPriceHistoryRepository,
            assetRepository,
            accountRepository,
            new AssetCatalogService(assetRepository),
            new PositionSettlementModelService(),
            new XtbPositionCurrencyResolver());
    org.mockito.Mockito.lenient()
        .when(accountRepository.findById(org.mockito.ArgumentMatchers.anyLong()))
        .thenAnswer(
            invocation -> {
              Account account = new Account();
              account.setId(invocation.getArgument(0));
              account.setProvider("XTB");
              account.setCurrency(CurrencyType.PLN);
              return java.util.Optional.of(account);
            });
  }

  private static Asset catalogAsset(String symbol, long id) {
    int dot = symbol.lastIndexOf('.');
    String ticker = dot > 0 ? symbol.substring(0, dot) : symbol;
    String suffix = dot > 0 && dot < symbol.length() - 1 ? symbol.substring(dot + 1) : "US";
    CurrencyType currency =
        switch (suffix) {
          case "PL" -> CurrencyType.PLN;
          case "DE", "FR", "NL", "IT", "ES", "FI", "PT", "IE", "AT", "BE" -> CurrencyType.EUR;
          default -> CurrencyType.USD;
        };
    return Asset.builder()
        .id(id)
        .name(ticker)
        .symbol(symbol)
        .ticker(ticker)
        .ibrk(ticker)
        .yahoo(symbol)
        .country(suffix)
        .currency(currency)
        .assetType("EQUITY")
        .active(true)
        .build();
  }

  @Test
  void importZip_parsesNewXtbBundleAndReconstructsOpenPositions() throws Exception {
    try (InputStream inputStream =
        getClass()
            .getResourceAsStream(
                "/50290466_51499241_51548444_51993106_2023-12-31_2025-12-31.zip")) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          inputStream != null, "Fixture zip not on classpath; skipping");

      ImportExecutionResult result =
          xtbImportV2Service.importZip(
              inputStream, "50290466_51499241_51548444_51993106_2006-01-01_2026-07-05.zip");

      assertTrue(result.rowsApplied() > 0);
      assertTrue(result.details().contains("workbook"));
    }

    verify(cashOperationRepository, atLeastOnce()).saveAll(anyList());
    verify(closedPositionRepository, atLeastOnce()).saveAll(anyList());
    verify(openedPositionRepository, atLeastOnce()).saveAll(anyList());

    verify(openedPositionRepository, atLeastOnce()).saveAll(openedPositionsCaptor.capture());

    List<OpenedPosition> allOpened = new ArrayList<>();
    for (Iterable<OpenedPosition> batch : openedPositionsCaptor.getAllValues()) {
      for (OpenedPosition position : batch) {
        allOpened.add(position);
      }
    }

    assertFalse(allOpened.isEmpty(), "Open positions should be reconstructed from cash operations");
    assertTrue(
        allOpened.stream().allMatch(pos -> pos.getSymbol() != null && !pos.getSymbol().isBlank()));
  }

  @Test
  void importZip_parsesSecondXtbBundle() throws Exception {
    try (InputStream inputStream =
        getClass()
            .getResourceAsStream(
                "/51707603_51747407_51822121_53582946_2025-12-31_2026-07-31.zip")) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          inputStream != null, "Fixture zip not on classpath; skipping");

      ImportExecutionResult result =
          xtbImportV2Service.importZip(
              inputStream, "51707603_51747407_51822121_53582946_2025-12-31_2026-07-31.zip");

      assertTrue(result.rowsApplied() > 0);
      assertTrue(result.details().contains("imported 5 workbook"));
      assertTrue(result.details().contains("acc=51707603"));
      assertTrue(result.details().contains("acc=51747407"));
      assertTrue(result.details().contains("acc=51822121"));
      assertTrue(result.details().contains("acc=53582946"));
      assertTrue(result.details().contains("acc=51729109"));
    }
  }

  @Test
  void supports_detectsNewFormatWorkbook() throws Exception {
    try (InputStream inputStream =
        getClass()
            .getResourceAsStream(
                "/50290466_51499241_51548444_51993106_2023-12-31_2025-12-31.zip")) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          inputStream != null, "Fixture zip not on classpath; skipping");

      byte[] workbookBytes = firstXlsx(inputStream);
      assertTrue(xtbImportV2Service.supports(new ByteArrayInputStream(workbookBytes)));
    }
  }

  @Test
  void reconstructOpenedPositionsUsesExistingImportedCashOperationsForPartialHistory() {
    Long account = 51707603L;
    CashOperation existingOpen =
        cashOperation(
            1L, account, CashOperationType.STOCK_PURCHASE, "AAPL.US", "OPEN BUY 10 @ 100");
    CashOperation currentClose =
        cashOperation(2L, account, CashOperationType.STOCK_SELL, "AAPL.US", "CLOSE BUY 4 @ 110");
    org.mockito.Mockito.when(cashOperationRepository.findAllByAccount(account))
        .thenReturn(List.of(existingOpen));

    List<CashOperation> operations =
        xtbImportV2Service.operationsForOpenReconstruction(account, List.of(currentClose));
    List<OpenedPosition> openedPositions =
        xtbImportV2Service.reconstructOpenedPositions(operations, account, CurrencyType.USD);

    assertEquals(1, openedPositions.size());
    assertEquals("AAPL.US", openedPositions.get(0).getSymbol());
    assertEquals(6.0, openedPositions.get(0).getVolume(), 0.0001);
  }

  @Test
  void importWorkbook_usesExistingAssetSkipsFooterAndWritesTradeCheckpoints() throws Exception {
    Asset asset =
        Asset.builder()
            .id(77L)
            .name("Apple")
            .symbol("AAPL.US")
            .ticker("AAPL")
            .ibrk("AAPL")
            .yahoo("AAPL")
            .country("US")
            .currency(CurrencyType.USD)
            .assetType("EQUITY")
            .active(true)
            .build();
    org.mockito.Mockito.when(
            assetRepository.findAllBySymbolIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(asset));

    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      XSSFSheet cashSheet = workbook.createSheet("Cash Operations");
      Row cashHeader1 = cashSheet.createRow(0);
      cashHeader1.createCell(0).setCellValue("Account number");
      cashHeader1.createCell(1).setCellValue("51707603");
      Row cashHeader2 = cashSheet.createRow(1);
      cashHeader2.createCell(0).setCellValue("ID");
      cashHeader2.createCell(1).setCellValue("Type");
      cashHeader2.createCell(2).setCellValue("Ticker");
      cashHeader2.createCell(3).setCellValue("Time");
      cashHeader2.createCell(4).setCellValue("Amount");
      cashHeader2.createCell(5).setCellValue("Comment");

      Row cashRow1 = cashSheet.createRow(2);
      cashRow1.createCell(0).setCellValue(1);
      cashRow1.createCell(1).setCellValue("Stock purchase");
      cashRow1.createCell(2).setCellValue("AAPL.US");
      cashRow1.createCell(3).setCellValue("2026-07-10 10:00:00");
      cashRow1.createCell(4).setCellValue(-1000);
      cashRow1.createCell(5).setCellValue("OPEN BUY 10 @ 100");

      Row cashRow2 = cashSheet.createRow(3);
      cashRow2.createCell(0).setCellValue(2);
      cashRow2.createCell(1).setCellValue("Stock purchase");
      cashRow2.createCell(2).setCellValue("AAPL.US");
      cashRow2.createCell(3).setCellValue("2026-07-10 11:00:00");
      cashRow2.createCell(4).setCellValue(-1040);
      cashRow2.createCell(5).setCellValue("OPEN BUY 10 @ 104");

      XSSFSheet closedSheet = workbook.createSheet("Closed Positions");
      Row closedHeader1 = closedSheet.createRow(0);
      closedHeader1.createCell(0).setCellValue("Account");
      closedHeader1.createCell(1).setCellValue("51707603");
      Row closedHeader2 = closedSheet.createRow(1);
      closedHeader2.createCell(0).setCellValue("Ticker");
      closedHeader2.createCell(1).setCellValue("Type");
      closedHeader2.createCell(2).setCellValue("Volume");
      closedHeader2.createCell(3).setCellValue("Open Time (UTC)");
      closedHeader2.createCell(4).setCellValue("Open Price");
      closedHeader2.createCell(5).setCellValue("Close Time (UTC)");
      closedHeader2.createCell(6).setCellValue("Close Price");
      closedHeader2.createCell(7).setCellValue("Purchase Value");
      closedHeader2.createCell(8).setCellValue("Sale Value");
      closedHeader2.createCell(9).setCellValue("Commission");
      closedHeader2.createCell(10).setCellValue("Margin");
      closedHeader2.createCell(11).setCellValue("Swap");
      closedHeader2.createCell(12).setCellValue("Profit/Loss");
      closedHeader2.createCell(13).setCellValue("Product");

      Row closedRow = closedSheet.createRow(2);
      closedRow.createCell(0).setCellValue("AAPL.US");
      closedRow.createCell(1).setCellValue("BUY");
      closedRow.createCell(2).setCellValue(2);
      closedRow.createCell(3).setCellValue("2026-07-01 10:00:00");
      closedRow.createCell(4).setCellValue(100);
      closedRow.createCell(5).setCellValue("2026-07-02 10:00:00");
      closedRow.createCell(6).setCellValue(110);
      closedRow.createCell(7).setCellValue(800);
      closedRow.createCell(8).setCellValue(880);
      closedRow.createCell(9).setCellValue(-4);
      closedRow.createCell(11).setCellValue(-1);
      closedRow.createCell(12).setCellValue(75);

      Row totalsRow = closedSheet.createRow(3);
      totalsRow.createCell(12).setCellValue(250.0);
      totalsRow.createCell(13).setCellValue("Total");

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      xtbImportV2Service.importWorkbook(
          new ByteArrayInputStream(out.toByteArray()), "PLN_test.xlsx");
    }

    verify(assetPriceHistoryRepository)
        .upsertObservedPrice(
            77L,
            LocalDate.of(2026, 7, 10),
            "XTB_TRADE_OPEN",
            "AAPL.US",
            "AAPL.US",
            "XTB_TRADE_OPEN",
            "USD",
            BigDecimal.valueOf(102.0),
            90,
            "XTB_TRADE_OPEN_OBSERVATION");
    verify(closedPositionRepository).saveAll(closedPositionsCaptor.capture());
    List<ClosedPosition> savedClosed = new ArrayList<>();
    closedPositionsCaptor.getValue().forEach(savedClosed::add);
    assertEquals(1, savedClosed.size(), "Footer row must not become a position");
    assertEquals(CurrencyType.USD, savedClosed.getFirst().getPriceCurrency());
    assertEquals(CurrencyType.PLN, savedClosed.getFirst().getCostCurrency());
    assertEquals(CurrencyType.PLN, savedClosed.getFirst().getProfitCurrency());
    assertEquals(CurrencyType.PLN, savedClosed.getFirst().getCommissionCurrency());
    assertEquals(PositionSettlementModel.CASH_SETTLED, savedClosed.getFirst().getSettlementModel());

    verify(openedPositionRepository, atLeastOnce()).saveAll(openedPositionsCaptor.capture());
    List<OpenedPosition> savedOpened = new ArrayList<>();
    for (Iterable<OpenedPosition> batch : openedPositionsCaptor.getAllValues()) {
      for (OpenedPosition position : batch) {
        savedOpened.add(position);
      }
    }
    assertTrue(
        savedOpened.stream().allMatch(position -> position.getPriceCurrency() == CurrencyType.USD));
    assertTrue(
        savedOpened.stream().allMatch(position -> position.getCostCurrency() == CurrencyType.USD));
    assertTrue(
        savedOpened.stream()
            .allMatch(position -> position.getProfitCurrency() == CurrencyType.PLN));
    assertTrue(
        savedOpened.stream()
            .allMatch(position -> position.getCommissionCurrency() == CurrencyType.PLN));
  }

  @Test
  void importWorkbook_keepsAssetMarkedExcludedFromImport() throws Exception {
    Asset excluded =
        Asset.builder()
            .id(78L)
            .name("AIGI")
            .symbol("AIGI.UK")
            .ticker("AIGI")
            .ibrk("AIGI")
            .yahoo("AIGI.L")
            .country("UK")
            .currency(CurrencyType.USD)
            .assetType("ETF")
            .active(true)
            .excludeFromImport(true)
            .build();
    org.mockito.Mockito.when(
            assetRepository.findAllBySymbolIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(excluded));

    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      XSSFSheet cashSheet = workbook.createSheet("Cash Operations");
      Row account = cashSheet.createRow(0);
      account.createCell(0).setCellValue("Account number");
      account.createCell(1).setCellValue("51707603");
      Row header = cashSheet.createRow(1);
      header.createCell(0).setCellValue("ID");
      header.createCell(1).setCellValue("Type");
      header.createCell(2).setCellValue("Ticker");
      header.createCell(3).setCellValue("Time");
      header.createCell(4).setCellValue("Amount");
      header.createCell(5).setCellValue("Comment");
      Row data = cashSheet.createRow(2);
      data.createCell(0).setCellValue(1);
      data.createCell(1).setCellValue("Stock purchase");
      data.createCell(2).setCellValue("AIGI.UK");
      data.createCell(3).setCellValue("2026-07-10 10:00:00");
      data.createCell(4).setCellValue(-1000);
      data.createCell(5).setCellValue("OPEN BUY 10 @ 100");

      XSSFSheet closedSheet = workbook.createSheet("Closed Positions");
      Row closedAccount = closedSheet.createRow(0);
      closedAccount.createCell(0).setCellValue("Account");
      closedAccount.createCell(1).setCellValue("51707603");
      Row closedHeader = closedSheet.createRow(1);
      closedHeader.createCell(0).setCellValue("Ticker");
      closedHeader.createCell(1).setCellValue("Type");
      closedHeader.createCell(2).setCellValue("Volume");

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      xtbImportV2Service.importWorkbook(
          new ByteArrayInputStream(out.toByteArray()), "PLN_test.xlsx");
    }

    verify(cashOperationRepository).saveAll(anyList());
    verify(openedPositionRepository, atLeastOnce()).saveAll(anyList());
    verify(assetPriceHistoryRepository)
        .upsertObservedPrice(
            org.mockito.ArgumentMatchers.eq(78L),
            org.mockito.ArgumentMatchers.any(LocalDate.class),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(BigDecimal.class),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void importWorkbook_preservesIdenticalPartialCloseRowsAndBrokerCurrencyEvidence()
      throws Exception {
    Asset asset =
        Asset.builder()
            .id(12851L)
            .name("L&G Clean Energy UCITS ETF")
            .symbol("NCLR.UK")
            .ticker("NCLR")
            .ibrk("NCLR")
            .yahoo("NCLR.L")
            .country("UK")
            .currency(CurrencyType.USD)
            .assetType("ETF")
            .active(true)
            .build();
    org.mockito.Mockito.when(
            assetRepository.findAllBySymbolIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(asset));

    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      XSSFSheet cashSheet = workbook.createSheet("Cash Operations");
      Row cashAccount = cashSheet.createRow(0);
      cashAccount.createCell(0).setCellValue("Account number");
      cashAccount.createCell(1).setCellValue("51729109");
      Row cashHeader = cashSheet.createRow(1);
      cashHeader.createCell(0).setCellValue("ID");
      cashHeader.createCell(1).setCellValue("Type");
      cashHeader.createCell(2).setCellValue("Ticker");
      cashHeader.createCell(3).setCellValue("Time");
      cashHeader.createCell(4).setCellValue("Amount");
      cashHeader.createCell(5).setCellValue("Comment");

      XSSFSheet closedSheet = workbook.createSheet("Closed Positions");
      Row closedAccount = closedSheet.createRow(0);
      closedAccount.createCell(0).setCellValue("Account");
      closedAccount.createCell(1).setCellValue("51729109");
      String[] headers = {
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
      Row closedHeader = closedSheet.createRow(1);
      for (int index = 0; index < headers.length; index++) {
        closedHeader.createCell(index).setCellValue(headers[index]);
      }
      for (int occurrence = 0; occurrence < 4; occurrence++) {
        Row row = closedSheet.createRow(occurrence + 2);
        row.createCell(0).setCellValue("NCLR.UK");
        row.createCell(1).setCellValue("BUY");
        row.createCell(2).setCellValue(1);
        row.createCell(3).setCellValue("2026-01-01 10:00:00");
        row.createCell(4).setCellValue(50);
        row.createCell(5).setCellValue(3.5);
        row.createCell(6).setCellValue("2026-02-05 10:00:00");
        row.createCell(7).setCellValue(60);
        row.createCell(8).setCellValue(3.6);
        row.createCell(9).setCellValue(175);
        row.createCell(10).setCellValue(216);
        row.createCell(11).setCellValue(0);
        row.createCell(14).setCellValue(41);
        row.createCell(15).setCellValue("IKE");
        row.createCell(16).setCellValue(2317002593L);
      }

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      workbook.write(output);
      xtbImportV2Service.importWorkbook(
          new ByteArrayInputStream(output.toByteArray()), "PLN_repeated_rows.xlsx");
    }

    verify(closedPositionRepository).saveAll(closedPositionsCaptor.capture());
    List<ClosedPosition> positions = new ArrayList<>();
    closedPositionsCaptor.getValue().forEach(positions::add);

    assertEquals(4, positions.size());
    assertEquals(4, positions.stream().map(ClosedPosition::getId).distinct().count());
    assertEquals(
        List.of(1, 2, 3, 4),
        positions.stream().map(ClosedPosition::getSourceRowOccurrence).toList());
    assertTrue(
        positions.stream()
            .allMatch(position -> "2317002593".equals(position.getSourcePositionId())));
    assertTrue(
        positions.stream().allMatch(position -> position.getPriceCurrency() == CurrencyType.USD));
    assertTrue(
        positions.stream().allMatch(position -> position.getCostCurrency() == CurrencyType.PLN));
  }

  private static CashOperation cashOperation(
      Long id, Long account, CashOperationType type, String symbol, String comment) {
    CashOperation operation = new CashOperation();
    operation.setId(id);
    operation.setAccount(account);
    operation.setType(type);
    operation.setSymbol(symbol);
    operation.setComment(comment);
    operation.setDate(ZonedDateTime.parse("2026-07-15T00:00:00Z"));
    return operation;
  }

  private byte[] firstXlsx(InputStream zipInputStream) throws Exception {
    try (ZipInputStream zip = new ZipInputStream(zipInputStream)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".xlsx")) {
          continue;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
        return out.toByteArray();
      }
    }
    throw new IllegalStateException("No .xlsx entry found in fixture zip");
  }
}
