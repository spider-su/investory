package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.onboarding.CompanyLookupResult;
import com.smartbox.investory.ryczalt.application.onboarding.RyczaltOnboarding;
import com.smartbox.investory.ryczalt.application.onboarding.RyczaltOnboardingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profiles/{profileId}/onboarding")
public class RyczaltOnboardingRestController {
  private final RyczaltOnboardingService onboarding;
  private final AuthorizationService authorization;

  public RyczaltOnboardingRestController(
      RyczaltOnboardingService onboarding, AuthorizationService authorization) {
    this.onboarding = onboarding;
    this.authorization = authorization;
  }

  @GetMapping
  public RyczaltOnboarding get(@PathVariable long profileId, Authentication authentication) {
    read(profileId, authentication);
    return onboarding.get(profileId);
  }

  @PostMapping("/company/lookup")
  public CompanyLookupResult lookup(
      @PathVariable long profileId,
      @RequestBody LookupRequest request,
      Authentication authentication) {
    write(profileId, authentication);
    if (request == null) throw badRequest("nip_required");
    return onboarding.lookupCompany(profileId, request.nip());
  }

  @PostMapping("/company/confirm")
  public RyczaltOnboarding confirmCompany(
      @PathVariable long profileId,
      @RequestBody RyczaltOnboardingService.CompanyConfirmation request,
      Authentication authentication) {
    write(profileId, authentication);
    return onboarding.confirmCompany(profileId, request);
  }

  @GetMapping("/configuration")
  public SupportedConfiguration configuration(
      @PathVariable long profileId, Authentication authentication) {
    read(profileId, authentication);
    return new SupportedConfiguration("JDG", "RYCZALT", "12%", "MONTHLY", "ACTIVE", "MONTHLY");
  }

  @GetMapping("/zus")
  public ZusOptions zus(@PathVariable long profileId, Authentication authentication) {
    read(profileId, authentication);
    return new ZusOptions(java.util.List.of("JDG"), java.util.List.of("REVENUE_BAND"), true);
  }

  @PostMapping("/accounting/confirm")
  public RyczaltOnboarding confirmAccounting(
      @PathVariable long profileId,
      @RequestBody RyczaltOnboardingService.AccountingConfirmation request,
      Authentication authentication) {
    write(profileId, authentication);
    return onboarding.confirmAccounting(profileId, request);
  }

  @PostMapping("/zus")
  public RyczaltOnboarding saveZus(
      @PathVariable long profileId,
      @RequestBody RyczaltOnboardingService.ZusConfirmation request,
      Authentication authentication) {
    write(profileId, authentication);
    return onboarding.saveZus(profileId, request);
  }

  @GetMapping("/ksef")
  public KsefStatus ksef(@PathVariable long profileId, Authentication authentication) {
    read(profileId, authentication);
    return new KsefStatus(onboarding.get(profileId).ksefState());
  }

  @PostMapping("/ksef/skip")
  public RyczaltOnboarding skipKsef(@PathVariable long profileId, Authentication authentication) {
    write(profileId, authentication);
    return onboarding.skipKsef(profileId);
  }

  @PostMapping("/complete")
  public RyczaltOnboarding complete(
      @PathVariable long profileId,
      @RequestBody CompleteRequest request,
      Authentication authentication) {
    write(profileId, authentication);
    return onboarding.complete(profileId, request == null ? "SKIPPED" : request.ksefState());
  }

  private void read(long profileId, Authentication authentication) {
    if (!authorization.canRead(profileId, authentication))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  private void write(long profileId, Authentication authentication) {
    if (!authorization.canWrite(profileId, authentication))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  private static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  public record LookupRequest(String nip) {}

  public record CompleteRequest(String ksefState) {}

  public record SupportedConfiguration(
      String legalForm,
      String taxationMethod,
      String ryczaltRate,
      String pitFrequency,
      String vatStatus,
      String vatFrequency) {}

  public record ZusOptions(
      java.util.List<String> regimes,
      java.util.List<String> healthMethods,
      boolean voluntarySicknessSupported) {}

  public record KsefStatus(String state) {}
}
