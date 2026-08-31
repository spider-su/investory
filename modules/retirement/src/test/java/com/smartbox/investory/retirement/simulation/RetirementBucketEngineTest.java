package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RetirementBucketEngineTest {
  private final RetirementBucketEngine engine = new RetirementBucketEngine();

  @Test
  void spendsCashFirstAndDoesNotRefillIt() {
    var r =
        engine.simulate(
            buckets("100", "200", "300", "400", "0.10", "0.08"),
            bd("50"),
            BigDecimal.ZERO,
            policy());
    assertThat(r.buckets().get(BucketType.CASH).withdrawal()).isEqualByComparingTo("50");
    assertThat(r.buckets().get(BucketType.BONDS).withdrawal()).isZero();
    assertThat(r.buckets().get(BucketType.CASH).expectedEndValue()).isEqualByComparingTo("50");
  }

  @Test
  void usesBondsThenEquitiesThenRealEstate() {
    var r =
        engine.simulate(
            buckets("50", "100", "80", "100", "0", "0"), bd("300"), BigDecimal.ZERO, policy());
    assertThat(r.buckets().get(BucketType.CASH).expectedEndValue()).isZero();
    assertThat(r.buckets().get(BucketType.BONDS).expectedEndValue()).isZero();
    assertThat(r.buckets().get(BucketType.EQUITIES).expectedEndValue()).isZero();
    assertThat(r.buckets().get(BucketType.REAL_ESTATE).withdrawal()).isEqualByComparingTo("70");
    assertThat(r.unfunded()).isZero();
  }

  @Test
  void realEstateIsUntouchedUntilAllLiquidBucketsAreExhausted() {
    var exactlyLiquid =
        engine.simulate(
            buckets("10", "20", "30", "40", "0", "0"), bd("60"), BigDecimal.ZERO, policy());
    assertThat(exactlyLiquid.buckets().get(BucketType.CASH).expectedEndValue()).isZero();
    assertThat(exactlyLiquid.buckets().get(BucketType.BONDS).expectedEndValue()).isZero();
    assertThat(exactlyLiquid.buckets().get(BucketType.EQUITIES).expectedEndValue()).isZero();
    assertThat(exactlyLiquid.buckets().get(BucketType.REAL_ESTATE).withdrawal()).isZero();
    assertThat(exactlyLiquid.buckets().get(BucketType.REAL_ESTATE).expectedEndValue())
        .isEqualByComparingTo("40");

    var needsRealEstate =
        engine.simulate(
            buckets("10", "20", "30", "40", "0", "0"), bd("61"), BigDecimal.ZERO, policy());
    assertThat(needsRealEstate.buckets().get(BucketType.REAL_ESTATE).withdrawal())
        .isEqualByComparingTo("1");
    assertThat(needsRealEstate.buckets().get(BucketType.REAL_ESTATE).expectedEndValue())
        .isEqualByComparingTo("39");
  }

  @Test
  void exhaustsRealEstateBeforeReportingUnfundedSpending() {
    var r =
        engine.simulate(
            buckets("10", "20", "30", "10", "0", "0"), bd("75"), BigDecimal.ZERO, policy());

    assertThat(r.buckets().get(BucketType.CASH).expectedEndValue()).isZero();
    assertThat(r.buckets().get(BucketType.BONDS).expectedEndValue()).isZero();
    assertThat(r.buckets().get(BucketType.EQUITIES).expectedEndValue()).isZero();
    assertThat(r.buckets().get(BucketType.REAL_ESTATE).expectedEndValue()).isZero();
    assertThat(r.buckets().get(BucketType.REAL_ESTATE).withdrawal()).isEqualByComparingTo("10");
    assertThat(r.unfunded()).isEqualByComparingTo("5");
  }

  @Test
  void bondReturnCarriesAndEquityProfitRefillsOnlyToTarget() {
    var r =
        engine.simulate(
            buckets("0", "100", "1000", "0", "0.10", "0.10"), bd("0"), BigDecimal.ZERO, policy());
    assertThat(r.buckets().get(BucketType.BONDS).returnAmount()).isEqualByComparingTo("10");
    assertThat(r.buckets().get(BucketType.BONDS).refill()).isEqualByComparingTo("0");
    assertThat(r.buckets().get(BucketType.EQUITIES).expectedEndValue())
        .isEqualByComparingTo("1100");
  }

  @Test
  void annualRateSeamOverridesConstantRatesWithoutChangingFundingOrder() {
    var r =
        engine.simulate(
            buckets("0", "100", "1000", "0", "0.10", "0.10"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            policy(),
            bd("0.03"),
            bd("0.04"));

    assertThat(r.buckets().get(BucketType.BONDS).returnAmount()).isEqualByComparingTo("3");
    assertThat(r.buckets().get(BucketType.EQUITIES).returnAmount()).isEqualByComparingTo("40");
  }

  @Test
  void equityToBondTransferIsSignedAndPortfolioValueNeutral() {
    var r =
        engine.simulate(
            PlanningBuckets.of(
                bd("0"), bd("80"), bd("1000"), bd("0"), bd("0.10"), bd("0.10"), bd("100"), bd("0")),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            policy());

    var bonds = r.buckets().get(BucketType.BONDS);
    var equities = r.buckets().get(BucketType.EQUITIES);
    assertThat(bonds.transfer()).isEqualByComparingTo("12");
    assertThat(equities.transfer()).isEqualByComparingTo("-12");
    assertThat(
            r.buckets().values().stream()
                .map(RetirementBucketEngine.BucketResult::transfer)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isZero();

    BigDecimal beforeTransfer =
        bonds
            .startValue()
            .add(bonds.returnAmount())
            .add(equities.startValue())
            .add(equities.returnAmount());
    BigDecimal afterTransfer = bonds.expectedEndValue().add(equities.expectedEndValue());
    assertThat(afterTransfer).isEqualByComparingTo(beforeTransfer);
  }

  @Test
  void harvestRunsAtThresholdButNotJustBelowIt() {
    var exact =
        engine.simulate(
            PlanningBuckets.of(
                bd("0"),
                bd("80"),
                bd("1000"),
                bd("0"),
                BigDecimal.ZERO,
                bd("0.07"),
                bd("100"),
                BigDecimal.ZERO),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            policy("0.07", "1"));
    assertThat(exact.buckets().get(BucketType.BONDS).refill()).isEqualByComparingTo("20");
    assertThat(exact.buckets().get(BucketType.BONDS).expectedEndValue())
        .isEqualByComparingTo("100");

    var below =
        engine.simulate(
            PlanningBuckets.of(
                bd("0"),
                bd("80"),
                bd("1000"),
                bd("0"),
                BigDecimal.ZERO,
                bd("0.069999"),
                bd("100"),
                BigDecimal.ZERO),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            policy("0.07", "1"));
    assertThat(below.buckets().get(BucketType.BONDS).refill()).isZero();
  }

  @Test
  void harvestShareZeroAndOneRespectEligibleGainAndBondTarget() {
    var zeroShare =
        engine.simulate(
            PlanningBuckets.of(
                bd("0"),
                bd("0"),
                bd("1000"),
                bd("0"),
                BigDecimal.ZERO,
                bd("0.10"),
                bd("100"),
                BigDecimal.ZERO),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            policy("0.07", "0"));
    assertThat(zeroShare.buckets().get(BucketType.BONDS).refill()).isZero();

    var fullShare =
        engine.simulate(
            PlanningBuckets.of(
                bd("0"),
                bd("0"),
                bd("1000"),
                bd("0"),
                BigDecimal.ZERO,
                bd("0.10"),
                bd("150"),
                BigDecimal.ZERO),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            policy("0.07", "1"));
    assertThat(fullShare.buckets().get(BucketType.BONDS).refill()).isEqualByComparingTo("100");
    assertThat(fullShare.buckets().get(BucketType.BONDS).expectedEndValue())
        .isEqualByComparingTo("100");
    assertThat(fullShare.buckets().get(BucketType.EQUITIES).expectedEndValue())
        .isEqualByComparingTo("1000");
  }

  @Test
  void bondsAtTargetDoNotReceiveUnnecessaryEquityTransfer() {
    var r =
        engine.simulate(
            PlanningBuckets.of(
                bd("0"),
                bd("100"),
                bd("1000"),
                bd("0"),
                bd("0.10"),
                bd("0.10"),
                bd("100"),
                bd("0")),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            policy());

    assertThat(r.buckets().get(BucketType.BONDS).transfer()).isZero();
    assertThat(r.buckets().get(BucketType.EQUITIES).transfer()).isZero();
  }

  @Test
  void unfundedIsPerYearAndCanRecover() {
    var start = buckets("0", "0", "0", "0", "0", "0");
    assertThat(engine.simulate(start, bd("100"), BigDecimal.ZERO, policy()).unfunded())
        .isEqualByComparingTo("100");
    assertThat(
            engine
                .simulate(
                    PlanningBuckets.of(
                        bd("200"), bd("0"), bd("0"), bd("0"), bd("0"), bd("0"), bd("0"), bd("0")),
                    bd("100"),
                    BigDecimal.ZERO,
                    policy())
                .unfunded())
        .isZero();
  }

  private static PlanningBuckets buckets(
      String cash,
      String bonds,
      String equities,
      String realEstate,
      String bondRate,
      String equityRate) {
    return PlanningBuckets.of(
        bd(cash),
        bd(bonds),
        bd(equities),
        bd(realEstate),
        bd(bondRate),
        bd(equityRate),
        bd(bonds),
        BigDecimal.ZERO);
  }

  private static RetirementFundingPolicy policy() {
    return new RetirementFundingPolicy(
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, true, null);
  }

  private static RetirementFundingPolicy policy(String threshold, String share) {
    return new RetirementFundingPolicy(BigDecimal.ZERO, bd(threshold), bd(share), true, null);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
