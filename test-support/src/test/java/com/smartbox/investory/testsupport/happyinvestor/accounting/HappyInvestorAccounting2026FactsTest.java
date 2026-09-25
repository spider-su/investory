package com.smartbox.investory.testsupport.happyinvestor.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.testsupport.happyinvestor.ryczalt.HappyInvestorRyczalt2026SourceFacts;
import com.smartbox.investory.testsupport.happyinvestor.ryczalt.HappyInvestorRyczaltFixtureLoader;
import org.junit.jupiter.api.Test;

class HappyInvestorRyczalt2026FactsTest {

  @Test
  void ownsOnlySourceFactsForTheThreePocMonths() {
    HappyInvestorRyczalt2026SourceFacts facts =
        HappyInvestorRyczaltFixtureLoader.load().sourceFacts();

    assertThat(facts.profile().reference()).isEqualTo("HI-RYC-2026");
    assertThat(facts.businessPeriods())
        .allMatch(HappyInvestorRyczalt2026SourceFacts.BusinessPeriod::jdgActive);
    assertThat(facts.salesDocuments())
        .extracting(HappyInvestorRyczalt2026SourceFacts.SalesDocument::taxPeriod)
        .contains(
            java.time.LocalDate.of(2026, 1, 1),
            java.time.LocalDate.of(2026, 2, 1),
            java.time.LocalDate.of(2026, 3, 1),
            java.time.LocalDate.of(2026, 4, 1),
            java.time.LocalDate.of(2026, 5, 1),
            java.time.LocalDate.of(2026, 6, 1),
            java.time.LocalDate.of(2026, 7, 1));
    assertThat(facts.expenseDocuments())
        .allMatch(document -> document.gross().compareTo(document.net().add(document.vat())) == 0);
  }
}
