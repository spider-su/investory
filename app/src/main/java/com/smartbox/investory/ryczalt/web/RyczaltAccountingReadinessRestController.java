package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.onboarding.RyczaltReadiness;
import com.smartbox.investory.ryczalt.application.onboarding.RyczaltReadinessService;
import java.time.YearMonth;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/profiles/{profileId}/accounting")
public class RyczaltAccountingReadinessRestController {
  private final AuthorizationService authorization;
  private final RyczaltReadinessService readiness;

  public RyczaltAccountingReadinessRestController(AuthorizationService authorization, RyczaltReadinessService readiness) {
    this.authorization = authorization;
    this.readiness = readiness;
  }

  @GetMapping("/readiness")
  public RyczaltReadiness get(@PathVariable long profileId, @RequestParam YearMonth month, Authentication authentication) {
    read(profileId, authentication);
    return readiness.get(profileId, month);
  }

  @PostMapping("/periods/{month}/activity-confirmation")
  public RyczaltReadiness.NoActivityConfirmation confirm(
      @PathVariable long profileId, @PathVariable YearMonth month, @RequestBody ActivityConfirmation request,
      Authentication authentication) {
    write(profileId, authentication);
    if (request == null || !"NO_REVENUE".equals(request.type())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "activity_confirmation_invalid");
    return readiness.confirmNoActivity(profileId, month, authentication == null ? null : authentication.getName());
  }

  private void read(long profileId, Authentication authentication) {
    if (!authorization.canRead(profileId, authentication)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  private void write(long profileId, Authentication authentication) {
    if (!authorization.canWrite(profileId, authentication)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  public record ActivityConfirmation(String type) {}
}
