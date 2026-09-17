package com.smartbox.investory.testsupport.happyinvestor.accounting;

/** Loads only the independent source-fact fixture; it does not seed database tables. */
public final class HappyInvestorAccountingFixtureLoader {
  private HappyInvestorAccountingFixtureLoader() {}

  public static HappyInvestorAccountingFixture load() {
    return new HappyInvestorAccountingFixture(HappyInvestorAccounting2026Facts.load());
  }
}
