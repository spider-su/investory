package com.example.demo.services.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TechnicalIndicator {
    private String symbol;
    private double macd;
    private double rsi;
    private long volume;
    private ZonedDateTime timestamp; // for time-series
}
