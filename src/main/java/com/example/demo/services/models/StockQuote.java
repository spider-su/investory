package com.example.demo.services.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockQuote {
    private String symbol;
    private String name;
    private String exchange;
    private String currency;
    private String datetime;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private double previousClose;
    private double change;
    private double percentChange;
    private boolean isMarketOpen;
}
