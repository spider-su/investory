package com.smartbox.investory.investment.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Import History Execution Result")
class ImportHistoryExecutionResultTest {

  @DisplayName("record Preserves All Values")
  @Test
  void recordPreservesAllValues() {
    ImportExecutionResult result = new ImportExecutionResult(10, 9, 1, "ok", Set.of(11L, 12L));
    assertEquals(10, result.rowsTotal());
    assertEquals(9, result.rowsApplied());
    assertEquals(1, result.rowsFailed());
    assertEquals("ok", result.details());
    assertEquals(Set.of(11L, 12L), result.affectedAccountIds());
  }

  @DisplayName("copies Affected Account IDs Immutably")
  @Test
  void copiesAffectedAccountIdsImmutably() {
    Set<Long> ids = new HashSet<>(Set.of(11L));
    ImportExecutionResult result = new ImportExecutionResult(1, 1, 0, "ok", ids);
    ids.add(12L);
    assertEquals(Set.of(11L), result.affectedAccountIds());
    assertThrows(UnsupportedOperationException.class, () -> result.affectedAccountIds().add(12L));
  }

  @DisplayName("rejects Counters That Cannot Describe Source Rows")
  @Test
  void rejectsCountersThatCannotDescribeSourceRows() {
    assertThrows(
        IllegalArgumentException.class, () -> new ImportExecutionResult(10, 9, 2, "invalid"));
    assertThrows(
        IllegalArgumentException.class, () -> new ImportExecutionResult(-1, 0, 0, "invalid"));
  }
}
