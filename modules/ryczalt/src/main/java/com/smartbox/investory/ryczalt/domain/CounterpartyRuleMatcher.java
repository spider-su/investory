package com.smartbox.investory.ryczalt.domain;

import java.util.List;
import java.util.Objects;

/** Pure deterministic matcher. A rule matches only on every criterion it declares. */
public final class CounterpartyRuleMatcher {
  public RuleMatchResult match(InvoiceCandidate candidate, List<CounterpartyRule> rules) {
    List<CounterpartyRule> matches =
        rules.stream().filter(rule -> matches(candidate, rule)).toList();
    return matches.isEmpty()
        ? RuleMatchResult.noMatch()
        : matches.size() == 1
            ? new RuleMatchResult(RuleMatchResult.Kind.MATCHED, matches.getFirst())
            : RuleMatchResult.ambiguous();
  }

  private boolean matches(InvoiceCandidate c, CounterpartyRule r) {
    return c.counterpartyId() == r.counterpartyId()
        && criterion(r.sourceType(), c.sourceType())
        && criterion(r.documentType(), c.documentType())
        && criterion(r.serviceKey(), c.serviceKey());
  }

  private boolean criterion(String rule, String candidate) {
    return rule == null || (candidate != null && Objects.equals(rule, candidate));
  }
}
