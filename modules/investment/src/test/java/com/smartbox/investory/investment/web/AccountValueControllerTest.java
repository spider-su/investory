package com.smartbox.investory.investment.web;

import static org.mockito.Mockito.verify;

import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account Value Controller")
class AccountValueControllerTest {
  @Mock private InvestmentPerformanceApi performance;

  @DisplayName("empty Selection Remains Empty Instead Of Loading All Accounts")
  @Test
  void emptySelectionRemainsEmptyInsteadOfLoadingAllAccounts() {
    new AccountValueController(performance).accountValues("", 1L);

    verify(performance).loadAccountValues(1L, List.of());
  }
}
