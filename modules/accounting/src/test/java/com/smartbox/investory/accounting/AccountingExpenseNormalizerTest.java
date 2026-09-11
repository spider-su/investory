package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartbox.investory.accounting.AccountingExpenseNormalizer.ExpenseImportCandidate;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountingExpenseNormalizerTest {

  private final AccountingExpenseNormalizer normalizer = new AccountingExpenseNormalizer();

  @Test
  void derivesEightPercentFuelVatAndFiftyPercentDeduction() {
    var normalized =
        normalizer.normalize(
            new ExpenseImportCandidate("VEHICLE_FUEL", new BigDecimal("385.08"), null, null, null));

    assertThat(normalized.netAmount()).isEqualByComparingTo("356.56");
    assertThat(normalized.vatAmount()).isEqualByComparingTo("28.52");
    assertThat(normalized.vatDeductionRatio()).isEqualByComparingTo("0.50");
    assertThat(normalized.deductibleVat()).isEqualByComparingTo("14.26");
    assertThat(normalized.sourceQuality()).isEqualTo("DERIVED_RULE_8");
  }

  @Test
  void derivesTwentyThreePercentServiceVatWithFullDeduction() {
    var normalized =
        normalizer.normalize(
            new ExpenseImportCandidate(
                "ACCOUNTING_SERVICE", new BigDecimal("366.54"), null, null, null));

    assertThat(normalized.netAmount()).isEqualByComparingTo("298.00");
    assertThat(normalized.vatAmount()).isEqualByComparingTo("68.54");
    assertThat(normalized.vatDeductionRatio()).isEqualByComparingTo("1.00");
    assertThat(normalized.deductibleVat()).isEqualByComparingTo("68.54");
    assertThat(normalized.sourceQuality()).isEqualTo("DERIVED_RULE_23");
  }

  @Test
  void sourceDocumentValuesAlwaysWinOverCategoryDefaults() {
    var normalized =
        normalizer.normalize(
            new ExpenseImportCandidate(
                "EQUIPMENT",
                new BigDecimal("529.74"),
                new BigDecimal("430.68"),
                new BigDecimal("99.06"),
                new BigDecimal("1.00")));

    assertThat(normalized.netAmount()).isEqualByComparingTo("430.68");
    assertThat(normalized.vatAmount()).isEqualByComparingTo("99.06");
    assertThat(normalized.deductibleVat()).isEqualByComparingTo("99.06");
    assertThat(normalized.sourceQuality()).isEqualTo("SOURCE_DOCUMENT");
    assertThat(normalized.derivedVatRate()).isNull();
  }

  @Test
  void refusesToGuessVatForUnsupportedCategory() {
    assertThatThrownBy(
            () ->
                normalizer.normalize(
                    new ExpenseImportCandidate(
                        "EQUIPMENT", new BigDecimal("312.99"), null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source net/VAT is required instead of guessing");
  }
}
