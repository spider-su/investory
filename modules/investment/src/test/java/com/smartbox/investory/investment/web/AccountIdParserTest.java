package com.smartbox.investory.investment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AccountIdParserTest {
  @Test
  void parsesTrimmedDistinctPositiveIds() {
    assertThat(AccountIdParser.parse("1, 2,2")).containsExactly(1L, 2L);
  }

  @Test
  void blankSelectionIsEmpty() {
    assertThat(AccountIdParser.parse("  ")).isEqualTo(List.of());
  }

  @Test
  void rejectsMalformedAndNonPositiveIds() {
    assertThatThrownBy(() -> AccountIdParser.parse("1,nope"))
        .isInstanceOf(AccountIdParser.InvalidAccountSelectionException.class);
    assertThatThrownBy(() -> AccountIdParser.parse("0"))
        .isInstanceOf(AccountIdParser.InvalidAccountSelectionException.class);
  }
}
