package com.smartbox.investory.testsupport.happyinvestor.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HappyInvestorAccounting2026FactsTest {

  @Test
  void ownsOnlySourceFactsForTheThreePocMonths() {
    HappyInvestorAccounting2026Facts facts = HappyInvestorAccountingFixtureLoader.load().facts();

    assertThat(facts.profile().reference()).isEqualTo("HI-ACC-2026");
    assertThat(facts.effectivePeriods())
        .allMatch(period -> period.jdgActive() || !period.vatRegistered());
    assertThat(facts.salesDocuments())
        .extracting(HappyInvestorAccounting2026Facts.SalesDocument::taxPeriod)
        .contains(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
    assertThat(facts.salesDocuments())
        .allMatch(
            document -> HappyInvestorAccounting2026Facts.PROVENANCE.equals(document.provenance()));
    assertThat(facts.expenseDocuments())
        .allMatch(document -> document.gross().compareTo(document.net().add(document.vat())) == 0);
  }
}
