package com.smartbox.investory.config;

import com.smartbox.investory.integrations.telegram.TelegramAssistant;
import com.smartbox.investory.rag.FinancialAiOrchestratorController;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Connects the existing authorized Telegram bot to the same Smart Assistant used by the web UI.
 *
 * <p>The configured Telegram chat is mapped to an existing active Investory username. The shared
 * controller checks that user's actual profile membership before any financial read. This is not an
 * admin/service-account override. Never derive a username or portfolio ID from Telegram text.
 */
@Component
@ConditionalOnProperty(
    name = {"app.telegram.enabled", "investory.rag.enabled"},
    havingValue = "true")
public class TelegramRagAssistantAdapter implements TelegramAssistant {
  private final FinancialAiOrchestratorController orchestrator;
  private final String authorizedChat;
  private final String username;
  private final ConcurrentHashMap<String, FinancialAiOrchestratorController.Context> contexts =
      new ConcurrentHashMap<>();

  public TelegramRagAssistantAdapter(
      FinancialAiOrchestratorController orchestrator,
      @Value("${app.telegram.chat-id:}") String authorizedChat,
      @Value("${app.telegram.rag-username:}") String username) {
    this.orchestrator = orchestrator;
    this.authorizedChat = authorizedChat == null ? "" : authorizedChat.trim();
    this.username = username == null ? "" : username.trim();
  }

  @Override
  public String reply(long portfolioId, String chatId, String question) {
    if (authorizedChat.isBlank()
        || !authorizedChat.equals(chatId)
        || username.isBlank()
        || portfolioId <= 0) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Telegram Smart Assistant is not bound to an Investory account and portfolio");
    }
    // Scoped authorization in the orchestrator verifies this named user's live DB membership.
    // Do not use ROLE_ADMIN; synthetic authentication is available only behind the chat binding.
    var identity =
        UsernamePasswordAuthenticationToken.authenticated(
            username, "N/A", List.of(new SimpleGrantedAuthority("ROLE_PROFILE_USER")));
    var result =
        orchestrator.ask(
            portfolioId,
            new FinancialAiOrchestratorController.Question(question, contexts.get(chatId)),
            identity);
    if (result.context() == null) {
      contexts.remove(chatId);
    } else {
      contexts.put(chatId, result.context());
    }
    return result.answer();
  }

  @Override
  public void reset(String chatId) {
    if (authorizedChat.equals(chatId)) contexts.remove(chatId);
  }
}
