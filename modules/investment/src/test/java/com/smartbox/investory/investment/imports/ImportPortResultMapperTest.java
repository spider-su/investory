package com.smartbox.investory.investment.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ImportPortResultMapperTest {
  @Test
  void mapsCountsDetailsAndAffectedAccountIds() {
    Set<Long> affectedAccountIds = new HashSet<>(Set.of(101L, 202L));
    ImportExecutionResult executionResult =
        new ImportExecutionResult(12, 10, 2, "two rejected rows", affectedAccountIds);

    var portResult = ImportPortResultMapper.toPortResult(executionResult);
    affectedAccountIds.add(303L);

    assertEquals(12, portResult.rowsTotal());
    assertEquals(10, portResult.rowsApplied());
    assertEquals(2, portResult.rowsFailed());
    assertEquals("two rejected rows", portResult.details());
    assertEquals(Set.of(101L, 202L), portResult.affectedAccountIds());
    assertThrows(
        UnsupportedOperationException.class, () -> portResult.affectedAccountIds().add(303L));
  }
}
