package com.example.demo.services.imports;

public record ImportExecutionResult(int rowsTotal, int rowsApplied, int rowsFailed, String details) {

    public static ImportExecutionResult unknownSuccess() {
        return new ImportExecutionResult(0, 0, 0, "Imported successfully");
    }
}

