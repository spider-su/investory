package com.smartbox.investory.accounting.web;

import com.smartbox.investory.accounting.api.AccountingUserApi;
import com.smartbox.investory.config.AuthorizationService;
import java.time.YearMonth;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Versioned, read-only REST adapter for the mobile accounting client. */
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/accounting")
public class AccountingMobileRestController {
  private final AccountingUserApi accounting;
  private final AuthorizationService authorization;

  public AccountingMobileRestController(
      @Qualifier("accountingUserFacade") AccountingUserApi accounting,
      AuthorizationService authorization) {
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

  private void read(long profileId, Authentication authentication) {
    if (!authorization.canRead(profileId, authentication)) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN);
    }
  }
}
