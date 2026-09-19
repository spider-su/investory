package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.domain.*;
import com.smartbox.investory.ryczalt.persistence.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RyczaltCounterpartyService {
  private final RyczaltCounterpartyJpaRepository counterparties;
  private final RyczaltCounterpartyRuleJpaRepository rules;
  private final CounterpartyRuleMatcher matcher = new CounterpartyRuleMatcher();

  public RyczaltCounterpartyService(
      RyczaltCounterpartyJpaRepository counterparties, RyczaltCounterpartyRuleJpaRepository rules) {
    this.counterparties = counterparties;
    this.rules = rules;
  }

  @Transactional(readOnly = true)
  public List<Counterparty> list(long profileId) {
    return counterparties.findByProfileIdOrderByLegalName(profileId).stream()
        .map(this::counterparty)
        .toList();
  }

  @Transactional(readOnly = true)
  public Counterparty get(long profileId, long id) {
    return counterparty(entity(profileId, id));
  }

  @Transactional
  public Counterparty setAlias(long profileId, long id, String alias) {
    var row = entity(profileId, id);
    row.setAlias(alias);
    return counterparty(counterparties.save(row));
  }

  @Transactional(readOnly = true)
  public List<CounterpartyRule> rules(long profileId, long counterpartyId) {
    entity(profileId, counterpartyId);
    return this.rules
        .findByProfileIdAndCounterpartyIdOrderByName(profileId, counterpartyId)
        .stream()
        .map(this::rule)
        .toList();
  }

  @Transactional
  public CounterpartyRule addRule(long profileId, long counterpartyId, RuleCommand c) {
    var cp = entity(profileId, counterpartyId);
    return rule(rules.save(c.entity(profileId, cp)));
  }

  @Transactional
  public CounterpartyRule updateRule(
      long profileId, long counterpartyId, long ruleId, RuleCommand c) {
    entity(profileId, counterpartyId);
    var row =
        rules
            .findByIdAndProfileIdAndCounterpartyId(ruleId, profileId, counterpartyId)
            .orElseThrow(() -> new IllegalArgumentException("Counterparty rule not found"));
    row.update(
        c.name(),
        c.sourceType(),
        c.documentType(),
        c.serviceKey(),
        c.classification(),
        c.vatTreatment(),
        c.vatDeductionRatio(),
        c.ryczaltRate(),
        c.autoApprove(),
        c.paymentVerificationPolicy());
    return rule(rules.save(row));
  }

  @Transactional
  public void deleteRule(long profileId, long counterpartyId, long ruleId) {
    rules.delete(
        rules
            .findByIdAndProfileIdAndCounterpartyId(ruleId, profileId, counterpartyId)
            .orElseThrow(() -> new IllegalArgumentException("Counterparty rule not found")));
  }

  @Transactional(readOnly = true)
  public RuleMatchResult match(InvoiceCandidate candidate, long profileId) {
    return matcher.match(candidate, rules(profileId, candidate.counterpartyId()));
  }

  private RyczaltCounterpartyEntity entity(long p, long id) {
    return counterparties
        .findByIdAndProfileId(id, p)
        .orElseThrow(() -> new IllegalArgumentException("Counterparty not found"));
  }

  private Counterparty counterparty(RyczaltCounterpartyEntity e) {
    return new Counterparty(
        e.id(),
        e.getProfileId(),
        e.getTaxIdentifier(),
        e.getCountry(),
        e.getLegalName(),
        e.getAlias());
  }

  private CounterpartyRule rule(RyczaltCounterpartyRuleEntity e) {
    return new CounterpartyRule(
        e.id(),
        e.getProfileId(),
        e.getCounterparty().id(),
        e.getName(),
        e.getSourceType(),
        e.getDocumentType(),
        e.getServiceKey(),
        e.getClassification(),
        e.getVatTreatment(),
        e.getVatDeductionRatio(),
        e.getRyczaltRate(),
        e.isAutoApprove(),
        e.getPaymentVerificationPolicy());
  }

  public record RuleCommand(
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
    RyczaltCounterpartyRuleEntity entity(long p, RyczaltCounterpartyEntity cp) {
      return new RyczaltCounterpartyRuleEntity(
          p,
          cp,
          name,
          sourceType,
          documentType,
          serviceKey,
          classification,
          vatTreatment,
          vatDeductionRatio,
          ryczaltRate,
          autoApprove,
          paymentVerificationPolicy == null
              ? PaymentVerificationPolicy.REQUIRED
              : paymentVerificationPolicy);
    }
  }
}
