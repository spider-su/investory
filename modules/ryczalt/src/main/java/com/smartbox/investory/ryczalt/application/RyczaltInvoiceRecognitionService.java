package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.application.port.InvoiceRecognitionPort;
import com.smartbox.investory.ryczalt.application.port.InvoiceRecognitionPort.Party;
import com.smartbox.investory.ryczalt.domain.*;
import com.smartbox.investory.ryczalt.persistence.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RyczaltInvoiceRecognitionService {
  private static final String SOURCE = "UPLOAD";
  private final InvoiceRecognitionPort recognizer;
  private final RyczaltInvoiceCandidateJpaRepository candidates;
  private final RyczaltSourceReferenceJpaRepository sources;
  private final RyczaltCounterpartyService counterparties;

  public RyczaltInvoiceRecognitionService(
      InvoiceRecognitionPort recognizer,
      RyczaltInvoiceCandidateJpaRepository candidates,
      RyczaltSourceReferenceJpaRepository sources,
      RyczaltCounterpartyService counterparties) {
    this.recognizer = recognizer;
    this.candidates = candidates;
    this.sources = sources;
    this.counterparties = counterparties;
  }

  @Transactional
  public CandidateView recognize(
      long profileId, String filename, String contentType, byte[] content) {
    var invoice = recognizer.recognize(filename, contentType, content);
    validate(invoice);
    String externalId = sha256(content);
    var existing =
        candidates.findByProfileIdAndSourceTypeAndSourceExternalId(profileId, SOURCE, externalId);
    if (existing.isPresent()) return view(existing.get(), true);
    LocalDate accountingDate =
        invoice.saleDate() == null ? invoice.issueDate() : invoice.saleDate();
    Party supplier = invoice.seller();
    RyczaltCounterpartyEntity cp =
        supplier == null
            ? null
            : counterparties.resolveByTaxId(
                profileId, supplier.taxIdentifier(), supplier.country(), supplier.legalName());
    var row =
        new RyczaltInvoiceCandidateEntity(
            profileId,
            UUID.randomUUID(),
            SOURCE,
            externalId,
            invoice.documentType(),
            InvoiceDirection.COST,
            invoice.issueDate(),
            invoice.saleDate(),
            invoice.dueDate(),
            invoice.reference(),
            partyName(invoice.seller()),
            partyTax(invoice.seller()),
            partyCountry(invoice.seller()),
            partyName(invoice.buyer()),
            partyTax(invoice.buyer()),
            partyCountry(invoice.buyer()),
            invoice.currency() == null ? "PLN" : invoice.currency(),
            invoice.netAmount(),
            invoice.vatAmount(),
            invoice.grossAmount(),
            invoice.sourceMetadata(),
            invoice.confidence(),
            accountingDate.getYear(),
            accountingDate.getMonthValue());
    if (cp != null) row.setCounterpartyId(cp.id());
    if (cp != null) {
      var match =
          counterparties.match(
              new InvoiceCandidate(cp.id(), SOURCE, invoice.documentType(), null), profileId);
      if (match.kind() == RuleMatchResult.Kind.MATCHED) {
        var rule = match.rule();
        row.apply(
            rule.classification(),
            rule.vatTreatment(),
            rule.vatDeductionRatio(),
            rule.ryczaltRate(),
            rule.paymentVerificationPolicy(),
            rule.autoApprove() ? ApprovalStatus.APPROVED : ApprovalStatus.NEEDS_REVIEW,
            rule.autoApprove() ? "COUNTERPARTY_RULE" : null);
      }
    }
    row = candidates.save(row);
    sources.save(
        new RyczaltSourceReferenceEntity(
            profileId,
            "INVOICE_CANDIDATE",
            row.id(),
            SOURCE,
            externalId,
            invoice.sourceMetadata()));
    return view(row, false);
  }

  @Transactional(readOnly = true)
  public CandidateView get(long profileId, UUID key) {
    return candidates
        .findByProfileIdAndCandidateKey(profileId, key)
        .map(row -> view(row, row.isDuplicate()))
        .orElseThrow(() -> new RyczaltInvoiceCandidateNotFoundException(profileId));
  }

  private CandidateView view(RyczaltInvoiceCandidateEntity row, boolean duplicate) {
    return new CandidateView(
        row.getCandidateKey(),
        row.getSourceType(),
        row.getSourceExternalId(),
        row.getDocumentType(),
        row.getDirection(),
        row.getIssueDate(),
        row.getSaleDate(),
        row.getDueDate(),
        row.getReference(),
        row.getCounterpartyId(),
        row.getCurrency(),
        decimal(row.getNetAmount()),
        decimal(row.getVatAmount()),
        decimal(row.getGrossAmount()),
        row.getClassification(),
        row.getVatTreatment(),
        decimal(row.getRyczaltRate()),
        row.getApprovalStatus(),
        row.getApprovalSource(),
        row.getPaymentVerificationPolicy(),
        row.getPaymentVerificationPolicy() == PaymentVerificationPolicy.NOT_REQUIRED
            ? "NOT_REQUIRED"
            : "UNMATCHED",
        duplicate,
        row.getPeriodYear(),
        row.getPeriodMonth(),
        required(row));
  }

  private List<RequiredInput> required(RyczaltInvoiceCandidateEntity row) {
    if (row.getApprovalStatus() == ApprovalStatus.APPROVED) return List.of();
    var result = new java.util.ArrayList<RequiredInput>();
    if (row.getCounterpartyId() == null)
      result.add(new RequiredInput("COUNTERPARTY", "CHOICE", true, List.of(), null, List.of()));
    if (row.getClassification() == null)
      result.add(new RequiredInput("CLASSIFICATION", "TEXT", true, List.of(), null, List.of()));
    if (row.getRyczaltRate() == null)
      result.add(new RequiredInput("RYCZALT_RATE", "DECIMAL", true, List.of(), null, List.of()));
    return List.copyOf(result);
  }

  private static void validate(InvoiceRecognitionPort.RecognizedInvoice i) {
    if (i == null
        || i.issueDate() == null
        || blank(i.reference())
        || blank(i.documentType())
        || i.netAmount() == null
        || i.vatAmount() == null
        || i.grossAmount() == null)
      throw new IllegalArgumentException(
          "Recognition did not produce required invoice source facts");
    if (i.netAmount().signum() < 0 || i.vatAmount().signum() < 0 || i.grossAmount().signum() < 0)
      throw new IllegalArgumentException("Invoice amounts cannot be negative");
    try {
      CurrencyType.valueOf(i.currency() == null ? "PLN" : i.currency());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported invoice currency", e);
    }
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String partyName(Party p) {
    return p == null ? null : p.legalName();
  }

  private static String partyTax(Party p) {
    return p == null ? null : p.taxIdentifier();
  }

  private static String partyCountry(Party p) {
    return p == null ? null : p.country();
  }

  private static String decimal(java.math.BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }

  private static String sha256(byte[] content) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(content);
      return java.util.HexFormat.of().formatHex(bytes);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public record CandidateView(
      UUID candidateKey,
      String sourceType,
      String sourceExternalId,
      String documentType,
      InvoiceDirection direction,
      LocalDate issueDate,
      LocalDate saleDate,
      LocalDate dueDate,
      String reference,
      Long counterpartyId,
      String currency,
      String netAmount,
      String vatAmount,
      String grossAmount,
      String classification,
      String vatTreatment,
      String ryczaltRate,
      ApprovalStatus approvalStatus,
      String approvalMethod,
      PaymentVerificationPolicy paymentVerificationPolicy,
      String paymentStatus,
      boolean duplicate,
      int periodYear,
      int periodMonth,
      List<RequiredInput> requiredInputs) {}
}
