package com.example.demo.services.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ImportHistoryExecutionResultTest {

  @Test
  void recordPreservesAllValues() {
    ImportExecutionResult result = new ImportExecutionResult(10, 9, 1, "ok");
    assertEquals(10, result.rowsTotal());
    assertEquals(9, result.rowsApplied());
    assertEquals(1, result.rowsFailed());
    assertEquals("ok", result.details());
  }

  @Test
  void rejectsCountersThatCannotDescribeSourceRows() {
    assertThrows(
        IllegalArgumentException.class, () -> new ImportExecutionResult(10, 9, 2, "invalid"));
    assertThrows(
        IllegalArgumentException.class, () -> new ImportExecutionResult(-1, 0, 0, "invalid"));
  }
}
