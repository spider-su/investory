package com.smartbox.investory.integrations.management.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.integrations.ai.openai.OpenAiIntegrationPlugin;
import com.smartbox.investory.integrations.telegram.TelegramIntegrationPlugin;
import org.junit.jupiter.api.Test;

class IntegrationPluginContractTest {
  @Test
  void notificationAndAiPluginsUseInstanceEnablementOnly() {
    assertThat(new TelegramIntegrationPlugin().descriptor().configuration())
        .noneMatch(field -> field.key().equals("enabled"));
    assertThat(new OpenAiIntegrationPlugin().descriptor().configuration())
        .noneMatch(field -> field.key().equals("enabled"));
  }
}
