package com.smartbox.investory.integrations.importing.ibkr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import com.smartbox.investory.investment.port.importing.BrokerImportResult;
import com.smartbox.investory.investment.port.importing.IbkrImportPort;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IbkrBrokerImportHistoryParserTest {
  @Mock private IbkrImportPort ibkrImportPort;
  @InjectMocks private IbkrBrokerImportParser parser;

  @Test
  void brokerType_isIbkr() {
    assertEquals(ImportBroker.IBKR, parser.brokerType());
  }

  @Test
  void importFile_delegatesAndReturnsServiceResult() throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1});
    BrokerImportResult expected = new BrokerImportResult(5, 5, 0, "ok");
    when(ibkrImportPort.importStatement(in, "ibkr.csv")).thenReturn(expected);

    assertSame(expected, parser.importFile(in, "ibkr.csv"));
  }
}
