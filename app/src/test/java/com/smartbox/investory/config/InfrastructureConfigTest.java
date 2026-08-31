package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smartbox.investory.integrations.telegram.PortfolioBot;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

class InfrastructureConfigTest {
  @Test
  void asyncExecutorIsBoundedAndNamedForReconciliation() {
    var executor = new AsyncConfig().reconciliationRefreshExecutor();
    executor.initialize();
    try {
      assertThat(executor.getCorePoolSize()).isEqualTo(1);
      assertThat(executor.getMaxPoolSize()).isEqualTo(1);
      assertThat(executor.getThreadNamePrefix()).isEqualTo("reconciliation-refresh-");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void openApiDeclaresVersionAndBasicAuthentication() {
    var api = new OpenApiConfig().investoryOpenAPI();
    assertThat(api.getInfo().getTitle()).isEqualTo("Investory REST API");
    assertThat(api.getInfo().getVersion()).isEqualTo("v1");
    assertThat(api.getComponents().getSecuritySchemes()).containsKey("basicAuth");
    assertThat(api.getSecurity())
        .singleElement()
        .satisfies(item -> assertThat(item).containsKey("basicAuth"));
  }

  @Test
  void telegramRegistrationDelegatesAndTranslatesStartupFailure() throws Exception {
    TelegramBotsApi api = mock(TelegramBotsApi.class);
    PortfolioBot bot = mock(PortfolioBot.class);
    var registration = new TelegramBotConfig().telegramBotRegistration(api, bot);
    registration.afterSingletonsInstantiated();
    verify(api).registerBot(bot);

    doThrow(new TelegramApiException("offline")).when(api).registerBot(bot);
    assertThatThrownBy(registration::afterSingletonsInstantiated)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to register");
  }
}
