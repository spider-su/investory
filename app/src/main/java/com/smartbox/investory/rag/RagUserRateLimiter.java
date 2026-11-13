package com.smartbox.investory.rag;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-process fixed-window budget per authenticated principal, across all portfolios. This is a
 * single-replica v1 guard. Use a shared store before scaling Java replicas. Usernames are used only
 * as in-memory keys, never metrics labels or log fields.
 */
@Component
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public final class RagUserRateLimiter {
  private static final long WINDOW_MILLIS = 60_000L;
  private static final int MAX_PRINCIPALS = 10_000;
  private final int limit;
  private final LongSupplier millis;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  @Autowired
  public RagUserRateLimiter(@Value("${investory.rag.max-requests-per-minute:10}") int limit) {
    this(limit, System::currentTimeMillis);
  }

  RagUserRateLimiter(int limit, LongSupplier millis) {
    if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Invalid RAG rate limit");
    this.limit = limit;
    this.millis = millis;
  }

  public boolean tryAcquire(String principal) {
    if (principal == null || principal.isBlank()) return false;
    long now = millis.getAsLong();
    if (!windows.containsKey(principal) && windows.size() >= MAX_PRINCIPALS) {
      windows.entrySet().removeIf(e -> now - e.getValue().startedAt() >= WINDOW_MILLIS);
      if (windows.size() >= MAX_PRINCIPALS) return false;
    }
    AtomicBoolean allowed = new AtomicBoolean(false);
    windows.compute(
        principal,
        (key, old) -> {
          if (old == null || now < old.startedAt() || now - old.startedAt() >= WINDOW_MILLIS) {
            allowed.set(true);
            return new Window(now, 1);
          }
          if (old.count() < limit) {
            allowed.set(true);
            return new Window(old.startedAt(), old.count() + 1);
          }
          return old;
        });
    return allowed.get();
  }

  private record Window(long startedAt, int count) {}
}
