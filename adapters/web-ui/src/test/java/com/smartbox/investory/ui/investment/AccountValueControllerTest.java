package com.smartbox.investory.ui.investment;

import static org.mockito.Mockito.verify;

import com.smartbox.investory.investment.api.InvestmentPerformanceApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountValueControllerTest {
  @Mock private InvestmentPerformanceApi performance;

  @Test
  void emptySelectionRemainsEmptyInsteadOfLoadingAllAccounts() {
    new AccountValueController(performance).accountValues("");

    verify(performance).loadAccountValues(List.of());
  }
}
