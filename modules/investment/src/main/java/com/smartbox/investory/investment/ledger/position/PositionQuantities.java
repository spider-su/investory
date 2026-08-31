package com.smartbox.investory.investment.ledger.position;

/** Canonical quantity semantics shared by position valuation code. */
public final class PositionQuantities {

  private PositionQuantities() {}

  public static double signed(PositionType type, Double storedVolume) {
    double absolute = storedVolume == null ? 0.0 : Math.abs(storedVolume);
    return type == PositionType.SELL ? -absolute : absolute;
  }
}
