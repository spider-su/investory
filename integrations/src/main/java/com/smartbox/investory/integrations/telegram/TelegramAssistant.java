package com.smartbox.investory.integrations.telegram;

/**
 * Optional read-only Smart Assistant boundary for authorized Telegram conversations. The
 * application supplies the implementation so the integrations module never depends on app.
 */
public interface TelegramAssistant {
  String reply(long portfolioId, String chatId, String question);

  void reset(String chatId);
}
