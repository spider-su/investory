package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.application.port.InvoiceRecognitionPort;
import com.smartbox.investory.ryczalt.application.port.InvoiceRecognitionPort.Party;
import com.smartbox.investory.ryczalt.domain.*;
import com.smartbox.investory.ryczalt.persistence.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RyczaltInvoiceRecognitionService {
  private static final String SOURCE = "UPLOAD";
  public static final String MANUAL_SOURCE = "MANUAL";
  private final InvoiceRecognitionPort recognizer;
  private final RyczaltInvoiceCandidateJpaRepository candidates;
  private final RyczaltSourceReferenceJpaRepository sources;
  private final RyczaltCounterpartyService counterparties;
  private final RyczaltInvoiceCandidatePersistenceService persistence;
  private final RyczaltInvoiceApprovalService approval;

  public RyczaltInvoiceRecognitionService(
      InvoiceRecognitionPort recognizer,
      RyczaltInvoiceCandidateJpaRepository candidates,
      RyczaltSourceReferenceJpaRepository sources,
      RyczaltCounterpartyService counterparties,
      RyczaltInvoiceCandidatePersistenceService persistence,
      RyczaltInvoiceApprovalService approval) {
    this.recognizer = recognizer;
    this.candidates = candidates;
    this.sources = sources;
    this.counterparties = counterparties;
    this.persistence = persistence;
    this.approval = approval;
  }

  public CandidateView recognize(
      long profileId, String filename, String contentType, byte[] content) {
    var invoice = recognizer.recognize(filename, contentType, content);
    validate(invoice);
    String externalId = sha256(content);
    if (sources
        .findByProfileIdAndEntityTypeAndSourceAndExternalId(
            profileId, "INVOICE", SOURCE, externalId)
        .isPresent()) throw new RyczaltInvoiceSourceConflictException();
    var existing =
        candidates.findByProfileIdAndSourceTypeAndSourceExternalId(profileId, SOURCE, externalId);
    if (existing.isPresent()) return view(existing.get(), SourceState.EXISTING_CANDIDATE);
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
    row.setServiceKey(RuleCriteria.serviceKey(invoice.serviceKey()));
    if (cp != null) row.setCounterpartyId(cp.id());
    if (cp != null && row.getServiceKey() != null) {
      var match =
          counterparties.match(
              new InvoiceCandidate(cp.id(), SOURCE, invoice.documentType(), row.getServiceKey()),
              profileId);
      row.setRuleMatchStatus(match.kind());
      if (match.kind() == RuleMatchResult.Kind.MATCHED) {
        var rule = match.rule();
        row.apply(
            rule.classification(),
            rule.vatTreatment(),
            rule.vatDeductionRatio(),
            rule.ryczaltRate(),
            rule.paymentVerificationPolicy(),
            rule.autoApprove() ? ApprovalStatus.APPROVED : ApprovalStatus.NEEDS_REVIEW,
            rule.autoApprove() ? ApprovalMethod.COUNTERPARTY_RULE : null);
      }
    }
    try {
      row = persistence.persist(row, invoice.sourceMetadata());
    } catch (DataIntegrityViolationException exception) {
      if (knownRecognitionConflict(exception)) {
        return candidates
            .findByProfileIdAndSourceTypeAndSourceExternalId(profileId, SOURCE, externalId)
            .map(winner -> view(winner, SourceState.EXISTING_CANDIDATE))
            .orElseThrow(() -> exception);
      }
      throw exception;
    }
    CandidateView result = view(row, SourceState.NEW_CANDIDATE);
    if (row.getApprovalStatus() == ApprovalStatus.APPROVED) {
      approval.approve(
          profileId,
          row.getCandidateKey(),
          new RyczaltInvoiceApprovalService.ApproveCommand(
              row.getCounterpartyId(),
              row.getClassification(),
              row.getVatTreatment(),
              row.getVatDeductionRatio(),
              row.getRyczaltRate(),
              row.getPaymentVerificationPolicy(),
              true,
              false,
              null,
              row.getServiceKey()));
    }
    return result;
  }

  @Transactional
  public CandidateView createManual(long profileId, ManualCandidateCommand command) {
    validateManual(command);
    String externalId =
        sha256(command.identity().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    if (sources
        .findByProfileIdAndEntityTypeAndSourceAndExternalId(
            profileId, "INVOICE", MANUAL_SOURCE, externalId)
        .isPresent()) throw new RyczaltInvoiceSourceConflictException();
    var existing =
        candidates.findByProfileIdAndSourceTypeAndSourceExternalId(
            profileId, MANUAL_SOURCE, externalId);
    if (existing.isPresent()) return view(existing.get(), SourceState.EXISTING_CANDIDATE);

    LocalDate accountingDate =
        command.saleDate() == null ? command.issueDate() : command.saleDate();
    var row =
        new RyczaltInvoiceCandidateEntity(
            profileId,
            UUID.randomUUID(),
            MANUAL_SOURCE,
            externalId,
            "MANUAL",
            InvoiceDirection.COST,
            command.issueDate(),
            command.saleDate(),
            command.dueDate(),
            command.reference(),
            command.counterparty().legalName(),
            command.counterparty().taxIdentifier(),
            command.counterparty().country(),
            null,
            null,
            null,
            command.currency(),
            command.netAmount(),
            command.vatAmount(),
            command.grossAmount(),
            null,
            null,
            accountingDate.getYear(),
            accountingDate.getMonthValue());
    var cp =
        counterparties.resolveByTaxId(
            profileId,
            command.counterparty().taxIdentifier(),
            command.counterparty().country(),
            command.counterparty().legalName());
    if (cp != null) row.setCounterpartyId(cp.id());
    if (cp != null) {
      var match =
          counterparties.match(
              new InvoiceCandidate(cp.id(), MANUAL_SOURCE, "MANUAL", null), profileId);
      row.setRuleMatchStatus(match.kind());
      if (match.kind() == RuleMatchResult.Kind.MATCHED) {
        var rule = match.rule();
        row.apply(
            rule.classification(),
            rule.vatTreatment(),
            rule.vatDeductionRatio(),
            rule.ryczaltRate(),
            rule.paymentVerificationPolicy(),
            rule.autoApprove() ? ApprovalStatus.APPROVED : ApprovalStatus.NEEDS_REVIEW,
            rule.autoApprove() ? ApprovalMethod.COUNTERPARTY_RULE : null);
      }
    }
    try {
      row = persistence.persist(row, null);
    } catch (DataIntegrityViolationException exception) {
      if (knownRecognitionConflict(exception)) {
        return candidates
            .findByProfileIdAndSourceTypeAndSourceExternalId(profileId, MANUAL_SOURCE, externalId)
            .map(winner -> view(winner, SourceState.EXISTING_CANDIDATE))
            .orElseThrow(() -> exception);
      }
      throw exception;
    }
    CandidateView result = view(row, SourceState.NEW_CANDIDATE);
    if (row.getApprovalStatus() == ApprovalStatus.APPROVED) {
      approval.approve(
          profileId,
          row.getCandidateKey(),
          new RyczaltInvoiceApprovalService.ApproveCommand(
              row.getCounterpartyId(),
              row.getClassification(),
              row.getVatTreatment(),
              row.getVatDeductionRatio(),
              row.getRyczaltRate(),
              row.getPaymentVerificationPolicy(),
              true,
              false,
              null,
              null));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public CandidateView get(long profileId, UUID key) {
    return candidates
        .findByProfileIdAndCandidateKey(profileId, key)
        .map(row -> view(row, SourceState.EXISTING_CANDIDATE))
        .orElseThrow(() -> new RyczaltInvoiceCandidateNotFoundException(profileId));
  }

  private CandidateView view(RyczaltInvoiceCandidateEntity row, SourceState sourceState) {
    return new CandidateView(
        row.getCandidateKey(),
        row.getSourceType(),
        row.getSourceExternalId(),
        row.getDocumentType(),
        row.getDirection().name(),
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
        row.getApprovalMethod(),
        row.getPaymentVerificationPolicy(),
        row.getRuleMatchStatus(),
        row.getPaymentVerificationPolicy() == PaymentVerificationPolicy.NOT_REQUIRED
            ? InvoicePaymentStatus.NOT_REQUIRED
            : InvoicePaymentStatus.UNMATCHED,
        sourceState,
        row.getPeriodYear(),
        row.getPeriodMonth(),
        required(row));
  }

  private List<RequiredInput> required(RyczaltInvoiceCandidateEntity row) {
    var result = new java.util.ArrayList<RequiredInput>();
    if (row.getCounterpartyId() == null)
      result.add(new RequiredInput("COUNTERPARTY", "CHOICE", true, List.of(), null, List.of()));
    if (row.getClassification() == null)
      result.add(new RequiredInput("CLASSIFICATION", "TEXT", true, List.of(), null, List.of()));
    if (row.getVatTreatment() == null)
      result.add(new RequiredInput("VAT_TREATMENT", "CHOICE", true, List.of(), null, List.of()));
    if (row.getVatTreatment() != null
        && (row.getVatTreatment().equals("HALF") || row.getVatTreatment().equals("PARTIAL"))
        && row.getVatDeductionRatio() == null)
      result.add(
          new RequiredInput(
              "VAT_DEDUCTION_RATIO",
              "DECIMAL",
              true,
              List.of(),
              "VAT_TREATMENT",
              List.of("HALF", "PARTIAL")));
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

  private static void validateManual(ManualCandidateCommand c) {
    if (c == null) throw new IllegalArgumentException("Manual invoice is required");
    if (c.direction() != InvoiceDirection.COST)
      throw new IllegalArgumentException("Manual invoice direction must be COST");
    if (blank(c.reference()) || c.reference().length() > 128)
      throw new IllegalArgumentException("Manual invoice reference is required");
    if (c.issueDate() == null) throw new IllegalArgumentException("issueDate is required");
    if (c.currency() == null || c.currency().length() != 3)
      throw new IllegalArgumentException("currency must be a three-letter code");
    try {
      CurrencyType.valueOf(c.currency());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported invoice currency", e);
    }
    amount(c.netAmount(), "netAmount");
    amount(c.vatAmount(), "vatAmount");
    amount(c.grossAmount(), "grossAmount");
    if (c.counterparty() == null || blank(c.counterparty().legalName()))
      throw new IllegalArgumentException("counterparty.legalName is required");
    if (c.counterparty().country() == null || !c.counterparty().country().matches("[A-Z]{2}"))
      throw new IllegalArgumentException(
          "counterparty.country must be an uppercase ISO country code");
  }

  private static BigDecimal amount(BigDecimal value, String field) {
    if (value == null || value.signum() < 0 || value.scale() > 4)
      throw new IllegalArgumentException(
          field + " must be a non-negative decimal with at most 4 places");
    return value;
  }

  private static LocalDate date(String value, String field) {
    if (value == null) return null;
    try {
      return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException(field + " must use YYYY-MM-DD", exception);
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

  private static boolean knownRecognitionConflict(DataIntegrityViolationException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
        String name = violation.getConstraintName();
        return "uq_ryczalt_candidate_source".equals(name)
            || "uq_ryczalt_source_reference".equals(name);
      }
      cause = cause.getCause();
    }
    return false;
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
      String direction,
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
      ApprovalMethod approvalMethod,
      PaymentVerificationPolicy paymentVerificationPolicy,
      RuleMatchResult.Kind ruleMatchStatus,
      InvoicePaymentStatus paymentStatus,
      SourceState sourceState,
      int periodYear,
      int periodMonth,
      List<RequiredInput> requiredInputs) {}

  public enum SourceState {
    NEW_CANDIDATE,
    EXISTING_CANDIDATE
  }

  public record ManualCandidateCommand(
      InvoiceDirection direction,
      String reference,
      LocalDate issueDate,
      LocalDate saleDate,
      LocalDate dueDate,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      ManualCounterparty counterparty) {
    public static ManualCandidateCommand of(
        String direction,
        String reference,
        String issueDate,
        String saleDate,
        String dueDate,
        String currency,
        String netAmount,
        String vatAmount,
        String grossAmount,
        ManualCounterparty counterparty) {
      return new ManualCandidateCommand(
          enumValue(direction, "direction"),
          reference,
          date(issueDate, "issueDate"),
          date(saleDate, "saleDate"),
          date(dueDate, "dueDate"),
          currency,
          decimal(netAmount, "netAmount"),
          decimal(vatAmount, "vatAmount"),
          decimal(grossAmount, "grossAmount"),
          counterparty);
    }

    String identity() {
      return String.join(
          "\u001f",
          direction.name(),
          reference,
          issueDate.toString(),
          String.valueOf(saleDate),
          String.valueOf(dueDate),
          currency,
          netAmount.toPlainString(),
          vatAmount.toPlainString(),
          grossAmount.toPlainString(),
          counterparty == null ? "null" : counterparty.identity());
    }

    private static InvoiceDirection enumValue(String value, String field) {
      try {
        return InvoiceDirection.valueOf(value);
      } catch (Exception exception) {
        throw new IllegalArgumentException(field + " must be COST");
      }
    }

    private static BigDecimal decimal(String value, String field) {
      if (value == null || !value.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,4})?"))
        throw new IllegalArgumentException(field + " must be an exact decimal string");
      try {
        return new BigDecimal(value);
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException(field + " must be an exact decimal string", exception);
      }
    }
  }

  public record ManualCounterparty(String legalName, String taxIdentifier, String country) {
    String identity() {
      return String.join(
          "\u001f",
          String.valueOf(legalName),
          String.valueOf(taxIdentifier),
          String.valueOf(country));
    }
  }
}
