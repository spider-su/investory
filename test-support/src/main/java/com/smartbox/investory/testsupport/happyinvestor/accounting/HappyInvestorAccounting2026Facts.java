package com.smartbox.investory.testsupport.happyinvestor.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Independent source/business facts for the HappyInvestor Accounting 2026 POC. */
public record HappyInvestorAccounting2026Facts(
    Profile profile,
    List<EffectivePeriod> effectivePeriods,
    List<SalesDocument> salesDocuments,
    List<ExpenseDocument> expenseDocuments,
    List<BankTransaction> bankTransactions,
    List<AuthorityEvidence> authorityEvidence) {

  public static final String PROVENANCE = "HAPPYINVESTOR_ACCOUNTING_2026";
  private static final String RESOURCE =
      "/happyinvestor/accounting/happyinvestor-accounting-2026.json";

  public static HappyInvestorAccounting2026Facts load() {
    try (InputStream stream =
        HappyInvestorAccounting2026Facts.class.getResourceAsStream(RESOURCE)) {
      if (stream == null)
        throw new IllegalStateException("Missing accounting fixture: " + RESOURCE);
      JsonNode root = new ObjectMapper().readTree(stream);
      return new HappyInvestorAccounting2026Facts(
          profile(root.path("profile")),
          root.path("effectivePeriods")
              .valueStream()
              .map(HappyInvestorAccounting2026Facts::period)
              .toList(),
          root.path("salesDocuments")
              .valueStream()
              .map(HappyInvestorAccounting2026Facts::sale)
              .toList(),
          root.path("expenseDocuments")
              .valueStream()
              .map(HappyInvestorAccounting2026Facts::expense)
              .toList(),
          root.path("bankTransactions")
              .valueStream()
              .map(HappyInvestorAccounting2026Facts::bank)
              .toList(),
          root.path("authorityEvidence")
              .valueStream()
              .map(HappyInvestorAccounting2026Facts::authority)
              .toList());
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read accounting fixture", exception);
    }
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

  private static EffectivePeriod period(JsonNode n) {
    return new EffectivePeriod(
        date(n, "from"),
        dateOrNull(n, "to"),
        text(n, "kind"),
        bool(n, "jdgActive"),
        bool(n, "vatRegistered"),
        decimal(n, "ryczaltRate"),
        text(n, "zusRegime"));
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
        text(n, "provenance"));
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
        decimal(n, "vatDeductionRatio"),
        text(n, "provenance"));
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
        text(n, "scope"),
        text(n, "provenance"));
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
    return n.path(field).isNull() ? null : n.path(field).asText();
  }

  private static boolean bool(JsonNode n, String field) {
    return n.path(field).asBoolean();
  }

  private static LocalDate date(JsonNode n, String field) {
    return LocalDate.parse(text(n, field));
  }

  private static LocalDate dateOrNull(JsonNode n, String field) {
    return n.path(field).isMissingNode() || n.path(field).isNull() ? null : date(n, field);
  }

  private static BigDecimal decimal(JsonNode n, String field) {
    return new BigDecimal(text(n, field));
  }

  public record Profile(
      String reference,
      String nip,
      String taxOffice,
      String taxMicroAccount,
      String zusPaymentAccount,
      String reportingCurrency) {}

  public record EffectivePeriod(
      LocalDate from,
      LocalDate to,
      String kind,
      boolean jdgActive,
      boolean vatRegistered,
      BigDecimal ryczaltRate,
      String zusRegime) {}

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
      String provenance) {}

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
      BigDecimal vatDeductionRatio,
      String provenance) {}

  public record BankTransaction(
      LocalDate bookingDate,
      LocalDate relatedPeriod,
      String reference,
      String counterparty,
      String currency,
      BigDecimal amount,
      String transactionType,
      String scope,
      String provenance) {}

  public record AuthorityEvidence(
      LocalDate taxPeriod, String type, BigDecimal amount, String reference, String provenance) {}
}
