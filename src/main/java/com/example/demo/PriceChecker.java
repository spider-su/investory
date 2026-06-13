package com.example.demo;

import com.example.demo.controllers.bot.PortfolioBot;
import com.example.demo.services.TwelveDataService;
import com.example.demo.services.models.StockQuote;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

public class PriceChecker {
    private static final String[] STOCKS = {"VZ", "T", "MO", "PG", "LMT"};
    private final PortfolioBot bot;

    public PriceChecker(PortfolioBot bot) {
        this.bot = bot;
    }

    TwelveDataService twelveDataService = new TwelveDataService();
    public void checkPrices() {
        StringBuilder alert = new StringBuilder("📊 *Daily Stock Summary:*\n\n");

        List<String> risers = new ArrayList<>();
        List<String> fallers = new ArrayList<>();

        try {

            Map<String, StockQuote> stockQuotes = Map.of();//TwelveDataService.fetchStockQuotes(String.join(",", Arrays.asList(STOCKS)));

            List<Map.Entry<String, StockQuote>> sortedQuotes = stockQuotes.entrySet()
                    .stream()
                    .sorted((a, b) -> Double.compare(
                            Math.abs(b.getValue().getPercentChange()),
                            Math.abs(a.getValue().getPercentChange())
                    ))
                    .collect(Collectors.toList());

            for (Map.Entry<String, StockQuote> entry : sortedQuotes) {
                String symbol = entry.getKey();
                StockQuote quote = entry.getValue();

                if (quote != null) {
                    double percent = quote.getPercentChange();
                    String emoji;

                    if (percent >= 10) {
                        emoji = "🚀";
                    } else if (percent >= 5) {
                        emoji = "📈";
                    } else if (percent >= 0) {
                        emoji = "🟢";
                    } else if (percent <= -10) {
                        emoji = "💥";
                    } else if (percent <= -5) {
                        emoji = "📉";
                    } else {
                        emoji = "🔴";
                    }

                    String formatted = String.format("%-7s | %s %s", symbol, emoji, String.format("%+6.2f%%", percent));

                    if (percent >= 0) {
                        risers.add(formatted);
                    } else {
                        fallers.add(formatted);
                    }
                }
            }

        } catch (Exception e) {
            alert.append("\n❌ Error fetching stock prices.\n");
            e.printStackTrace();
        }

        // Добавляем блок роста
        if (!risers.isEmpty()) {
            alert.append("📈 *Risers:*\n");
            alert.append("```Ticker  | Change\n--------------------\n");
            risers.forEach(line -> alert.append(line).append("\n"));
            alert.append("```\n\n");
        }

        // Добавляем блок падений
        if (!fallers.isEmpty()) {
            alert.append("📉 *Fallers:*\n");
            alert.append("```Ticker  | Change\n--------------------\n");
            fallers.forEach(line -> alert.append(line).append("\n"));
            alert.append("```\n");
        }

//        bot.sendMarkdownMessage(alert.toString());
    }




    public void checkPortfolio(List<StockPosition> positions) {
        StringBuilder alert = new StringBuilder("Price alert:\n");

        for (StockPosition position : positions) {
            try {
                Stock stock = YahooFinance.get(position.symbol);
                BigDecimal currentPrice = stock.getQuote().getPrice();

                if (currentPrice != null && position.buyPrice != null) {
                    BigDecimal changePercent = currentPrice.subtract(position.buyPrice)
                            .divide(position.buyPrice, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    double percent = changePercent.doubleValue();
                    if (Math.abs(percent) >= 5) {
                        alert.append(String.format("%s: %.2f%% from buy price (%.2f → %.2f)",
                                position.symbol, percent, position.buyPrice, currentPrice));

                        if (Math.abs(percent) >= 10) {
                            alert.append(" ⚠️ 10%+\n");
                        } else {
                            alert.append(" ⚠️ 5%+\n");
                        }
                    }
                }
            } catch (IOException e) {
                alert.append(position.symbol).append(": Error fetching price\n");
            }
        }

        if (alert.length() > 12) {
            bot.sendMessage(alert.toString());
        }
    }

}

