package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.RyczaltCounterpartyService;
import com.smartbox.investory.ryczalt.application.RyczaltCounterpartyService.RuleCommand;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profiles/{profileId}/accounting/counterparties")
public class RyczaltCounterpartyRestController {
  private final RyczaltCounterpartyService service;
  private final AuthorizationService authorization;

  public RyczaltCounterpartyRestController(
      RyczaltCounterpartyService service, AuthorizationService authorization) {
    this.service = service;
    this.authorization = authorization;
  }

  @GetMapping
  public List<CounterpartyResponse> list(@PathVariable long profileId, Authentication a) {
    read(profileId, a);
    return service.list(profileId).stream().map(CounterpartyResponse::of).toList();
  }

  @GetMapping("/{id}")
  public CounterpartyResponse get(
      @PathVariable long profileId, @PathVariable long id, Authentication a) {
    read(profileId, a);
    return CounterpartyResponse.of(service.get(profileId, id));
  }

  @PutMapping("/{id}/alias")
  public CounterpartyResponse alias(
      @PathVariable long profileId,
      @PathVariable long id,
      @RequestBody AliasRequest r,
      Authentication a) {
    write(profileId, a);
    return CounterpartyResponse.of(service.setAlias(profileId, id, r.alias()));
  }

  @GetMapping("/{id}/rules")
  public List<CounterpartyRuleResponse> rules(
      @PathVariable long profileId, @PathVariable long id, Authentication a) {
    read(profileId, a);
    return service.rules(profileId, id).stream().map(CounterpartyRuleResponse::of).toList();
  }

  @PostMapping("/{id}/rules")
  public CounterpartyRuleResponse add(
      @PathVariable long profileId,
      @PathVariable long id,
      @RequestBody RuleRequest r,
      Authentication a) {
    write(profileId, a);
    return CounterpartyRuleResponse.of(service.addRule(profileId, id, r.command()));
  }

  @PutMapping("/{id}/rules/{ruleId}")
  public CounterpartyRuleResponse update(
      @PathVariable long profileId,
      @PathVariable long id,
      @PathVariable long ruleId,
      @RequestBody RuleRequest r,
      Authentication a) {
    write(profileId, a);
    return CounterpartyRuleResponse.of(service.updateRule(profileId, id, ruleId, r.command()));
  }

  @DeleteMapping("/{id}/rules/{ruleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable long profileId,
      @PathVariable long id,
      @PathVariable long ruleId,
      Authentication a) {
    write(profileId, a);
    service.deleteRule(profileId, id, ruleId);
  }

  private void read(long p, Authentication a) {
    if (!authorization.canRead(p, a)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  private void write(long p, Authentication a) {
    if (!authorization.canWrite(p, a)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  public record AliasRequest(String alias) {}

  public record RuleRequest(
      String name,
      String sourceType,
      String documentType,
      String serviceKey,
      String classification,
      String vatTreatment,
      BigDecimal vatDeductionRatio,
      BigDecimal ryczaltRate,
      boolean autoApprove,
      PaymentVerificationPolicy paymentVerificationPolicy) {
    RuleCommand command() {
      return new RuleCommand(
          name,
          sourceType,
          documentType,
          serviceKey,
          classification,
          vatTreatment,
          vatDeductionRatio,
          ryczaltRate,
          autoApprove,
          paymentVerificationPolicy);
    }
  }
}
