package com.smartbox.investory.investment.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.imports.xtb.XtbBrokerImportParser;
import com.smartbox.investory.investment.imports.xtb.XtbImportService;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Xtb Broker Import History Parser")
class XtbBrokerImportHistoryParserTest {

  @Mock private XtbImportService xtbImportService;

  @InjectMocks private XtbBrokerImportParser parser;

  @DisplayName("broker Type is Xtb")
  @Test
  void brokerType_isXtb() {
    assertEquals(BrokerType.XTB, parser.brokerType());
  }

  @DisplayName("import File delegates Zip To V2Service")
  @Test
  void importFile_delegatesZipToV2Service() throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
    ImportExecutionResult expected = new ImportExecutionResult(42, 42, 0, "XTB v2 zip");
    when(xtbImportService.isZipReport("report.zip")).thenReturn(true);
    when(xtbImportService.importZip(any(), anyString())).thenReturn(expected);

    ImportExecutionResult result = parser.importFile(in, "report.zip");

    assertSame(expected, result);
  }

  @DisplayName("import File delegates New Xlsx To V2Service")
  @Test
  void importFile_delegatesNewXlsxToV2Service() throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
    ImportExecutionResult expected = new ImportExecutionResult(21, 21, 0, "acc=51499241 ...");
    when(xtbImportService.isZipReport("report.xlsx")).thenReturn(false);
    when(xtbImportService.supports(any())).thenReturn(true);
    when(xtbImportService.importWorkbook(any(), anyString())).thenReturn(expected);

    ImportExecutionResult result = parser.importFile(in, "report.xlsx");

    assertSame(expected, result);
  }

  @DisplayName("import File rejects Legacy Workbook")
  @Test
  void importFile_rejectsLegacyWorkbook() {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
    when(xtbImportService.isZipReport("legacy.xlsx")).thenReturn(false);
    when(xtbImportService.supports(any())).thenReturn(false);

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> parser.importFile(in, "legacy.xlsx"));
    assertEquals(
        "Unsupported XTB statement format for V2 importer: legacy.xlsx", error.getMessage());
  }
}
