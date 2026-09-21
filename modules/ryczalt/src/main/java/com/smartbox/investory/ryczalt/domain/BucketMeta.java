package com.smartbox.investory.ryczalt.domain;

/** Generic summary metadata for a bucket. */
public record BucketMeta(int count) {
  public BucketMeta {
    if (count < 0) {
      throw new IllegalArgumentException("Bucket count cannot be negative");
    }
  }
}
