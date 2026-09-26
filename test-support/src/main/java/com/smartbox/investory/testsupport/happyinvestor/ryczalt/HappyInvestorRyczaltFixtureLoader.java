package com.smartbox.investory.testsupport.happyinvestor.ryczalt;

public final class HappyInvestorRyczaltFixtureLoader {
  private HappyInvestorRyczaltFixtureLoader() {}

  public static HappyInvestorRyczaltFixture load() {
    return new HappyInvestorRyczaltFixture(HappyInvestorRyczalt2026SourceFacts.load());
  }
}
