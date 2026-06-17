package com.example.demo.services.imports;

import com.example.demo.infrastructure.BrokerType;

import java.io.InputStream;

public interface BrokerImportParser {
    BrokerType brokerType();

    ImportExecutionResult importFile(InputStream inputStream, String fileName) throws Exception;
}

