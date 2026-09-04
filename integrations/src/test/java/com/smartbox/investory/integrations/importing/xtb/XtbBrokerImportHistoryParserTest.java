package com.smartbox.investory.integrations.importing.xtb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import com.smartbox.investory.investment.port.importing.BrokerImportResult;
import com.smartbox.investory.investment.port.importing.XtbImportPort;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XtbBrokerImportHistoryParserTest {
  @Mock private XtbImportPort xtbImportPort;
  @InjectMocks private XtbBrokerImportParser parser;

  @Test
  void brokerType_isXtb() {
    assertEquals(ImportBroker.XTB, parser.brokerType());
  }

  @Test
  void importFile_delegatesZipToV2Service() throws Exception {
    BrokerImportResult expected = new BrokerImportResult(42, 42, 0, "XTB v2 zip");
    when(xtbImportPort.isZipReport("report.zip")).thenReturn(true);
    when(xtbImportPort.importZip(any(), anyString())).thenReturn(expected);

    assertSame(
        expected, parser.importFile(new ByteArrayInputStream(new byte[] {1, 2, 3}), "report.zip"));
  }

  @Test
  void importFile_delegatesNewXlsxToV2Service() throws Exception {
    BrokerImportResult expected = new BrokerImportResult(21, 21, 0, "acc=51499241 ...");
    when(xtbImportPort.isZipReport("report.xlsx")).thenReturn(false);
    when(xtbImportPort.supports(any())).thenReturn(true);
    when(xtbImportPort.importWorkbook(any(), anyString())).thenReturn(expected);

    assertSame(
        expected, parser.importFile(new ByteArrayInputStream(new byte[] {1, 2, 3}), "report.xlsx"));
  }

  @Test
  void importFile_rejectsLegacyWorkbook() {
    when(xtbImportPort.isZipReport("legacy.xlsx")).thenReturn(false);
    when(xtbImportPort.supports(any())).thenReturn(false);

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.importFile(new ByteArrayInputStream(new byte[] {1, 2, 3}), "legacy.xlsx"));
    assertEquals(
        "Unsupported XTB statement format for V2 importer: legacy.xlsx", error.getMessage());
  }
}
