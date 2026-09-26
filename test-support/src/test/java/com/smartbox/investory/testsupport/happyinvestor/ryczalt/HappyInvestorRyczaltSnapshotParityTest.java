package com.smartbox.investory.testsupport.happyinvestor.ryczalt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class HappyInvestorRyczaltSnapshotParityTest {
  @Test
  void sourceFixtureAndNativeOverlayDescribeTheSameSevenMonthStory() throws IOException {
    var source = HappyInvestorRyczaltFixtureLoader.load().sourceFacts();
    String sql =
        new String(
            getClass()
                .getClassLoader()
                .getResourceAsStream("db/snapshot/happyinvestor-ryczalt.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);

    List<YearMonth> months = source.months();
    assertThat(months).hasSize(7);
    for (int index = 0; index < months.size(); index++) {
      YearMonth month = months.get(index);
      assertThat(sql).contains("2026, " + month.getMonthValue());
      assertThat(sql).contains("HI-RYC-" + month + "-");
      assertThat(sql).contains("sales_corrections");
      assertThat(source.qualifyingUop(month)).isEqualTo(index < 2);
    }
    assertThat(sql).contains("true, true, 'JDG'");
    assertThat(sql).contains("true, false, 'JDG'");
    assertThat(sql).doesNotContain("CORRECTION");
    assertThat(sql).contains("(12107, 2, 2026, 7, true, false");
  }
}
