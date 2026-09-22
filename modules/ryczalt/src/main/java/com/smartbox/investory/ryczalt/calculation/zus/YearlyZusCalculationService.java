package com.smartbox.investory.ryczalt.calculation.zus;

import org.springframework.stereotype.Service;

/** Resolves the annual rule set before applying the common ZUS formula. */
@Service
public class YearlyZusCalculationService {
  private final ZusRuleSetService rules;
  private final ZusCalculator calculator;

  public YearlyZusCalculationService(ZusRuleSetService rules) {
    this(rules, new ZusCalculator());
  }

  YearlyZusCalculationService(ZusRuleSetService rules, ZusCalculator calculator) {
    this.rules = rules;
    this.calculator = calculator;
  }

  public ZusCalculationResult calculate(int year, ZusCalculationInput input) {
    return calculator.calculate(input, rules.forYear(year));
  }
}
