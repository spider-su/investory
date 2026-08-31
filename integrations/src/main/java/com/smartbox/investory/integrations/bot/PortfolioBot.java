package com.smartbox.investory.integrations.bot;

import com.smartbox.investory.integrations.openai.OpenAiChatService;
import com.smartbox.investory.investment.api.importing.InvestmentImportApi;
import com.smartbox.investory.investment.api.importing.InvestmentImportApi.ImportResult;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true")
public class PortfolioBot extends TelegramLongPollingBot {

  static final long MAX_DOWNLOAD_SIZE_BYTES = 20L * 1024 * 1024;
  static final int MAX_MESSAGE_LENGTH = 4096;
  private static final Pattern IBKR_TRANSACTION_FILE = Pattern.compile("^u\\d+\\..*\\.csv$");

  @Value("${app.telegram.chat-id:}")
  private String chatId;

  @Value("${app.telegram.bot-username:}")
  private String botUsername;

  private final InvestmentImportApi investmentImports;
  private final OpenAiChatService openAiChatService;
  private final PortfolioCommandRouter portfolioCommandRouter;

  public PortfolioBot(
      @Value("${app.telegram.bot-token:}") String botToken,
      InvestmentImportApi investmentImports,
      OpenAiChatService openAiChatService,
      PortfolioCommandRouter portfolioCommandRouter) {
    super(botToken);
    this.investmentImports = investmentImports;
    this.openAiChatService = openAiChatService;
    this.portfolioCommandRouter = portfolioCommandRouter;
  }

  @Override
  public String getBotUsername() {
    return botUsername;
  }

  @Override
  public void onUpdateReceived(Update update) {
    if (!update.hasMessage()) {
      return;
    }
    Message message = update.getMessage();
    String replyChatId = message.getChatId().toString();
    if (!isAuthorized(chatId, replyChatId)) {
      log.warn("Ignoring Telegram update from unauthorized chat {}", replyChatId);
      sendTo(replyChatId, "This chat is not authorized to use Investory.");
      return;
    }

    if (message.hasDocument()) {
      handleDocument(message, replyChatId);
      return;
    }

    if (message.hasText()) {
      String text = message.getText();
      if (isStartCommand(text, botUsername)) {
        sendTo(replyChatId, portfolioCommandRouter.help());
        return;
      }
      if (isResetCommand(text, botUsername)) {
        openAiChatService.resetConversation(replyChatId);
        sendTo(replyChatId, "Conversation context cleared.");
        return;
      }

      var deterministicReply = portfolioCommandRouter.route(text, botUsername);
      if (deterministicReply.isPresent()) {
        sendTo(replyChatId, deterministicReply.get());
        return;
      }

      sendTo(replyChatId, openAiChatService.reply(replyChatId, text));
    }
  }

  static boolean isAuthorized(String configuredChatId, String candidateChatId) {
    return configuredChatId != null
        && !configuredChatId.isBlank()
        && configuredChatId.trim().equals(candidateChatId);
  }

  static boolean isStartCommand(String text, String configuredBotUsername) {
    if (text == null) {
      return false;
    }
    String command = text.trim();
    return "/start".equals(command)
        || (configuredBotUsername != null
            && !configuredBotUsername.isBlank()
            && ("/start@" + configuredBotUsername).equalsIgnoreCase(command));
  }

  static boolean isResetCommand(String text, String configuredBotUsername) {
    if (text == null) {
      return false;
    }
    String command = text.trim();
    return "/reset".equals(command)
        || (configuredBotUsername != null
            && !configuredBotUsername.isBlank()
            && ("/reset@" + configuredBotUsername).equalsIgnoreCase(command));
  }

