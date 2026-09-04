package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SandboxSimulationFormValidationTest {
  private Validator validator;
  private ValidatorFactory factory;

  @BeforeAll
  void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  void tearDown() {
    factory.close();
  }

  @Test
  void rejectsInvalidAgeOrderAndNegativeMoneyAtTheWebBoundary() {
    var form = new SandboxSimulationForm();
    form.setCurrentAge(70);
    form.setRetirementAge(65);
    form.setAnnualSpending(new BigDecimal("-1"));

    var violations = validator.validate(form);

    assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Current age")));
    assertTrue(
        violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("annualSpending")));
  }

  @Test
  void usesScreenshotSandboxDefaults() {
    var form = new SandboxSimulationForm();

    assertEquals(40, form.getCurrentAge());
    assertEquals(42, form.getRetirementAge());
    assertEquals(80, form.getEndAge());
    assertEquals(new BigDecimal("240000"), form.getAnnualSpending());
    assertEquals(new BigDecimal("0.021"), form.getInflationRate());
    assertEquals(new BigDecimal("120000"), form.getCash());
    assertEquals(new BigDecimal("750000"), form.getBonds());
    assertEquals(new BigDecimal("0.040"), form.getBondReturnRate());
    assertEquals(new BigDecimal("750000"), form.getEquities());
    assertEquals(new BigDecimal("0.080"), form.getEquityReturnRate());
    assertEquals(new BigDecimal("14500"), form.getMonthlyRentalIncome());
    assertEquals(new BigDecimal("5000"), form.getMonthlyPensionIncome());
    assertEquals(67, form.getPensionAge());
  }

  @Test
  void rejectsRatesOutsideSaneBoundary() {
    var form = new SandboxSimulationForm();
    form.setEquityReturnRate(new BigDecimal("11"));

    assertTrue(
        validator.validateProperty(form, "equityReturnRate").stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("equityReturnRate")));
  }
}
