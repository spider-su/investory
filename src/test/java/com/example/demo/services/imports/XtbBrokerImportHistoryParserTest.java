package com.example.demo.services.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.BrokerType;
import com.example.demo.services.imports.xtb.XtbBrokerImportParser;
import com.example.demo.services.imports.xtb.XtbImportV2Service;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XtbBrokerImportHistoryParserTest {

  @Mock private XtbImportV2Service xtbImportV2Service;

  @InjectMocks private XtbBrokerImportParser parser;

  @Test
  void brokerType_isXtb() {
    assertEquals(BrokerType.XTB, parser.brokerType());
  }

  @Test
  void importFile_delegatesZipToV2Service() throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
    ImportExecutionResult expected = new ImportExecutionResult(42, 42, 0, "XTB v2 zip");
    when(xtbImportV2Service.isZipReport("report.zip")).thenReturn(true);
    when(xtbImportV2Service.importZip(any(), anyString())).thenReturn(expected);

    ImportExecutionResult result = parser.importFile(in, "report.zip");

    assertSame(expected, result);
  }

  @Test
  void importFile_delegatesNewXlsxToV2Service() throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
    ImportExecutionResult expected = new ImportExecutionResult(21, 21, 0, "acc=51499241 ...");
    when(xtbImportV2Service.isZipReport("report.xlsx")).thenReturn(false);
    when(xtbImportV2Service.supports(any())).thenReturn(true);
    when(xtbImportV2Service.importWorkbook(any(), anyString())).thenReturn(expected);

    ImportExecutionResult result = parser.importFile(in, "report.xlsx");

    assertSame(expected, result);
  }

  @Test
  void importFile_rejectsLegacyWorkbook() {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
    when(xtbImportV2Service.isZipReport("legacy.xlsx")).thenReturn(false);
    when(xtbImportV2Service.supports(any())).thenReturn(false);

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> parser.importFile(in, "legacy.xlsx"));
    assertEquals(
        "Unsupported XTB statement format for V2 importer: legacy.xlsx", error.getMessage());
  }
}