  private void handleDocument(Message message, String replyChatId) {
    Document document = message.getDocument();
    String fileName = document.getFileName() != null ? document.getFileName() : "upload";
    Long fileSize = document.getFileSize();
    if (isDownloadTooLarge(fileSize)) {
      sendTo(
          replyChatId,
          "File is too large for Telegram bot download: "
              + fileName
              + ". Maximum supported size is 20 MB.");
      return;
    }

    String broker = detectBroker(fileName);
    if (broker == null) {
      sendTo(
          replyChatId,
          "Could not detect broker from file name: "
              + fileName
              + ". Use an XTB *.xlsx or IBKR activity CSV export.");
      return;
    }

    try {
      byte[] bytes = downloadDocumentBytes(document);
      ImportResult result =
          investmentImports.importForBroker(broker, fileName, bytes, "TELEGRAM", replyChatId);
      sendTo(replyChatId, formatImportSummary(result));
    } catch (Exception e) {
      log.warn("Telegram import failed for {}", fileName, e);
      String messageText = e.getMessage();
      sendTo(
          replyChatId,
          "Import failed"
              + (messageText == null || messageText.isBlank() ? "." : ": " + messageText));
    }
  }

  static boolean isDownloadTooLarge(Long fileSize) {
    return fileSize != null && fileSize > MAX_DOWNLOAD_SIZE_BYTES;
  }

  private byte[] downloadDocumentBytes(Document document) throws TelegramApiException, IOException {
    GetFile getFile = new GetFile(document.getFileId());
    File telegramFile = execute(getFile);
    java.io.File local = downloadFile(telegramFile);
    try (FileInputStream in = new FileInputStream(local)) {
      return in.readAllBytes();
    } finally {
      if (!local.delete()) {
        local.deleteOnExit();
      }
    }
  }

  static String detectBroker(String fileName) {
    if (fileName == null) {
      return null;
    }
    String lower = fileName.toLowerCase(Locale.ROOT);
    if (lower.contains("xtb")) {
      return "XTB";
    }
    if (lower.contains("ibkr")) {
      return "IBKR";
    }
    if (IBKR_TRANSACTION_FILE.matcher(lower).matches()) {
      return "IBKR";
    }
    if (lower.endsWith(".xlsx")) {
      return "XTB";
    }
    return null;
  }

  private String formatImportSummary(ImportResult r) {
    StringBuilder sb = new StringBuilder();
    sb.append(r.duplicate() ? "Already imported." : "Import complete.").append('\n');
    sb.append("Broker: ").append(r.broker()).append('\n');
    sb.append("Status: ").append(r.status()).append('\n');
    sb.append("Rows total/applied/failed: ")
        .append(r.rowsTotal())
        .append('/')
        .append(r.rowsApplied())
        .append('/')
        .append(r.rowsFailed());
    if (r.message() != null && !r.message().isBlank()) {
      sb.append('\n').append(r.message());
    }
    return sb.toString();
  }

  private void sendTo(String targetChatId, String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    for (int start = 0; start < text.length(); start += MAX_MESSAGE_LENGTH) {
      int end = Math.min(start + MAX_MESSAGE_LENGTH, text.length());
      try {
        execute(new SendMessage(targetChatId, text.substring(start, end)));
      } catch (TelegramApiException e) {
        log.warn("Failed to send Telegram message", e);
        return;
      }
    }
  }

  public void sendMessage(String data) {
    if (chatId == null || chatId.isBlank()) {
      log.debug("Telegram chat-id not configured; skipping message");
      return;
    }
    sendTo(chatId.trim(), data);
  }

  /** Delivery-outbox entry point. Returns only after Telegram confirms every message chunk. */
  public void sendMessageConfirmed(String data) {
    if (chatId == null || chatId.isBlank()) {
      throw new IllegalStateException("Telegram chat-id is not configured");
    }
    for (int start = 0; start < data.length(); start += MAX_MESSAGE_LENGTH) {
      int end = Math.min(start + MAX_MESSAGE_LENGTH, data.length());
      try {
        execute(new SendMessage(chatId.trim(), data.substring(start, end)));
      } catch (TelegramApiException exception) {
        throw new IllegalStateException("Telegram rejected notification delivery", exception);
      }
    }
  }
}
