package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RetirementBucketEngineTest {
  private final RetirementBucketEngine engine = new RetirementBucketEngine();

  @Test void spendsCashFirstAndDoesNotRefillIt() {
    var r = engine.simulate(buckets("100", "200", "300", "400", "0.10", "0.08"), bd("50"), BigDecimal.ZERO, policy());
    assertThat(r.buckets().get(BucketType.CASH).withdrawal()).isEqualByComparingTo("50");
    assertThat(r.buckets().get(BucketType.BONDS).withdrawal()).isZero();
    assertThat(r.buckets().get(BucketType.CASH).expectedEndValue()).isEqualByComparingTo("50");
  }

  @Test void usesBondsThenEquitiesThenRealEstate() {
    var r = engine.simulate(buckets("50", "100", "80", "100", "0", "0"), bd("300"), BigDecimal.ZERO, policy());
    assertThat(r.buckets().get(BucketType.CASH).expectedEndValue()).isZero();
    assertThat(r.buckets().get(BucketType.BONDS).expectedEndValue()).isZero();
    assertThat(r.buckets().get(BucketType.EQUITIES).expectedEndValue()).isZero();
    assertThat(r.buckets().get(BucketType.REAL_ESTATE).withdrawal()).isEqualByComparingTo("70");
    assertThat(r.unfunded()).isZero();
  }

  @Test void bondReturnCarriesAndEquityProfitRefillsOnlyToTarget() {
    var r = engine.simulate(buckets("0", "100", "1000", "0", "0.10", "0.10"), bd("0"), BigDecimal.ZERO, policy());
    assertThat(r.buckets().get(BucketType.BONDS).returnAmount()).isEqualByComparingTo("10");
    assertThat(r.buckets().get(BucketType.BONDS).refill()).isEqualByComparingTo("0");
    assertThat(r.buckets().get(BucketType.EQUITIES).expectedEndValue()).isEqualByComparingTo("1100");
  }

  @Test void unfundedIsPerYearAndCanRecover() {
    var start = buckets("0", "0", "0", "0", "0", "0");
    assertThat(engine.simulate(start, bd("100"), BigDecimal.ZERO, policy()).unfunded()).isEqualByComparingTo("100");
    assertThat(engine.simulate(PlanningBuckets.of(bd("200"), bd("0"), bd("0"), bd("0"), bd("0"), bd("0"), bd("0"), bd("0")), bd("100"), BigDecimal.ZERO, policy()).unfunded()).isZero();
  }

  private static PlanningBuckets buckets(String cash, String bonds, String equities, String realEstate, String bondRate, String equityRate) {
    return PlanningBuckets.of(bd(cash), bd(bonds), bd(equities), bd(realEstate), bd(bondRate), bd(equityRate), bd(bonds), BigDecimal.ZERO);
  }
  private static RetirementFundingPolicy policy() {
    return new RetirementFundingPolicy(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, true, null);
  }
  private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
