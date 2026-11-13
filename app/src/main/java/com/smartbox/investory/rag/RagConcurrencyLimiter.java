package com.smartbox.investory.rag;

import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Bounds snapshot reads as well as inference; admission never waits for capacity. */
@Component
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public final class RagConcurrencyLimiter {
  private final Semaphore slots;

  public RagConcurrencyLimiter(@Value("${investory.rag.max-concurrent-requests:1}") int capacity) {
    if (capacity < 1 || capacity > 16) {
      throw new IllegalArgumentException("Invalid RAG concurrency limit");
    }
    slots = new Semaphore(capacity);
  }

  public boolean tryAcquire() {
    return slots.tryAcquire();
  }

  public void release() {
    slots.release();
  }
}
