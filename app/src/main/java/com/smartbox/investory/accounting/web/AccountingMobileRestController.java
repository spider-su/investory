package com.smartbox.investory.accounting.web;

import com.smartbox.investory.accounting.api.AccountingUserApi;
import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.RyczaltUserApi;
import java.time.YearMonth;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Versioned, read-only REST adapter for the mobile accounting client. */
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/accounting")
public class AccountingMobileRestController {
  private final RyczaltUserApi accounting;
  private final AuthorizationService authorization;

  public AccountingMobileRestController(
      @Qualifier("ryczaltUserApi") RyczaltUserApi accounting, AuthorizationService authorization) {
    this.accounting = accounting;
    this.authorization = authorization;
  }

  @GetMapping("/months/{month}")
  public AccountingMobileResponse month(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication authentication) {
    read(profileId, authentication);
    return AccountingMobileResponse.from(accounting.overview(profileId, month));
  }

  @GetMapping("/months/{month}/documents")
  public List<AccountingMobileResponse.Document> documents(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication authentication) {
    read(profileId, authentication);
    return accounting.documents(profileId, month).stream()
        .map(AccountingMobileResponse.Document::from)
        .toList();
  }

  @GetMapping("/payments/history")
  public List<AccountingUserApi.PaymentHistoryView> paymentHistory(
      @PathVariable long profileId,
      @org.springframework.web.bind.annotation.RequestParam YearMonth from,
      @org.springframework.web.bind.annotation.RequestParam YearMonth to,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String type,
      Authentication authentication) {
    read(profileId, authentication);
    return accounting.paymentHistory(profileId, from, to, type);
  }

  @GetMapping("/auto-approval")
  public AccountingUserApi.AutoApprovalSettings autoApproval(
      @PathVariable long profileId, Authentication authentication) {
    read(profileId, authentication);
    return accounting.autoApprovalSettings(profileId);
  }

  @PutMapping("/auto-approval")
  public AccountingUserApi.AutoApprovalSettings updateAutoApproval(
      @PathVariable long profileId,
      @RequestBody AutoApprovalRequest request,
      Authentication authentication) {
    if (!authorization.canWrite(profileId, authentication)) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN);
    }
    var settings =
        new AccountingUserApi.AutoApprovalSettings(
            request.enabled(), request.maxAmount(), request.trustedCategories());
    accounting.updateAutoApprovalSettings(profileId, settings);
    return settings;
  }

  public record AutoApprovalRequest(
      boolean enabled, java.math.BigDecimal maxAmount, List<String> trustedCategories) {}

  private void read(long profileId, Authentication authentication) {
    if (!authorization.canRead(profileId, authentication)) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN);
    }
  }
}
