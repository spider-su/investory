package com.example.demo.controllers.bot;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportSourceType;
import com.example.demo.services.imports.ImportBatchResponse;
import com.example.demo.services.imports.ImportOrchestratorService;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
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

    @Value("${app.telegram.chat-id:}")
    private String chatId;

    @Value("${app.telegram.bot-username:}")
    private String botUsername;

    private final ImportOrchestratorService importOrchestratorService;

    public PortfolioBot(
            @Value("${app.telegram.bot-token:}") String botToken,
            ImportOrchestratorService importOrchestratorService) {
        // Use the non-deprecated constructor that stores the token in the base
        // class; removes the need to override the deprecated getBotToken().
        super(botToken);
        this.importOrchestratorService = importOrchestratorService;
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

        if (message.hasDocument()) {
            handleDocument(message, replyChatId);
            return;
        }

        if (message.hasText()) {
            String text = message.getText();
            String response = "/start".equals(text)
                    ? "Hello! Send me a broker statement (XLSX for XTB, CSV for IBKR) and I'll import it."
                    : "I understand /start and broker statement files (XLSX/CSV).";
            sendTo(replyChatId, response);
        }
    }

    private void handleDocument(Message message, String replyChatId) {
        Document document = message.getDocument();
        String fileName = document.getFileName() != null ? document.getFileName() : "upload";
        BrokerType broker = detectBroker(fileName);
        if (broker == null) {
            sendTo(replyChatId, "Could not detect broker from file name: " + fileName
                    + ". Use an XTB *.xlsx or IBKR *.csv export.");
            return;
        }

        try {
            byte[] bytes = downloadDocumentBytes(document);
            ImportBatchResponse result = importOrchestratorService.importFile(
                    broker, bytes, fileName, ImportSourceType.TELEGRAM, replyChatId);
            sendTo(replyChatId, formatImportSummary(result));
        } catch (Exception e) {
            log.warn("Telegram import failed for {}", fileName, e);
            sendTo(replyChatId, "Import failed: " + e.getMessage());
        }
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

    static BrokerType detectBroker(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        // Keyword match wins over extension so "IBKR_jan.xlsx" is not misclassified as XTB
        // and "upload.csv" is not misclassified as IBKR.
        if (lower.contains("xtb")) {
            return BrokerType.XTB;
        }
        if (lower.contains("ibkr")) {
            return BrokerType.IBKR;
        }
        // IBKR Activity Statement files are named like "U17959259.TRANSACTIONS....csv";
        // require a digit right after the leading 'U' to avoid catching arbitrary "u*"
        // file names like "upload.csv" or "us-rates.csv".
        if (lower.length() > 1 && lower.charAt(0) == 'u' && Character.isDigit(lower.charAt(1))
                && lower.endsWith(".csv")) {
            return BrokerType.IBKR;
        }
        if (lower.endsWith(".xlsx")) {
            return BrokerType.XTB;
        }
        if (lower.endsWith(".csv")) {
            return BrokerType.IBKR;
        }
        return null;
    }

    private String formatImportSummary(ImportBatchResponse r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.duplicate() ? "Already imported." : "Import complete.").append('\n');
        sb.append("Broker: ").append(r.broker()).append('\n');
        sb.append("Status: ").append(r.status()).append('\n');
        sb.append("Rows total/applied/failed: ")
                .append(r.rowsTotal()).append('/').append(r.rowsApplied()).append('/').append(r.rowsFailed());
        if (r.message() != null && !r.message().isBlank()) {
            sb.append('\n').append(r.message());
        }
        return sb.toString();
    }

    private void sendTo(String targetChatId, String text) {
        try {
            execute(new SendMessage(targetChatId, text));
        } catch (TelegramApiException e) {
            log.warn("Failed to send Telegram message", e);
        }
    }

    public void sendMessage(String data) {
        if (chatId == null || chatId.isBlank()) {
            log.debug("Telegram chat-id not configured; skipping message");
            return;
        }
        sendTo(chatId, data);
    }
}
