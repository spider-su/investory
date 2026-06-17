package com.example.demo.services.models;

import com.example.demo.infrastructure.CurrencyType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenPositionsPerformance {
    CurrencyType baseCurrency = CurrencyType.USD;

    double totalOpen = 0.0;
    double totalOpenPercents = 0.0;

    double totalClosed = 0.0;
    double totalClosedPercents = 0.0;

    List<OpenPositionsPerformanceItem> positions = new ArrayList<>();
}
