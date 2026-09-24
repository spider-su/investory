package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.RyczaltNativeMonthInputService;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profiles/{profileId}/accounting/periods/{month}/input-settings")
public class RyczaltNativeMonthInputRestController {
  private final RyczaltNativeMonthInputService inputs;
  private final AuthorizationService authorization;

  public RyczaltNativeMonthInputRestController(
      RyczaltNativeMonthInputService inputs, AuthorizationService authorization) {
    this.inputs = inputs;
    this.authorization = authorization;
  }

  @PutMapping
  public ResponseEntity<Void> save(
      @PathVariable long profileId,
      @PathVariable YearMonth month,
      @RequestBody InputRequest request,
      Authentication authentication) {
    if (!authorization.canWrite(profileId, authentication))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN);
    inputs.save(profileId, month, request.command());
    return ResponseEntity.noContent().build();
  }

  public record InputRequest(
      boolean jdgActive,
      boolean qualifyingUop,
      String zusRegime,
      boolean voluntarySickness,
      BigDecimal ytdRyczaltRevenue,
      BigDecimal fullJdgSocial,
      BigDecimal socialContributionDeduction,
      BigDecimal healthContributionOverride,
      BigDecimal healthContributionPaidOverride,
      BigDecimal deductionsAlreadyConsumed,
      BigDecimal salesCorrections,
      BigDecimal explicitVatAdjustments) {
    RyczaltNativeMonthInputService.Command command() {
      return new RyczaltNativeMonthInputService.Command(
          jdgActive,
          qualifyingUop,
          zusRegime,
          voluntarySickness,
          ytdRyczaltRevenue,
          fullJdgSocial,
          socialContributionDeduction,
          healthContributionOverride,
          healthContributionPaidOverride,
          deductionsAlreadyConsumed,
          salesCorrections,
          explicitVatAdjustments);
    }
  }
}
