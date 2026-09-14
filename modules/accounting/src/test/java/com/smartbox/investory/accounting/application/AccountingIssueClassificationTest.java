package com.smartbox.investory.accounting.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.accounting.api.AccountingUserApi.IssueKind;
import org.junit.jupiter.api.Test;

class AccountingIssueClassificationTest {

  @Test
  void classifiesSetupAnswerBlockingAndInfoIssuesExplicitly() {
    assertThat(AccountingUserFacade.issueKind("MISSING_PAYMENT_CONFIGURATION: VAT", "BLOCKING"))
        .isEqualTo(IssueKind.SETUP);
    assertThat(AccountingUserFacade.issueKind("MISSING_EXPLICIT_VAT_RATE: FV-1", "BLOCKING"))
        .isEqualTo(IssueKind.NEEDS_ANSWER);
    assertThat(AccountingUserFacade.issueKind("UNSUPPORTED_VAT_RATE: FV-1 (7)", "BLOCKING"))
        .isEqualTo(IssueKind.NEEDS_ANSWER);
    assertThat(AccountingUserFacade.issueKind("MISSING_FX", "BLOCKING"))
        .isEqualTo(IssueKind.BLOCKED);
    assertThat(AccountingUserFacade.issueKind("CALCULATION_NOTE", "INFO"))
        .isEqualTo(IssueKind.INFO);
  }

  @Test
  void keepsFilingCodeInsteadOfCollapsingItIntoGenericReadiness() {
    assertThat(AccountingUserFacade.filingIssueCode("MISSING_PAYMENT_CONFIGURATION: VAT"))
        .isEqualTo("MISSING_PAYMENT_CONFIGURATION");
    assertThat(AccountingUserFacade.filingIssueCode("Month calculation is not confirmed"))
        .isEqualTo("FILING_READINESS");
  }
}
