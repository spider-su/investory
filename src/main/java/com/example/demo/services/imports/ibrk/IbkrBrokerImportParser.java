package com.example.demo.services.imports.ibrk;

import com.example.demo.infrastructure.BrokerType;
import com.example.demo.services.imports.BrokerImportParser;
import com.example.demo.services.imports.ImportExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class IbkrBrokerImportParser implements BrokerImportParser {

    private final IbkrImportService ibkrImportService;

    @Override
    public BrokerType brokerType() {
        return BrokerType.IBKR;
    }

    @Override
    public ImportExecutionResult importFile(InputStream inputStream, String fileName) throws Exception {
        return ibkrImportService.importStatement(inputStream);
    }
}

