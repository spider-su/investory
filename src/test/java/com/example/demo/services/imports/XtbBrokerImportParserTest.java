package com.example.demo.services.imports;

import com.example.demo.data.BrokerType;
import com.example.demo.services.XtbImportService;
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
class XtbBrokerImportParserTest {

    @Mock
    private XtbImportService xtbImportService;

    @InjectMocks
    private XtbBrokerImportParser parser;

    @Test
    void brokerType_isXtb() {
        assertEquals(BrokerType.XTB, parser.brokerType());
    }

    @Test
    void importFile_delegatesAndPropagatesServiceCounts() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[]{1, 2, 3});
        ImportExecutionResult expected = new ImportExecutionResult(42, 42, 0, "XTB 51499241: ...");
        when(xtbImportService.importXtbExport(in)).thenReturn(expected);

        ImportExecutionResult result = parser.importFile(in, "file.xlsx");

        assertSame(expected, result);
    }
}
