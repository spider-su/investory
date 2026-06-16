package com.example.demo.services.imports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportExecutionResultTest {

    @Test
    void unknownSuccess_returnsZeroRowsWithMessage() {
        ImportExecutionResult result = ImportExecutionResult.unknownSuccess();

        assertEquals(0, result.rowsTotal());
        assertEquals(0, result.rowsApplied());
        assertEquals(0, result.rowsFailed());
        assertEquals("Imported successfully", result.details());
    }

    @Test
    void recordPreservesAllValues() {
        ImportExecutionResult result = new ImportExecutionResult(10, 9, 1, "ok");
        assertEquals(10, result.rowsTotal());
        assertEquals(9, result.rowsApplied());
        assertEquals(1, result.rowsFailed());
        assertEquals("ok", result.details());
    }
}

