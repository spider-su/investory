package com.smartbox.investory.shared.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Utility")
class UtilityTest {
  @DisplayName("normalizes Null Big Decimal To Zero")
  @Test
  void normalizesNullBigDecimalToZero() {
    assertEquals(BigDecimal.ZERO, BigDecimalUtils.zeroIfNull(null));
    assertEquals(new BigDecimal("2.50"), BigDecimalUtils.zeroIfNull(new BigDecimal("2.50")));
  }

  @DisplayName("normalizes Nullable Collections")
  @Test
  void normalizesNullableCollections() {
    assertTrue(CollectionUtils.isEmpty(null));
    assertTrue(CollectionUtils.isEmpty(List.of()));
    assertEquals(List.of("a"), CollectionUtils.immutableListOrEmpty(List.of("a")));
    assertEquals(List.of(), CollectionUtils.immutableListOrEmpty(null));
    assertEquals(Map.of("a", 1), CollectionUtils.immutableMapOrEmpty(Map.of("a", 1)));
    assertEquals(Map.of(), CollectionUtils.immutableMapOrEmpty(null));
    assertEquals(Set.of("a"), CollectionUtils.immutableSetOrEmpty(List.of("a")));
    assertEquals(Set.of(), CollectionUtils.immutableSetOrEmpty(null));
  }

  @Test
  void normalizesNullTextToEmpty() {
    assertEquals("", StringUtils.nullToEmpty(null));
    assertEquals("value", StringUtils.nullToEmpty("value"));
  }

  @Test
  void providesIntentionalSharedBlankTextVocabulary() {
    assertTrue(StringUtils.isBlank(null));
    assertTrue(StringUtils.isBlank("  "));
  }
}
