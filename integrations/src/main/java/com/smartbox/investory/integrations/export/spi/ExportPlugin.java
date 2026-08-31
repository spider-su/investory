package com.smartbox.investory.integrations.export.spi;

import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.spi.IntegrationPlugin;
import java.io.IOException;

/** Port for exports. Concrete export request/result contracts are added per export format. */
public interface ExportPlugin extends IntegrationPlugin {
  void export(String target, PluginConfig config) throws IOException;
}
