package com.smartbox.investory.ryczalt.application.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NipValidatorTest {
  @Test
  void acceptsFormattedValidNip() {
    assertTrue(NipValidator.isValid("100-000-00-06"));
    assertEquals("1000000006", NipValidator.normalize("100-000-00-06"));
  }

  @Test
  void rejectsInvalidChecksumAndNonDigits() {
    assertFalse(NipValidator.isValid("1000000007"));
    assertFalse(NipValidator.isValid("abc"));
  }
}
