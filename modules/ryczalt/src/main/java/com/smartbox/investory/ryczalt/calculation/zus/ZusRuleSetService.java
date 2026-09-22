package com.smartbox.investory.ryczalt.calculation.zus;

import com.smartbox.investory.ryczalt.persistence.RyczaltZusRuleSetJpaRepository;
import org.springframework.stereotype.Service;

@Service
public class ZusRuleSetService {
  private final RyczaltZusRuleSetJpaRepository rules;

  public ZusRuleSetService(RyczaltZusRuleSetJpaRepository rules) {
    this.rules = rules;
  }

  public ZusRuleSet forYear(int year) {
    return resolveForYear(year).ruleSet();
  }

  public Resolution resolveForYear(int year) {
    return rules
        .findFirstByYearLessThanEqualOrderByYearDesc(year)
        .map(entity -> new Resolution(entity.ruleSet(), entity.ruleSet().year() != year))
        .orElseThrow(
            () -> new IllegalArgumentException("No ZUS rule set on or before year " + year));
  }

  public record Resolution(ZusRuleSet ruleSet, boolean fallback) {}
}
