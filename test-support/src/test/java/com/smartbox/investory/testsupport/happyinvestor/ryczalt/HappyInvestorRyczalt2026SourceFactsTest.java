package com.smartbox.investory.testsupport.happyinvestor.ryczalt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class HappyInvestorRyczalt2026SourceFactsTest {
  @Test
  void coversContinuousOperationalStoryWithoutSalesCorrections() {
    var facts = HappyInvestorRyczaltFixtureLoader.load().sourceFacts();

    assertThat(facts.months())
        .containsExactly(
            YearMonth.of(2026, 1),
            YearMonth.of(2026, 2),
            YearMonth.of(2026, 3),
            YearMonth.of(2026, 4),
            YearMonth.of(2026, 5),
            YearMonth.of(2026, 6),
            YearMonth.of(2026, 7));
    assertThat(facts.months())
        .allMatch(month -> facts.businessPeriods().stream().anyMatch(period -> period.jdgActive()));
    assertThat(facts.months().subList(0, 2)).allMatch(facts::qualifyingUop);
    assertThat(facts.months().subList(2, 7)).noneMatch(facts::qualifyingUop);
    assertThat(facts.salesDocuments())
        .noneMatch(document -> document.reference().contains("CORRECTION"));
    assertThat(facts.salesDocuments()).noneMatch(document -> document.reference().contains("ACC-"));
    assertThat(facts.salesDocuments())
        .allMatch(document -> document.bookedNetPln() != null || document.currency().equals("PLN"));
  }
}
