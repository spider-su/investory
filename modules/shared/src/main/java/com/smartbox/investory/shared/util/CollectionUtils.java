package com.smartbox.investory.shared.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CollectionUtils {
  private CollectionUtils() {}

  public static boolean isEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }

  public static <T> List<T> immutableListOrEmpty(Collection<? extends T> collection) {
    return collection == null ? List.of() : List.copyOf(collection);
  }

  public static <K, V> Map<K, V> immutableMapOrEmpty(Map<? extends K, ? extends V> map) {
    return map == null ? Map.of() : Map.copyOf(map);
  }

  public static <T> Set<T> immutableSetOrEmpty(Collection<? extends T> collection) {
    return collection == null ? Set.of() : Set.copyOf(collection);
  }
}
