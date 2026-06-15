package com.example.demo.services.models;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Comparison of the portfolio's realized performance against an S&P 500 buy-and-hold
 * of the same capital, expressed as cumulative USD P/L over time plus headline returns.
 */
@Data
public class Benchmark {

    private boolean available;
    private String symbol = "SPY";

    /** Month labels ("yyyy-MM") shared by both curves. */
    private List<String> labels = new ArrayList<>();

    /** Cumulative realized P/L + dividends of the portfolio, in base USD. */
    private List<Double> portfolioCurve = new ArrayList<>();

    /** Cumulative P/L if the same capital had tracked the S&P 500, in base USD. */
    private List<Double> benchmarkCurve = new ArrayList<>();

    private double investedCapital;
    private double portfolioPl;
    private double benchmarkPl;
    private double portfolioReturnPct;
    private double benchmarkReturnPct;
    /** Portfolio return minus benchmark return, in percentage points. */
    private double alpha;
}

