package com.smartbox.investory.shared.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class UtilityTest {
  @Test
  void normalizesNullBigDecimalToZero() {
    assertEquals(BigDecimal.ZERO, BigDecimalUtils.zeroIfNull(null));
    assertEquals(new BigDecimal("2.50"), BigDecimalUtils.zeroIfNull(new BigDecimal("2.50")));
  }

  @Test
  void handlesNullAndBlankText() {
    assertTrue(StringUtils.isBlank(null));
    assertTrue(StringUtils.isBlank("  "));
    assertFalse(StringUtils.isBlank("value"));
  }

  @Test
  void normalizesNullableCollections() {
    assertTrue(CollectionUtils.isEmpty(null));
    assertTrue(CollectionUtils.isEmpty(List.of()));
    assertEquals(List.of("a"), CollectionUtils.immutableListOrEmpty(List.of("a")));
    assertEquals(List.of(), CollectionUtils.immutableListOrEmpty(null));
  }
}
