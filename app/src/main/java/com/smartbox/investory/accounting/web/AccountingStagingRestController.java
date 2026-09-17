package com.smartbox.investory.accounting.web;

import com.smartbox.investory.accounting.api.AccountingStagingApi;
import com.smartbox.investory.config.AuthorizationService;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles/{profileId}/accounting")
public class AccountingStagingRestController {
  private final AccountingStagingApi staging;
  private final AuthorizationService authorization;

  public AccountingStagingRestController(
      @Qualifier("accountingStagingFacade") AccountingStagingApi staging,
      AuthorizationService authorization) {
    this.staging = staging;
    this.authorization = authorization;
  }

  @GetMapping("/months/{month}/staging")
  public java.util.List<AccountingStagingApi.Row> rows(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication auth) {
    read(profileId, auth);
    return staging.rows(profileId, month);
  }

  @GetMapping("/months/{month}/staging/reconciliation")
  public AccountingStagingApi.Summary summary(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication auth) {
    read(profileId, auth);
    return staging.summary(profileId, month);
  }

  @PostMapping("/months/{month}/staging/reconcile")
  public AccountingStagingApi.Summary reconcile(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication auth) {
    write(profileId, auth);
    return staging.reconcile(profileId, month);
  }

  @PostMapping("/months/{month}/staging/promote")
  public AccountingStagingApi.Promotion promote(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication auth) {
    write(profileId, auth);
    return staging.promote(profileId, month);
  }

  private void read(long profileId, Authentication auth) {
    if (!authorization.canRead(profileId, auth))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN);
  }

  private void write(long profileId, Authentication auth) {
    if (!authorization.canWrite(profileId, auth))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN);
  }
}
