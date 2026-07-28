package com.example.demo.services.models;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Comparison of account monthly P/L against SPY performance from the same starting value,
 * expressed as cumulative USD P/L over time plus simple excess return in percentage points.
 */
@Data
public class Benchmark {

    private boolean available;
    private String symbol = "SPY";

    /** Month labels ("yyyy-MM") shared by both curves. */
    private List<String> labels = new ArrayList<>();

    /** Cumulative account monthly P/L excluding external funding flows, in base USD. */
    private List<Double> portfolioCurve = new ArrayList<>();

    /** Cumulative P/L if each account's starting value had tracked SPY, in base USD. */
    private List<Double> benchmarkCurve = new ArrayList<>();

    private double investedCapital;
    private double portfolioPl;
    private double benchmarkPl;
    private double portfolioReturnPct;
    private double benchmarkReturnPct;
    /** Portfolio return minus benchmark return, in percentage points. This is not risk-adjusted alpha. */
    private double alpha;

    private List<AccountOption> accountOptions = new ArrayList<>();
    private List<AccountSeries> accountSeries = new ArrayList<>();
    private boolean accountValuesAvailable;
    private Integer selectedAccountValueYear;
    private List<AccountValueYear> accountValueYears = new ArrayList<>();

    public record AccountOption(Long id, String name, boolean selected) {}

    public record AccountSeries(
            Long id,
            double investedCapital,
            double portfolioPl,
            double benchmarkPl,
            List<Double> portfolioCurve,
            List<Double> benchmarkCurve) {}

    public record AccountValueYear(
            int year,
            List<String> labels,
            List<AccountValueSeries> accountSeries,
            List<Double> totalProfitValues,
            List<Double> totalProfitPctValues) {}

    public record AccountValueSeries(
            Long id,
            String name,
            List<Double> profitValues,
            List<Double> profitPctValues) {}
}

