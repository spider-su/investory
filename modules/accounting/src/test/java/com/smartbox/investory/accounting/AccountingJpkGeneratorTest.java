package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountingJpkGeneratorTest {
  @Test
  void projectsCanonicalVatTotalsIntoCurrentJpkV7mSchema() {
    AccountingMonthSnapshot snapshot = snapshot();
    AccountingFilingService.FilingResult result =
        new AccountingFilingService.FilingResult(
            snapshot.period(),
            snapshot,
            new AccountingProfile(
                true,
                "1010000000",
                "POC",
                "1215",
                "a@b",
                null,
                null,
                null,
                "Jan",
                "Kowalski",
                LocalDate.of(1980, 1, 1)),
            "hash",
            true,
            List.of());

    String xml = new String(new AccountingJpkGenerator().generate(result));

    assertThat(xml).contains("JPK_V7M (3)", "1-0E", AccountingJpkGenerator.NS);
    assertThat(xml).contains("<P_38>230</P_38>", "<P_43>23</P_43>", "<P_51>207</P_51>");
    new AccountingJpkXmlValidator().validate(xml.getBytes());
  }

  @Test
  void dueDatesAreDeterministicForMonthlyJdg() {
    AccountingDueDatePolicy policy = new AccountingDueDatePolicy();
    assertThat(policy.dueDate(LocalDate.of(2026, 9, 1), "VAT"))
        .isEqualTo(LocalDate.of(2026, 10, 26));
    assertThat(policy.dueDate(LocalDate.of(2026, 9, 1), "ZUS"))
        .isEqualTo(LocalDate.of(2026, 10, 20));
  }

  @Test
  void doesNotReportExemptOrReverseChargeSalesAsDomesticVat() {
    AccountingProfile taxpayer =
        new AccountingProfile(
            true,
            "1010000000",
            "POC",
            "1215",
            "a@b",
            null,
            null,
            null,
            "Jan",
            "Kowalski",
            LocalDate.of(1980, 1, 1));
    AccountingFilingInput input =
        new AccountingFilingInput(
            LocalDate.of(2026, 9, 1),
            snapshot().vat(),
            snapshot().ryczalt(),
            snapshot().zus(),
            List.of(
                new AccountingFilingInput.FilingDocument(
                    "EXEMPT-1",
                    LocalDate.of(2026, 9, 2),
                    LocalDate.of(2026, 9, 2),
                    null,
                    "PL123",
                    "Exempt customer",
                    new BigDecimal("100"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    VatTreatment.VAT_EXEMPT,
                    "PL"),
                new AccountingFilingInput.FilingDocument(
                    "EU-1",
                    LocalDate.of(2026, 9, 3),
                    LocalDate.of(2026, 9, 3),
                    null,
                    "DE123",
                    "EU customer",
                    new BigDecimal("200"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    VatTreatment.EU_B2B_REVERSE_CHARGE,
                    "DE")),
            List.of(),
            taxpayer,
            "JPK_V7M(3)");

    String xml = new String(new AccountingJpkGenerator().generate(input));

    assertThat(xml).contains("<K_10>100.00</K_10>", "<K_11>200.00</K_11>");
    assertThat(xml).doesNotContain("<K_19>100.00</K_19>", "<K_20>200.00</K_20>");
  }

  private AccountingMonthSnapshot snapshot() {
    return new AccountingMonthSnapshot(
        LocalDate.of(2026, 9, 1),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new AccountingMonthSnapshot.FxCalculation(
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "NO_FX_SOURCE"),
        new AccountingMonthSnapshot.RyczaltCalculation(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "MATCH"),
        new AccountingMonthSnapshot.VatCalculation(
            new BigDecimal("230"),
            BigDecimal.ZERO,
            new BigDecimal("230"),
            new BigDecimal("23"),
            BigDecimal.ZERO,
            new BigDecimal("207"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "MATCH"),
        new AccountingMonthSnapshot.ZusCalculation(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
            AccountingMonthSnapshot.ZusCalculation.UOP_PRIMARY_INSURANCE),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
