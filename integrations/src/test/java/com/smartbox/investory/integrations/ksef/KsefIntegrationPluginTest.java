package com.smartbox.investory.integrations.ksef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import org.junit.jupiter.api.Test;

class KsefIntegrationPluginTest {
  private final KsefIntegrationPlugin plugin = new KsefIntegrationPlugin(null);

  @Test
  void exposesEInvoicingPluginWithInvoiceSyncJob() {
    assertEquals("ksef", plugin.id());
    assertEquals(IntegrationType.E_INVOICING, plugin.type());
    assertTrue(plugin.descriptor().jobs().contains("sync-invoices"));
  }
}
