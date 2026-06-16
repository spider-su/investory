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
import static org.mockito.Mockito.verify;

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
    void importFile_delegatesToService_andReturnsUnknownSuccess() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[]{1, 2, 3});

        ImportExecutionResult result = parser.importFile(in, "file.xlsx");

        verify(xtbImportService).importXtbExport(in);
        assertEquals(0, result.rowsTotal());
        assertEquals(0, result.rowsApplied());
        assertEquals("Imported successfully", result.details());
    }
}

