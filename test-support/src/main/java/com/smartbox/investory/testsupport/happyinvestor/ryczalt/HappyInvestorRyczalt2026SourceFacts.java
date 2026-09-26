package com.smartbox.investory.testsupport.happyinvestor.ryczalt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Synthetic source facts for the independent Happy Investor Ryczalt 2026 story. */
public record HappyInvestorRyczalt2026SourceFacts(
    Profile profile,
    List<BusinessPeriod> businessPeriods,
    List<EmploymentPeriod> employmentPeriods,
    List<SalesDocument> salesDocuments,
    List<ExpenseDocument> expenseDocuments,
    List<BankTransaction> bankTransactions,
    List<AuthorityEvidence> authorityEvidence) {

  public static final String PROVENANCE = "HAPPYINVESTOR_RYCZALT_2026";
  private static final String RESOURCE = "/happyinvestor/ryczalt/happyinvestor-ryczalt-2026.json";

  public static HappyInvestorRyczalt2026SourceFacts load() {
    try (InputStream stream =
        HappyInvestorRyczalt2026SourceFacts.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) throw new IllegalStateException("Missing Ryczalt fixture: " + RESOURCE);
      JsonNode root = new ObjectMapper().readTree(stream);
      return new HappyInvestorRyczalt2026SourceFacts(
          profile(root.path("profile")),
          root.path("businessPeriods")
              .valueStream()
              .map(HappyInvestorRyczalt2026SourceFacts::business)
              .toList(),
          root.path("employmentPeriods")
              .valueStream()
              .map(HappyInvestorRyczalt2026SourceFacts::employment)
              .toList(),
          root.path("salesDocuments")
              .valueStream()
              .map(HappyInvestorRyczalt2026SourceFacts::sale)
              .toList(),
          root.path("expenseDocuments")
              .valueStream()
              .map(HappyInvestorRyczalt2026SourceFacts::expense)
              .toList(),
          root.path("bankTransactions")
              .valueStream()
              .map(HappyInvestorRyczalt2026SourceFacts::bank)
              .toList(),
          root.path("authorityEvidence")
              .valueStream()
              .map(HappyInvestorRyczalt2026SourceFacts::authority)
              .toList());
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read Ryczalt fixture", exception);
    }
  }

  public List<YearMonth> months() {
    return salesDocuments.stream()
        .map(document -> YearMonth.from(document.taxPeriod()))
        .distinct()
        .sorted()
        .toList();
  }

  public boolean qualifyingUop(YearMonth month) {
    return employmentPeriods.stream()
        .anyMatch(period -> period.qualifyingAsPrimaryInsuranceTitle() && period.includes(month));
  }

  private static Profile profile(JsonNode n) {
    return new Profile(
        text(n, "reference"),
        text(n, "nip"),
        text(n, "taxOffice"),
        text(n, "taxMicroAccount"),
        text(n, "zusPaymentAccount"),
        text(n, "reportingCurrency"));
  }

  private static BusinessPeriod business(JsonNode n) {
    return new BusinessPeriod(
        date(n, "from"),
        dateOrNull(n, "to"),
        bool(n, "jdgActive"),
        bool(n, "vatRegistered"),
        decimal(n, "ryczaltRate"));
  }

  private static EmploymentPeriod employment(JsonNode n) {
    return new EmploymentPeriod(
        date(n, "from"), dateOrNull(n, "to"), bool(n, "qualifyingAsPrimaryInsuranceTitle"));
  }

  private static SalesDocument sale(JsonNode n) {
    return new SalesDocument(
        date(n, "taxPeriod"),
        dateOrNull(n, "issueDate"),
        dateOrNull(n, "saleDate"),
        text(n, "reference"),
        text(n, "counterparty"),
        text(n, "currency"),
        decimal(n, "net"),
        decimal(n, "vat"),
        decimal(n, "gross"),
        dateOrNull(n, "fxRateDate"),
        decimalOrNull(n, "bookedNetPln"));
  }

  private static ExpenseDocument expense(JsonNode n) {
    return new ExpenseDocument(
        date(n, "taxPeriod"),
        dateOrNull(n, "invoiceDate"),
        text(n, "reference"),
        text(n, "supplier"),
        text(n, "category"),
        text(n, "currency"),
        decimal(n, "net"),
        decimal(n, "vat"),
        decimal(n, "gross"),
        decimal(n, "vatDeductionRatio"));
  }

  private static BankTransaction bank(JsonNode n) {
    return new BankTransaction(
        date(n, "bookingDate"),
        dateOrNull(n, "relatedPeriod"),
        text(n, "reference"),
        text(n, "counterparty"),
        text(n, "currency"),
        decimal(n, "amount"),
        text(n, "transactionType"),
        text(n, "scope"));
  }

  private static AuthorityEvidence authority(JsonNode n) {
    return new AuthorityEvidence(
        date(n, "taxPeriod"),
        text(n, "type"),
        decimal(n, "amount"),
        text(n, "reference"),
        text(n, "provenance"));
  }

  private static String text(JsonNode n, String field) {
    return n.path(field).isMissingNode() || n.path(field).isNull() ? null : n.path(field).asText();
  }

  private static boolean bool(JsonNode n, String field) {
    return n.path(field).asBoolean();
  }

  private static LocalDate date(JsonNode n, String field) {
    return LocalDate.parse(text(n, field));
  }

  private static LocalDate dateOrNull(JsonNode n, String field) {
    return text(n, field) == null ? null : date(n, field);
  }

  private static BigDecimal decimal(JsonNode n, String field) {
    return new BigDecimal(text(n, field));
  }

  private static BigDecimal decimalOrNull(JsonNode n, String field) {
    return text(n, field) == null ? null : decimal(n, field);
  }

  public record Profile(
      String reference,
      String nip,
      String taxOffice,
      String taxMicroAccount,
      String zusPaymentAccount,
      String reportingCurrency) {}

  public record BusinessPeriod(
      LocalDate from,
      LocalDate to,
      boolean jdgActive,
      boolean vatRegistered,
      BigDecimal ryczaltRate) {
    boolean includes(YearMonth month) {
      return !month.atEndOfMonth().isBefore(from) && (to == null || !month.atDay(1).isAfter(to));
    }
  }

  public record EmploymentPeriod(
      LocalDate from, LocalDate to, boolean qualifyingAsPrimaryInsuranceTitle) {
    boolean includes(YearMonth month) {
      return !month.atEndOfMonth().isBefore(from) && (to == null || !month.atDay(1).isAfter(to));
    }
  }

  public record SalesDocument(
      LocalDate taxPeriod,
      LocalDate issueDate,
      LocalDate saleDate,
      String reference,
      String counterparty,
      String currency,
      BigDecimal net,
      BigDecimal vat,
      BigDecimal gross,
      LocalDate fxRateDate,
      BigDecimal bookedNetPln) {}

  public record ExpenseDocument(
      LocalDate taxPeriod,
      LocalDate invoiceDate,
      String reference,
      String supplier,
      String category,
      String currency,
      BigDecimal net,
      BigDecimal vat,
      BigDecimal gross,
      BigDecimal vatDeductionRatio) {}

  public record BankTransaction(
      LocalDate bookingDate,
      LocalDate relatedPeriod,
      String reference,
      String counterparty,
      String currency,
      BigDecimal amount,
      String transactionType,
      String scope) {}

  public record AuthorityEvidence(
      LocalDate taxPeriod, String type, BigDecimal amount, String reference, String provenance) {}
}
