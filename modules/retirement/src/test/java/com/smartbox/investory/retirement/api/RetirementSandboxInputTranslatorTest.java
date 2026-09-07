package com.smartbox.investory.retirement.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.retirement.api.model.SandboxSimulationInput;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RetirementSandboxInputTranslatorTest {
  @Test
  void translatesAllSandboxCashFlowAndReturnInputsAtOneBoundary() {
    SandboxSimulationInput input =
        new SandboxSimulationInput(
            40,
            50,
            90,
            new BigDecimal("120000"),
            new BigDecimal("0.025"),
            new BigDecimal("10000"),
            new BigDecimal("20000"),
            new BigDecimal("0.04"),
            new BigDecimal("30000"),
            new BigDecimal("0.06"),
            new BigDecimal("1000"),
            new BigDecimal("2000"),
            67,
            2026);

    var assumptions = RetirementSandboxInputTranslator.toAssumptions(input);

    assertEquals(input.annualSpending(), assumptions.annualSpending());
    assertEquals(input.inflationRate(), assumptions.inflationRate());
    assertEquals(input.bondReturnRate(), assumptions.fixedIncomeReturnRate());
    assertEquals(input.equityReturnRate(), assumptions.equityReturnRate());
    assertEquals(input.retirementAge(), assumptions.retirementAge());
    assertEquals(input.pensionAge(), assumptions.pensionStartAge());
    assertEquals(new BigDecimal("24000"), assumptions.annualPension());
    assertEquals(0, assumptions.effectiveRentalIncomeGrowthRate().signum());
    assertEquals(input.inflationRate(), assumptions.effectiveSpendingGrowthRate());
  }
}
