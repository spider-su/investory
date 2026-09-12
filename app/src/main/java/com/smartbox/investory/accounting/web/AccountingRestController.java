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
  public Object months(@PathVariable long profileId, Authentication a) {
    read(profileId, a);
    return accounting.months(profileId);
  }

  @GetMapping("/months/{month}/overview")
  public Object overview(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.overview(profileId, month);
  }

  @GetMapping("/months/{month}/issues")
  public Object issues(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.issues(profileId, month);
  }

  @GetMapping("/months/{month}/documents")
  public Object documents(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.documents(profileId, month);
  }

  @GetMapping("/months/{month}/documents/{documentId}")
  public Object document(
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
  public Object bank(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.bankTransactions(profileId, month);
  }

  @GetMapping("/months/{month}/payments")
  public Object payments(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.payments(profileId, month);
  }

  @GetMapping("/months/{month}/filings")
  public Object filings(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.filings(profileId, month);
  }

  @GetMapping("/months/{month}/reconciliation")
  public Object reconciliation(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication a) {
    read(profileId, a);
    return accounting.reconciliation(profileId, month);
  }

  @PostMapping(value = "/documents/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object recognize(
      @PathVariable long profileId, @RequestPart MultipartFile file, Authentication a)
      throws java.io.IOException {
    write(profileId, a);
    return accounting.recognize(
        profileId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
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
    accounting.importBank(
        profileId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), month);
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
}
