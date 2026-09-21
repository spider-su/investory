package com.smartbox.investory.ryczalt.domain;

import java.util.List;
import java.util.Objects;

/** Immutable items with derived summary metadata. */
public record Bucket<T>(List<T> items, BucketMeta meta) {
  public Bucket {
    items = List.copyOf(Objects.requireNonNull(items, "items"));
    meta = Objects.requireNonNull(meta, "meta");
    if (meta.count() != items.size()) {
      throw new IllegalArgumentException("Bucket count must match item count");
    }
  }

  public static <T> Bucket<T> of(List<T> items) {
    Objects.requireNonNull(items, "items");
    return new Bucket<>(items, new BucketMeta(items.size()));
  }

  public static <T> Bucket<T> empty() {
    return of(List.of());
  }
}
