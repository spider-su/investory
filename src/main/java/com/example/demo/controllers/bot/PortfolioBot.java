package com.example.demo.controllers.bot;

import com.example.demo.PriceChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
public class PortfolioBot extends TelegramLongPollingBot {

    @Value("${app.telegram.chat-id:}")
    private String chatId;

    @Value("${app.telegram.bot-username:}")
    private String botUsername;

    @Value("${app.telegram.bot-token:}")
    private String botToken;

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String receivedText = update.getMessage().getText();
            String chatId = update.getMessage().getChatId().toString();

            String response = receivedText.equals("/start") ? "Hello bot!" :
                    "You should buy it now !!!: " + receivedText;
            PriceChecker priceChecker = new PriceChecker(this);
            priceChecker.checkPrices();
//            SendMessage message = new SendMessage(chatId, response);
//            try {
//                execute(message);
//            } catch (TelegramApiException e) {
//                e.printStackTrace();
//            }
        }
    }

    public void sendMessage(String data) {
        SendMessage message = new SendMessage(chatId, data);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendMarkdownMessage(String message) {
        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(message);
            sendMessage.setParseMode("MarkdownV2");
            sendMessage.disableWebPagePreview();

            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
