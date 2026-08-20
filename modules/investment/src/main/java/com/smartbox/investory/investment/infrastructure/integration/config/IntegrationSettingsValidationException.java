package com.smartbox.investory.investment.infrastructure.integration.config;

import java.util.List;

public class IntegrationSettingsValidationException extends RuntimeException {
  private final List<IntegrationConfigurationValidator.FieldError> errors;

  public IntegrationSettingsValidationException(
      List<IntegrationConfigurationValidator.FieldError> errors) {
    super("Invalid integration configuration");
    this.errors = List.copyOf(errors);
  }

  public List<IntegrationConfigurationValidator.FieldError> errors() {
    return errors;
  }
}
