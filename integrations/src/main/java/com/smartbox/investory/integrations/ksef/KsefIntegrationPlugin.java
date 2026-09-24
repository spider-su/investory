package com.smartbox.investory.integrations.ksef;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.api.model.PluginFieldDescriptor;
import com.smartbox.investory.integrations.management.api.model.PluginFieldType;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.model.PluginDescriptor;
import com.smartbox.investory.integrations.management.model.ValidationResult;
import com.smartbox.investory.integrations.management.spi.IntegrationPlugin;
import com.smartbox.investory.integrations.management.spi.TestableIntegrationPlugin;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KsefIntegrationPlugin implements IntegrationPlugin, TestableIntegrationPlugin {
  public static final String ID = "ksef";
  public static final String ENVIRONMENT = "environment";
  public static final String NIP = "nip";
  public static final String KSEF_TOKEN = "ksefToken";

  private final KsefClient client;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public IntegrationType type() {
    return IntegrationType.E_INVOICING;
  }

  @Override
  public PluginDescriptor descriptor() {
    return new PluginDescriptor(
        ID,
        "Krajowy System e-Faktur (KSeF)",
        IntegrationType.E_INVOICING,
        List.of(
            new PluginFieldDescriptor(
                ENVIRONMENT,
                PluginFieldType.ENUM,
                true,
                KsefEnvironment.TEST.name(),
                List.of(
                    KsefEnvironment.TEST.name(),
                    KsefEnvironment.DEMO.name(),
                    KsefEnvironment.PRODUCTION.name()),
                "Environment",
                "KSeF 2.0 environment",
                null,
                null,
                null),
            new PluginFieldDescriptor(
                NIP,
                PluginFieldType.STRING,
                true,
                null,
                List.of(),
                "NIP",
                "Taxpayer NIP used as the KSeF authentication context",
                null,
                null,
                "\\d{10}"),
            new PluginFieldDescriptor(
                KSEF_TOKEN,
                PluginFieldType.SECRET,
                true,
                null,
                List.of(),
                "KSeF token",
                "KSeF authentication token (bootstrap authentication for 2026)",
                null,
                null,
                null)),
        List.of("sync-invoices"));
  }

  @Override
  public ValidationResult validate(PluginConfig config) {
    String nip = config.value(NIP).orElse("");
    if (!nip.matches("\\d{10}")) return ValidationResult.invalid("NIP must contain 10 digits");
    if (config.value(KSEF_TOKEN).isEmpty())
      return ValidationResult.invalid("KSeF token is required");
    try {
      environment(config);
    } catch (IllegalArgumentException e) {
      return ValidationResult.invalid("Unknown KSeF environment");
    }
    return ValidationResult.success();
  }

  @Override
  public ConnectionTestResult testConnection(PluginConfig config) {
    ValidationResult validation = validate(config);
    if (!validation.valid()) {
      return new ConnectionTestResult(false, false, String.join(", ", validation.errors()));
    }
    try {
      client.authenticateWithToken(
          environment(config),
          config.value(NIP).orElseThrow(),
          config.value(KSEF_TOKEN).orElseThrow());
      return new ConnectionTestResult(true, true, "KSeF authentication succeeded");
    } catch (RuntimeException e) {
      return new ConnectionTestResult(true, false, "KSeF connection or authentication failed");
    }
  }

  public KsefEnvironment environment(PluginConfig config) {
    return KsefEnvironment.parse(config.value(ENVIRONMENT).orElse(KsefEnvironment.TEST.name()));
  }
}
