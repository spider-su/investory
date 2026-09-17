package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalAccountingDocumentTest {

  @Test
  void acceptsReviewedDomesticSaleWithMatchingVatBucket() {
    CanonicalAccountingDocument document =
        document(
            CanonicalAccountingDocument.Direction.SALE,
            new CanonicalAccountingDocument.VatBucket(
                VatTreatment.DOMESTIC_VAT,
                new BigDecimal("23"),
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                BigDecimal.ZERO));

    assertThat(document.reference().value()).isEqualTo("FV 1/2026");
    assertThat(document.currency()).isEqualTo("PLN");
  }

  @Test
  void rejectsBucketTotalsThatDoNotMatchTheDocumentHeader() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                document(
                    CanonicalAccountingDocument.Direction.SALE,
                    new CanonicalAccountingDocument.VatBucket(
                        VatTreatment.DOMESTIC_VAT,
                        new BigDecimal("23"),
                        new BigDecimal("99.00"),
                        new BigDecimal("23.00"),
                        BigDecimal.ZERO)))
        .withMessage("VAT bucket totals must equal document totals");
  }

  @Test
  void rejectsPurchaseTreatmentOnSale() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                document(
                    CanonicalAccountingDocument.Direction.SALE,
                    new CanonicalAccountingDocument.VatBucket(
                        VatTreatment.DOMESTIC_PURCHASE,
                        new BigDecimal("23"),
                        new BigDecimal("100.00"),
                        new BigDecimal("23.00"),
                        BigDecimal.ZERO)))
        .withMessage("VAT treatment is not legal for document direction");
  }

  @Test
  void rejectsUnsupportedVatRateAndNonMonthTaxPeriod() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> AccountingVatRate.of(new BigDecimal("12")))
        .withMessage("VAT rate must be one of 0, 5, 8 or 23");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> AccountingTaxPeriod.of(LocalDate.of(2026, 7, 2)))
        .withMessage("Tax period must start on the first day of a month");
  }

  private CanonicalAccountingDocument document(
      CanonicalAccountingDocument.Direction direction,
      CanonicalAccountingDocument.VatBucket bucket) {
    return new CanonicalAccountingDocument(
        direction,
        CanonicalAccountingDocument.Kind.INVOICE,
        AccountingTaxPeriod.of(LocalDate.of(2026, 7, 1)),
        AccountingDocumentReference.of(" FV 1/2026 "),
        "pln",
        new BigDecimal("100.00"),
        new BigDecimal("23.00"),
        new BigDecimal("123.00"),
        List.of(bucket));
  }
}
