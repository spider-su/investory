package com.smartbox.investory.rag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RagUserRateLimiterTest {
  @Test
  void limitsByUserAcrossAllPortfoliosAndResetsAfterWindow() {
    var now = new AtomicLong(100_000L);
    var limiter = new RagUserRateLimiter(2, now::get);
    assertTrue(limiter.tryAcquire("user-a"));
    assertTrue(limiter.tryAcquire("user-a"));
    assertFalse(limiter.tryAcquire("user-a"));
    assertTrue(limiter.tryAcquire("user-b"));
    assertFalse(limiter.tryAcquire(null));
    now.addAndGet(60_000L);
    assertTrue(limiter.tryAcquire("user-a"));
  }

  @Test
  void refusesInvalidLimitsAndUnauthenticatedUsers() {
    try {
      new RagUserRateLimiter(0, System::currentTimeMillis);
      throw new AssertionError("Should reject zero");
    } catch (IllegalArgumentException expected) {
      // Fail-closed configuration
    }
    assertFalse(new RagUserRateLimiter(10, System::currentTimeMillis).tryAcquire(" "));
  }
}
