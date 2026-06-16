package com.example.demo.services.imports;

import com.example.demo.data.BrokerType;
import com.example.demo.services.IbkrImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IbkrBrokerImportParserTest {

    @Mock
    private IbkrImportService ibkrImportService;

    @InjectMocks
    private IbkrBrokerImportParser parser;

    @Test
    void brokerType_isIbkr() {
        assertEquals(BrokerType.IBKR, parser.brokerType());
    }

    @Test
    void importFile_delegatesAndReturnsServiceResult() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[]{1});
        ImportExecutionResult expected = new ImportExecutionResult(5, 5, 0, "ok");
        when(ibkrImportService.importStatement(in)).thenReturn(expected);

        ImportExecutionResult result = parser.importFile(in, "ibkr.csv");

        assertSame(expected, result);
    }
}

