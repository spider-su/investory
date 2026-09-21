package com.smartbox.investory.ryczalt.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CounterpartyRuleMatcherTest {
  private final CounterpartyRuleMatcher matcher = new CounterpartyRuleMatcher();

  private CounterpartyRule rule(String service, boolean auto, PaymentVerificationPolicy policy) {
    return new CounterpartyRule(
        1L,
        10L,
        20L,
        "Fuel",
        "KSEF",
        "COST",
        service,
        "FUEL",
        "DOMESTIC_PURCHASE",
        null,
        null,
        auto,
        policy);
  }

  @Test
  void matchesAllDeclaredFacts() {
    var result =
        matcher.match(
            new InvoiceCandidate(20L, "KSEF", "COST", "FUEL"),
            List.of(rule("FUEL", true, PaymentVerificationPolicy.NOT_REQUIRED)));
    assertEquals(RuleMatchResult.Kind.MATCHED, result.kind());
    assertTrue(result.rule().autoApprove());
    assertEquals(PaymentVerificationPolicy.NOT_REQUIRED, result.rule().paymentVerificationPolicy());
  }

  @Test
  void missingOrDifferentServiceDoesNotMatch() {
    assertEquals(
        RuleMatchResult.Kind.NO_MATCH,
        matcher
            .match(
                new InvoiceCandidate(20L, "KSEF", "COST", "HOSTING"),
                List.of(rule("FUEL", true, PaymentVerificationPolicy.REQUIRED)))
            .kind());
  }

  @Test
  void overlappingRulesAreAmbiguous() {
    var result =
        matcher.match(
            new InvoiceCandidate(20L, "KSEF", "COST", "FUEL"),
            List.of(
                rule(null, true, PaymentVerificationPolicy.REQUIRED),
                rule("FUEL", true, PaymentVerificationPolicy.NOT_REQUIRED)));
    assertEquals(RuleMatchResult.Kind.AMBIGUOUS, result.kind());
    assertNull(result.rule());
  }

  @Test
  void counterpartyIdentityIsNotAlias() {
    var counterparty =
        new Counterparty(1, 10, "PL123", "PL", "POLSKI KONCERN NAFTOWY ORLEN S.A.", "Orlen");
    assertEquals("Orlen", counterparty.displayName());
    assertEquals("PL123", counterparty.taxIdentifier());
  }
}
