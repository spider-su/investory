package com.smartbox.investory.investment.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.imports.ibkr.IbkrBrokerImportParser;
import com.smartbox.investory.investment.imports.ibkr.IbkrImportService;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Ibkr Broker Import History Parser")
class IbkrBrokerImportHistoryParserTest {

  @Mock private IbkrImportService ibkrImportService;

  @InjectMocks private IbkrBrokerImportParser parser;

  @DisplayName("broker Type is Ibkr")
  @Test
  void brokerType_isIbkr() {
    assertEquals(BrokerType.IBKR, parser.brokerType());
  }

  @DisplayName("import File delegates And Returns Service Result")
  @Test
  void importFile_delegatesAndReturnsServiceResult() throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1});
    ImportExecutionResult expected = new ImportExecutionResult(5, 5, 0, "ok");
    when(ibkrImportService.importStatement(in, "ibkr.csv")).thenReturn(expected);

    ImportExecutionResult result = parser.importFile(in, "ibkr.csv");

    assertSame(expected, result);
  }
}
