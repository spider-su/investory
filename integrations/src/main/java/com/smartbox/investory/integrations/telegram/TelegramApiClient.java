package com.smartbox.investory.integrations.telegram;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class TelegramApiClient {
  private final RestClient client =
      RestClient.builder().baseUrl("https://api.telegram.org").build();

  void send(String token, String chatId, String message) {
    client
        .post()
        .uri("/bot" + token + "/sendMessage")
        .body(Map.of("chat_id", chatId, "text", message))
        .retrieve()
        .toBodilessEntity();
  }
}
