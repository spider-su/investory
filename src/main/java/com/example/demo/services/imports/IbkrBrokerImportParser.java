package com.example.demo.services.imports;

import com.example.demo.data.BrokerType;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class IbkrBrokerImportParser implements BrokerImportParser {

    @Override
    public BrokerType brokerType() {
        return BrokerType.IBKR;
    }

    @Override
    public ImportExecutionResult importFile(InputStream inputStream, String fileName) {
        throw new UnsupportedOperationException("IBKR parser is not implemented yet");
    }
}

