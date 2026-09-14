package com.smartbox.investory.accounting.web;

import com.smartbox.investory.accounting.api.AccountingUserApi;
import com.smartbox.investory.config.AuthorizationService;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profiles/{profileId}/accounting")
public class AccountingRestController {
  private final AccountingUserApi accounting;
  private final AuthorizationService authorization;

  public AccountingRestController(
      @Qualifier("accountingUserFacade") AccountingUserApi accounting,
      AuthorizationService authorization) {
    this.accounting = accounting;
    this.authorization = authorization;
  }

  @GetMapping("/months")
  public java.util.List<AccountingUserApi.MonthRef> months(
      @PathVariable long profileId, Authentication a) {
    read(profileId, a);
    return accounting.months(profileId);
  }

  @GetMapping("/months/{month}/overview")
  public AccountingUserApi.MonthOverview overview(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.overview(profileId, month);
  }

  @GetMapping("/months/{month}/issues")
  public java.util.List<AccountingUserApi.IssueView> issues(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.issues(profileId, month);
  }

  @GetMapping("/months/{month}/documents")
  public java.util.List<AccountingUserApi.DocumentView> documents(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.documents(profileId, month);
  }

  @GetMapping("/months/{month}/documents/{documentId}")
  public AccountingUserApi.DocumentView document(
      @PathVariable long profileId,
      @PathVariable YearMonth month,
      @PathVariable long documentId,
      Authentication a) {
    read(profileId, a);
    return accounting.documents(profileId, month).stream()
        .filter(d -> d.id() == documentId)
        .findFirst()
        .orElseThrow(
            () ->
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND));
  }

  @GetMapping("/months/{month}/bank-transactions")
  public java.util.List<AccountingUserApi.BankTransactionView> bank(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.bankTransactions(profileId, month);
  }

  @GetMapping("/months/{month}/payments")
  public java.util.List<AccountingUserApi.PaymentView> payments(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.payments(profileId, month);
  }

  @GetMapping("/months/{month}/filings")
  public AccountingUserApi.FilingView filings(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.filings(profileId, month);
  }

  @PostMapping("/months/{month}/filings/jpk/generate")
  public AccountingUserApi.FilingArtifactView generateJpk(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    write(profileId, a);
    return accounting.generateJpk(profileId, month);
  }

  @GetMapping(value = "/months/{month}/filings/jpk", produces = MediaType.APPLICATION_XML_VALUE)
  public org.springframework.http.ResponseEntity<byte[]> downloadJpk(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    var artifact =
        accounting
            .filingArtifact(profileId, month)
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
    return org.springframework.http.ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_XML)
        .header(
            org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + artifact.filename() + "\"")
        .body(artifact.content());
  }

  @GetMapping("/months/{month}/filings/jpk/metadata")
  public AccountingUserApi.FilingArtifactView filingArtifact(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting
        .filingArtifact(profileId, month)
        .orElseThrow(
            () ->
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND));
  }

  @PostMapping("/months/{month}/filings/confirmations")
  public void recordConfirmation(
      @PathVariable long profileId,
      @PathVariable YearMonth month,
      @RequestBody AccountingUserApi.ConfirmationInput input,
      Authentication a) {
    write(profileId, a);
    if (input == null || !month.equals(input.taxPeriod()))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Confirmation period does not match");
    accounting.recordConfirmation(profileId, input);
  }

  @GetMapping("/months/{month}/reconciliation")
  public java.util.List<AccountingUserApi.ReconciliationView> reconciliation(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.reconciliation(profileId, month);
  }

  @PostMapping(value = "/documents/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AccountingUserApi.CandidateView recognize(
      @PathVariable long profileId, @RequestPart MultipartFile file, Authentication a)
      throws java.io.IOException {
    write(profileId, a);
    validateUpload(file, "application/pdf", "image/jpeg", "image/png", "image/webp");
    return accounting.recognize(
        profileId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
  }

  @GetMapping("/documents/review")
  public AccountingUserApi.CandidateView reviewSource(
      @PathVariable long profileId, @RequestParam String sourceReference, Authentication a) {
    read(profileId, a);
    return accounting.reviewSource(profileId, sourceReference);
  }

  @PostMapping("/documents")
  public void saveDocument(
      @PathVariable long profileId,
      @RequestBody AccountingUserApi.ReviewedDocument document,
      Authentication a) {
    write(profileId, a);
    accounting.saveReviewed(profileId, document);
  }

  @PostMapping(value = "/bank/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public void importBank(
      @PathVariable long profileId,
      @RequestParam YearMonth month,
      @RequestPart MultipartFile file,
      Authentication a)
      throws java.io.IOException {
    write(profileId, a);
    validateUpload(file, "text/csv", "application/csv", "application/vnd.ms-excel");
    accounting.importBank(
        profileId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), month);
  }

  @PostMapping("/ksef/sync")
  public AccountingUserApi.KsefSyncResult syncKsef(
      @PathVariable long profileId, @RequestParam YearMonth month, Authentication a) {
    write(profileId, a);
    return accounting.syncKsef(profileId, month);
  }

  @PostMapping("/ksef/reimport")
  public AccountingUserApi.KsefSyncResult reimportKsef(
      @PathVariable long profileId, @RequestParam YearMonth month, Authentication a) {
    write(profileId, a);
    return accounting.reimportKsef(profileId, month);
  }

  @PostMapping("/ksef/sync-seller")
  public AccountingUserApi.KsefSyncResult syncKsefSeller(
      @PathVariable long profileId, @RequestParam YearMonth month, Authentication a) {
    write(profileId, a);
    return accounting.syncKsefSeller(profileId, month);
  }

  @PostMapping("/ksef/sync-third-party")
  public AccountingUserApi.KsefSyncResult syncKsefThirdParty(
      @PathVariable long profileId, @RequestParam YearMonth month, Authentication a) {
    write(profileId, a);
    return accounting.syncKsefThirdParty(profileId, month);
  }

  @PostMapping("/months/{month}/confirm")
  public void confirm(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    write(profileId, a);
    accounting.confirm(profileId, month);
  }

  @PostMapping("/months/{month}/file")
  public void file(@PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    write(profileId, a);
    accounting.file(profileId, month);
  }

  @PostMapping("/months/{month}/settle")
  public void settle(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    write(profileId, a);
    accounting.settle(profileId, month);
  }

  @PostMapping("/months/{month}/lock")
  public void lock(@PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    write(profileId, a);
    accounting.lock(profileId, month);
  }

  @PostMapping("/months/{month}/reopen")
  public void reopen(
      @PathVariable long profileId,
      @PathVariable YearMonth month,
      @RequestParam String reason,
      Authentication a) {
    write(profileId, a);
    accounting.reopen(profileId, month, reason);
  }

  private void read(long p, Authentication a) {
    if (!authorization.canRead(p, a))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN);
  }

  private void write(long p, Authentication a) {
    if (!authorization.canWrite(p, a))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN);
  }

  private void validateUpload(MultipartFile file, String... contentTypes) {
    if (file == null || file.isEmpty())
      throw new IllegalArgumentException("Uploaded file is empty");
    if (file.getSize() > 12L * 1024 * 1024)
      throw new IllegalArgumentException("Uploaded file exceeds the 12 MB limit");
    String contentType = file.getContentType();
    if (contentType != null
        && java.util.Arrays.stream(contentTypes).noneMatch(contentType::equalsIgnoreCase))
      throw new IllegalArgumentException("Unsupported uploaded file type");
  }
}
