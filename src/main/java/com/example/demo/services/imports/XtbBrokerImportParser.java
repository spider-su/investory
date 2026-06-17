package com.example.demo.services.imports;

import com.example.demo.data.BrokerType;
import com.example.demo.services.XtbImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class XtbBrokerImportParser implements BrokerImportParser {

    private final XtbImportService xtbImportService;

    @Override
    public BrokerType brokerType() {
        return BrokerType.XTB;
    }

    @Override
    public ImportExecutionResult importFile(InputStream inputStream, String fileName) throws Exception {
        return xtbImportService.importXtbExport(inputStream);
    }
}
