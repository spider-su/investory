package com.smartbox.investory.rag;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RagConcurrencyLimiterTest {
  @Test
  void rejectsExcessWorkAndRecoversOnRelease() {
    var limiter = new RagConcurrencyLimiter(1);
    assertTrue(limiter.tryAcquire());
    assertFalse(limiter.tryAcquire());
    limiter.release();
    assertTrue(limiter.tryAcquire());
    limiter.release();
  }

  @Test
  void rejectsInvalidCapacity() {
    assertThrows(IllegalArgumentException.class, () -> new RagConcurrencyLimiter(0));
    assertThrows(IllegalArgumentException.class, () -> new RagConcurrencyLimiter(17));
  }
}
