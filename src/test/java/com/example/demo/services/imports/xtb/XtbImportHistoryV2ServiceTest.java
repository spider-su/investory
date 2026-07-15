package com.example.demo.services.imports.xtb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.services.AssetCatalogService;
import com.example.demo.services.imports.ImportExecutionResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XtbImportHistoryV2ServiceTest {

  @Mock private ClosedPositionRepository closedPositionRepository;
  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private CashOperationRepository cashOperationRepository;
  @Mock private AssetRepository assetRepository;

  private XtbImportV2Service xtbImportV2Service;

  @BeforeEach
  void setUp() {
    xtbImportV2Service =
        new XtbImportV2Service(
            closedPositionRepository,
            openedPositionRepository,
            cashOperationRepository,
            new AssetCatalogService(assetRepository));
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllBySymbolIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(assetRepository.findAllByTickerIn(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of());
  }

  @Test
  void importZip_parsesNewXtbBundleAndReconstructsOpenPositions() throws Exception {
    try (InputStream inputStream =
        getClass()
            .getResourceAsStream(
                "/50290466_51499241_51548444_51993106_2006-01-01_2026-07-05.zip")) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          inputStream != null, "Fixture zip not on classpath; skipping");

      ImportExecutionResult result =
          xtbImportV2Service.importZip(
              inputStream, "50290466_51499241_51548444_51993106_2006-01-01_2026-07-05.zip");

      assertTrue(result.rowsApplied() > 0);
      assertTrue(result.details().contains("imported 5 workbook"));
    }

    verify(cashOperationRepository, atLeastOnce()).saveAll(anyList());
    verify(closedPositionRepository, atLeastOnce()).saveAll(anyList());
    verify(openedPositionRepository, atLeastOnce()).saveAll(anyList());

    ArgumentCaptor<Iterable<OpenedPosition>> openedCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(openedPositionRepository, atLeastOnce()).saveAll(openedCaptor.capture());

    List<OpenedPosition> allOpened = new ArrayList<>();
    for (Iterable<OpenedPosition> batch : openedCaptor.getAllValues()) {
      for (OpenedPosition position : batch) {
        allOpened.add(position);
      }
    }

    assertFalse(allOpened.isEmpty(), "Open positions should be reconstructed from cash operations");
    assertTrue(allOpened.stream().allMatch(pos -> pos.getSymbol() != null && !pos.getSymbol().isBlank()));
  }

  @Test
  void importZip_parsesSecondXtbBundle() throws Exception {
    try (InputStream inputStream =
        getClass()
            .getResourceAsStream(
                "/51707603_51747407_51822121_53582946_2024-06-30_2026-07-15.zip")) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          inputStream != null, "Fixture zip not on classpath; skipping");

      ImportExecutionResult result =
          xtbImportV2Service.importZip(
              inputStream, "51707603_51747407_51822121_53582946_2024-06-30_2026-07-15.zip");

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
                "/50290466_51499241_51548444_51993106_2006-01-01_2026-07-05.zip")) {
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
        cashOperation(1L, account, CashOperationType.STOCK_PURCHASE, "AAPL.US", "OPEN BUY 10 @ 100");
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



