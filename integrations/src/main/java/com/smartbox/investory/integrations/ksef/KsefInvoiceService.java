package com.smartbox.investory.integrations.ksef;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Application-facing KSeF invoice access using the configured managed integration. */
@Service
@RequiredArgsConstructor
public class KsefInvoiceService {
  private final IntegrationConfigurationService configurationService;
  private final KsefIntegrationPlugin plugin;
  private final KsefClient client;

  public String queryIncomingInvoices(
      OffsetDateTime from, OffsetDateTime to, int pageOffset, int pageSize) {
    PluginConfig config = configuration();
    KsefClient.KsefAccess access = authenticate(config);
    return client.queryIncomingInvoices(
        plugin.environment(config), access.accessToken(), from, to, pageOffset, pageSize);
  }

  public String downloadInvoice(String ksefNumber) {
    PluginConfig config = configuration();
    KsefClient.KsefAccess access = authenticate(config);
    return client.downloadInvoice(plugin.environment(config), access.accessToken(), ksefNumber);
  }

  public String getSessionStatus(String sessionReferenceNumber) {
    PluginConfig config = configuration();
    KsefClient.KsefAccess access = authenticate(config);
    return client.getSessionStatus(
        plugin.environment(config), access.accessToken(), sessionReferenceNumber);
  }

  public String listSessions(String sessionType, int pageSize, String continuationToken) {
    PluginConfig config = configuration();
    KsefClient.KsefAccess access = authenticate(config);
    return client.listSessions(
        plugin.environment(config), access.accessToken(), sessionType, pageSize, continuationToken);
  }

  public byte[] downloadInvoiceUpo(String sessionReferenceNumber, String ksefNumber) {
    PluginConfig config = configuration();
    KsefClient.KsefAccess access = authenticate(config);
    return client.downloadSessionInvoiceUpoByKsefNumber(
        plugin.environment(config), access.accessToken(), sessionReferenceNumber, ksefNumber);
  }

  public byte[] downloadSessionUpo(String sessionReferenceNumber, String upoReferenceNumber) {
    PluginConfig config = configuration();
    KsefClient.KsefAccess access = authenticate(config);
    return client.downloadSessionUpo(
        plugin.environment(config),
        access.accessToken(),
        sessionReferenceNumber,
        upoReferenceNumber);
  }

  private KsefClient.KsefAccess authenticate(PluginConfig config) {
    return client.authenticateWithToken(
        plugin.environment(config),
        config.value(KsefIntegrationPlugin.NIP).orElseThrow(),
        config.value(KsefIntegrationPlugin.KSEF_TOKEN).orElseThrow());
  }

  private PluginConfig configuration() {
    return configurationService
        .resolveEnabledGlobal(IntegrationType.E_INVOICING, KsefIntegrationPlugin.ID)
        .orElseThrow(() -> new IllegalStateException("KSeF integration is not enabled"));
  }
}
