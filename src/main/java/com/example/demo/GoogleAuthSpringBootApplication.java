package com.example.demo;

import com.example.demo.controllers.bot.PortfolioBot;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@EnableFeignClients(basePackages = "com.example.demo.clients")

@SpringBootApplication
public class GoogleAuthSpringBootApplication {

//    @Autowired
    private static PortfolioBot portfolioBot;

    public static void main(String[] args) {
        SpringApplication.run(GoogleAuthSpringBootApplication.class, args);

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
//            portfolioBot = new PortfolioBot();
//            botsApi.registerBot(portfolioBot);

//            PriceChecker checker = new PriceChecker(portfolioBot);

//            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
//            scheduler.scheduleAtFixedRate(checker::checkPrices, 0, 1, TimeUnit.DAYS);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
