package com.smartbox.investory.investment.imports;

/** Portfolio scope propagated through broker parser calls and account resolution. */
public final class ImportPortfolioContext {
  private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

  private ImportPortfolioContext() {}

  public static Scope open(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
    Long previous = CURRENT.get();
    CURRENT.set(portfolioId);
    return () -> {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    };
  }

  public static Long current() {
    return CURRENT.get();
  }

  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
