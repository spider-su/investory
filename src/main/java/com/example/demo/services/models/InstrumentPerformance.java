package com.example.demo.services.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InstrumentPerformance {
    private String symbol;
    private double closedProfit;
    private double unrealizedProfit;
    private double total;
}
