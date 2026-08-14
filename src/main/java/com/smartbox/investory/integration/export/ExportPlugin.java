package com.smartbox.investory.integration.export;

import com.smartbox.investory.integration.IntegrationPlugin;
import com.smartbox.investory.integration.PluginConfig;
import java.io.IOException;

/** Port for exports. Concrete export request/result contracts are added per export format. */
public interface ExportPlugin extends IntegrationPlugin {
  void export(String target, PluginConfig config) throws IOException;
}
