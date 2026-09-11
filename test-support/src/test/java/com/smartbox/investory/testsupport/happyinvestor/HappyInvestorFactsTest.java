package com.smartbox.investory.testsupport.happyinvestor;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Independent arithmetic checkpoints for canonical source and boundary facts. */
class HappyInvestorFactsTest {
  @Test
  void longTermTotalsIncludeBothCanonicalCashReserves() {
    assertThat(HappyInvestorLongTermFacts.CASH_RESERVE_TOTAL)
        .isEqualByComparingTo(
            HappyInvestorLongTermFacts.PLAIN_CASH_RESERVE_PRINCIPAL.add(
                HappyInvestorLongTermFacts.INTEREST_BEARING_RESERVE_PRINCIPAL));
    assertThat(HappyInvestorLongTermFacts.LONG_TERM_TOTAL)
        .isEqualByComparingTo(
            HappyInvestorLongTermFacts.REAL_ESTATE_TOTAL
                .add(HappyInvestorLongTermFacts.BOND_TOTAL)
                .add(HappyInvestorLongTermFacts.CASH_RESERVE_TOTAL)
                .add(HappyInvestorLongTermFacts.PERSONAL_ASSET_TOTAL));
  }

  @Test
  void documentsCalendarAndBoundaryRentalCalculations() {
    BigDecimal calendar2025 =
        new BigDecimal("3200")
            .multiply(new BigDecimal("12"))
            .add(new BigDecimal("2800").multiply(new BigDecimal("6")))
            .add(new BigDecimal("3000").multiply(new BigDecimal("6")));
    BigDecimal boundaryAnnualized =
        new BigDecimal("3200").add(new BigDecimal("3000")).multiply(new BigDecimal("12"));
    BigDecimal apartmentATax =
        HappyInvestorLongTermFacts.APARTMENT_A_ANNUAL_TAX_BASE.multiply(
            HappyInvestorLongTermFacts.RENTAL_TAX_RATE);
    BigDecimal apartmentBTax =
        HappyInvestorLongTermFacts.APARTMENT_B_ANNUAL_TAX_BASE.multiply(
            HappyInvestorLongTermFacts.RENTAL_TAX_RATE);
    BigDecimal boundaryTax = apartmentATax.add(apartmentBTax);

    assertThat(calendar2025)
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_CALENDAR_2025_GROSS);
    assertThat(boundaryAnnualized)
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_GROSS_ANNUAL);
    assertThat(apartmentATax).isEqualByComparingTo("272");
    assertThat(apartmentBTax).isEqualByComparingTo("255");
    assertThat(boundaryTax)
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_TAX_ANNUAL);
    assertThat(boundaryAnnualized.subtract(boundaryTax))
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_NET_ANNUAL);
  }

  @Test
  void documentsTreasuryLifecycleAndPostReinvestmentIncome() {
    assertThat(
            HappyInvestorLongTermFacts.TREASURY_PRINCIPAL
                .multiply(HappyInvestorLongTermFacts.TREASURY_ANNUAL_RATE)
                .multiply(new BigDecimal("0.81")))
        .isEqualByComparingTo("374.625");
    assertThat(
            HappyInvestorLongTermFacts.TREASURY_PRINCIPAL.multiply(
                HappyInvestorLongTermFacts.REINVESTMENT_TREASURY_COUPON))
        .isEqualByComparingTo(HappyInvestorLongTermFacts.REINVESTMENT_TREASURY_GROSS_ANNUAL);
    assertThat(
            HappyInvestorLongTermFacts.REINVESTMENT_TREASURY_GROSS_ANNUAL.multiply(
                new BigDecimal("0.81")))
        .isEqualByComparingTo(HappyInvestorLongTermFacts.REINVESTMENT_TREASURY_NET_ANNUAL);
    assertThat(
            HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_NET_ANNUAL
                .add(new BigDecimal("810"))
                .add(HappyInvestorLongTermFacts.REINVESTMENT_TREASURY_NET_ANNUAL))
        .isEqualByComparingTo(HappyInvestorLongTermFacts.POST_REINVESTMENT_AGGREGATE_NET_ANNUAL);
  }

  @Test
  void independentlyReconcilesCompleteBrokerStoryAtReferenceDate() {
    assertThat(HappyInvestorBrokerFacts.AS_OF_DATE).isEqualTo(HappyInvestorTestData.REFERENCE_DATE);
    assertThat(HappyInvestorDashboardFacts.AS_OF_DATE)
        .isEqualTo(HappyInvestorBrokerFacts.AS_OF_DATE);
    assertThat(HappyInvestorProfileFacts.AS_OF_DATE).isEqualTo(HappyInvestorBrokerFacts.AS_OF_DATE);
    assertThat(HappyInvestorBrokerFacts.OPEN_POSITIONS)
        .extracting("symbol")
        .containsExactly(
            "AAPL.US", "VWRA.UK", "NVDA.US", "TSLA.US", "GOOGL.US", "MSFT.US", "US91282CKB62");
    assertThat(HappyInvestorBrokerFacts.OPEN_POSITIONS_VALUE).isEqualByComparingTo("174847.919664");
    assertThat(HappyInvestorBrokerFacts.START_OF_YEAR_BALANCE)
        .isEqualByComparingTo(
            HappyInvestorBrokerFacts.OPEN_POSITIONS_VALUE.add(
                HappyInvestorBrokerFacts.BROKERAGE_CASH))
        .isEqualByComparingTo("174831.6796640");
    assertThat(HappyInvestorBrokerFacts.OPEN_POSITIONS_UNREALIZED)
        .isEqualByComparingTo("14036.479664");
    assertThat(HappyInvestorBrokerFacts.AAPL_VALUE).isEqualByComparingTo("134551.634160");
    assertThat(HappyInvestorBrokerFacts.FIXED_INCOME_VALUE).isEqualByComparingTo("360.160000");
    assertThat(
            new BigDecimal("10000")
                .multiply(new BigDecimal("98.81"))
                .movePointLeft(2)
                .multiply(new BigDecimal("3.6016")))
        .isEqualByComparingTo("35587.4096");
  }

  @Test
  void dashboardFlowsAreDerivedFromCanonicalExternalLedger() {
    assertThat(HappyInvestorScenario.externalCashOperations())
        .hasSize(10)
        .allMatch(
            operation ->
                operation.getType() == CashOperationType.DEPOSIT
                    || operation.getType() == CashOperationType.WITHDRAWAL);
    assertThat(
            HappyInvestorScenario.externalCashOperations().stream()
                .filter(operation -> operation.getType() == CashOperationType.WITHDRAWAL)
                .count())
        .isEqualTo(6);
    assertThat(HappyInvestorDashboardFacts.DEPOSITS).isEqualByComparingTo("451127.98693680");
    assertThat(HappyInvestorDashboardFacts.WITHDRAWALS).isEqualByComparingTo("412597.53729850");
    assertThat(HappyInvestorDashboardFacts.NET_DEPOSITS).isEqualByComparingTo("38530.44963830");
    assertThat(
            HappyInvestorDashboardFacts.DEPOSITS.subtract(HappyInvestorDashboardFacts.WITHDRAWALS))
        .isEqualByComparingTo(HappyInvestorDashboardFacts.NET_DEPOSITS);
  }
}
