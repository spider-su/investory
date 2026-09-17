package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.accounting.service.AccountingFilingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountingJpkGeneratorTest {
  @Test
  void selectsAndValidatesJpkV7m2For2025Periods() {
    assertThat(AccountingJpkSchemaVersion.forPeriod(LocalDate.of(2025, 12, 1)))
        .isEqualTo("JPK_V7M(2)");
    assertThat(AccountingJpkSchemaVersion.forPeriod(LocalDate.of(2026, 1, 1)))
        .isEqualTo("JPK_V7M(2)");
    assertThat(AccountingJpkSchemaVersion.forPeriod(LocalDate.of(2026, 2, 1)))
        .isEqualTo("JPK_V7M(3)");
    assertThat(AccountingJpkSchemaVersion.requiresEvidenceClassification("JPK_V7M(2)")).isFalse();
    assertThat(AccountingJpkSchemaVersion.requiresEvidenceClassification("JPK_V7M(3)")).isTrue();
    var snap = snapshot();
    var input =
        new AccountingFilingInput(
            LocalDate.of(2025, 12, 1),
            snap.vat(),
            snap.ryczalt(),
            snap.zus(),
            List.of(),
            List.of(),
            taxpayer(),
            "JPK_V7M(2)");

    byte[] xml = new AccountingJpkGenerator().generate(input);
    assertThat(new String(xml))
        .contains("JPK_V7M (2)", "http://crd.gov.pl/wzor/2021/12/27/11148/")
        .doesNotContain("JPK_V7M (3)", "<WariantFormularza>3</WariantFormularza>");
    new AccountingJpkXmlValidator().validate(xml, "JPK_V7M(2)");
  }

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
  void generatesByteIdenticalArtifactForIdenticalInput() {
    AccountingFilingInput input =
        new AccountingFilingService.FilingResult(
                snapshot().period(),
                snapshot(),
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
                List.of())
            .filingInput();

    assertThat(new AccountingJpkGenerator().generate(input))
        .isEqualTo(new AccountingJpkGenerator().generate(input));
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

  @Test
  void declarationTotalsMatchNormalizedEvidenceBuckets() {
    var sales =
        List.of(
            document("S23", VatTreatment.DOMESTIC_VAT, "100", "23", "23"),
            document("S8", VatTreatment.DOMESTIC_VAT, "100", "8", "8"),
            document("S0", VatTreatment.DOMESTIC_VAT, "100", "0", "0"),
            document("SE", VatTreatment.VAT_EXEMPT, "100", "0", null),
            document("SEU", VatTreatment.EU_B2B_REVERSE_CHARGE, "100", "0", null));
    var purchases =
        List.of(
            document("P", VatTreatment.DOMESTIC_PURCHASE, "100", "23", "23", "23"),
            document("PIE", VatTreatment.IMPORT_OF_SERVICES_EU, "200", "46", null),
            document("PIN", VatTreatment.IMPORT_OF_SERVICES_NON_EU, "300", "69", null));
    var input =
        new AccountingFilingInput(
            LocalDate.of(2026, 9, 1),
            snapshot().vat(),
            snapshot().ryczalt(),
            snapshot().zus(),
            sales,
            purchases,
            taxpayer(),
            "JPK_V7M(3)");

    String xml = new String(new AccountingJpkGenerator().generate(input));

    assertThat(xml)
        .contains(
            "<K_19>100.00</K_19><K_20>23.00</K_20>",
            "<K_17>100.00</K_17><K_18>8.00</K_18>",
            "<K_19>100.00</K_19>",
            "<K_29>200.00</K_29><K_30>46.00</K_30>",
            "<K_27>300.00</K_27><K_28>69.00</K_28>",
            "<P_27>300</P_27>",
            "<P_28>69</P_28>",
            "<P_29>200</P_29>",
            "<P_30>46</P_30>",
            "<P_42>600</P_42>",
            "<P_43>23</P_43>",
            "<P_38>146</P_38>");
    new AccountingJpkXmlValidator().validate(xml.getBytes());
  }

  @Test
  void appliesVatDeductionRatioToPurchaseNetBase() {
    var purchase =
        new AccountingFilingInput.FilingDocument(
            "BP-1",
            LocalDate.of(2026, 4, 3),
            null,
            LocalDate.of(2026, 4, 3),
            "9720865431",
            "BP",
            new BigDecimal("313.81"),
            new BigDecimal("25.11"),
            new BigDecimal("12.56"),
            new AccountingFilingEvidence(AccountingFilingEvidence.Type.OFF, null),
            VatTreatment.DOMESTIC_PURCHASE,
            "PL",
            new BigDecimal("8"),
            new BigDecimal("0.50"));
    var input =
        new AccountingFilingInput(
            LocalDate.of(2026, 4, 1),
            snapshot().vat(),
            snapshot().ryczalt(),
            snapshot().zus(),
            List.of(document("S-1", VatTreatment.DOMESTIC_VAT, "100", "23", "23")),
            List.of(purchase),
            taxpayer(),
            "JPK_V7M(3)");

    String xml = new String(new AccountingJpkGenerator().generate(input));

    assertThat(xml).contains("<K_42>156.91</K_42>", "<K_43>12.56</K_43>", "<P_42>157</P_42>");
    new AccountingJpkXmlValidator().validate(xml.getBytes());
  }

  @Test
  void pivotsMultipleVatBucketsIntoOneSalesRow() {
    var document =
        new AccountingFilingInput.FilingDocument(
            "MIXED-1",
            LocalDate.of(2026, 9, 2),
            LocalDate.of(2026, 9, 2),
            null,
            "PL123",
            "Mixed customer",
            new BigDecimal("300"),
            new BigDecimal("31"),
            BigDecimal.ZERO,
            new AccountingFilingEvidence(AccountingFilingEvidence.Type.OFF, null),
            VatTreatment.DOMESTIC_VAT,
            "PL",
            new BigDecimal("23"),
            BigDecimal.ONE,
            List.of(
                new AccountingFilingInput.FilingVatBucket(
                    VatTreatment.DOMESTIC_VAT,
                    new BigDecimal("23"),
                    new BigDecimal("100"),
                    new BigDecimal("23"),
                    BigDecimal.ZERO),
                new AccountingFilingInput.FilingVatBucket(
                    VatTreatment.DOMESTIC_VAT,
                    new BigDecimal("8"),
                    new BigDecimal("200"),
                    new BigDecimal("8"),
                    BigDecimal.ZERO)));
    var input =
        new AccountingFilingInput(
            LocalDate.of(2026, 9, 1),
            snapshot().vat(),
            snapshot().ryczalt(),
            snapshot().zus(),
            List.of(document),
            List.of(),
            taxpayer(),
            "JPK_V7M(3)");

    String xml = new String(new AccountingJpkGenerator().generate(input));

    assertThat(xml)
        .contains("<LiczbaWierszySprzedazy>1</LiczbaWierszySprzedazy>")
        .contains("<K_19>100.00</K_19><K_20>23.00</K_20>")
        .contains("<K_17>200.00</K_17><K_18>8.00</K_18>")
        .contains("<P_38>31</P_38>");
    new AccountingJpkXmlValidator().validate(xml.getBytes());
  }

  @Test
  void refusesUnsupportedDomesticVatRateBeforeGeneratingJpk() {
    var input =
        new AccountingFilingInput(
            LocalDate.of(2026, 9, 1),
            snapshot().vat(),
            snapshot().ryczalt(),
            snapshot().zus(),
            List.of(document("S7", VatTreatment.DOMESTIC_VAT, "100", "7", "7")),
            List.of(),
            taxpayer(),
            "JPK_V7M(3)");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new AccountingJpkGenerator().generate(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("UNSUPPORTED_VAT_RATE: 7");
  }

  private AccountingProfile taxpayer() {
    return new AccountingProfile(
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
  }

  private AccountingFilingInput.FilingDocument document(
      String reference, VatTreatment treatment, String net, String vat, String rate) {
    return document(reference, treatment, net, vat, rate, "0");
  }

  private AccountingFilingInput.FilingDocument document(
      String reference,
      VatTreatment treatment,
      String net,
      String vat,
      String rate,
      String deductible) {
    return new AccountingFilingInput.FilingDocument(
        reference,
        LocalDate.of(2026, 9, 2),
        LocalDate.of(2026, 9, 2),
        LocalDate.of(2026, 9, 2),
        "PL123",
        reference,
        new BigDecimal(net),
        new BigDecimal(vat),
        new BigDecimal(deductible),
        new AccountingFilingEvidence(AccountingFilingEvidence.Type.OFF, null),
        treatment,
        "PL",
        rate == null ? null : new BigDecimal(rate));
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
