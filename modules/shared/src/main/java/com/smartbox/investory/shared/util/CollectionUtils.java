package com.smartbox.investory.shared.util;

import java.util.Collection;
import java.util.List;

public final class CollectionUtils {
  private CollectionUtils() {}

  public static boolean isEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }

  public static <T> List<T> immutableListOrEmpty(Collection<? extends T> collection) {
    return collection == null ? List.of() : List.copyOf(collection);
  }
}
