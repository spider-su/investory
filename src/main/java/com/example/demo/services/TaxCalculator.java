package com.example.demo.services;

import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.ClosedPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Estimates Polish capital-gains tax ("Belka", 19 %) for the current tax year, applying loss
 * carry-forward from the previous five years.
 *
 * <p>Extracted from {@code PortfolioService.calculateTotalProfitLoss()} so the tax algorithm
 * can be unit-tested without spinning up the full analytics pipeline.
 */
@Component
@RequiredArgsConstructor
public class TaxCalculator {

    /** Polish capital-gains tax rate. */
    private static final double RATE = 0.19;
    /** Polish rule: losses deductible against gains for the next 5 years. */
    private static final int LOSS_CARRY_FORWARD_YEARS = 5;

    private final CurrencyRateService currencyRateService;

    /** Tax result in {@code baseCurrency}, rounded to 2 decimal places. */
    public record TaxSummary(double capitalGainsTax, double lossCarryForward) {}

    public TaxSummary calculate(List<ClosedPosition> closedPositions, CurrencyType baseCurrency) {
        return calculate(closedPositions, baseCurrency, Year.now().getValue());
    }

    /** Year-injectable overload, used by tests so they don't drift across calendar boundaries. */
    public TaxSummary calculate(List<ClosedPosition> closedPositions, CurrencyType baseCurrency, int currentYear) {
        Map<Integer, Double> realizedByYear = closedPositions.stream()
                .filter(p -> p.getCloseTime() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getCloseTime().getYear(),
                        Collectors.summingDouble(p -> currencyRateService.convertToBaseCurrency(
                                nz(p.getProfit()) + nz(p.getCommission()) + nz(p.getSwap()),
                                baseCurrency, p.getCurrency()))));

        // Walk years chronologically: loss years feed a pool; gain years consume losses from the
        // previous 5 years (oldest first). Only the current year's resulting tax is reported.
        Map<Integer, Double> lossPool = new TreeMap<>();
        double currentYearTaxable = 0.0;
        double appliedToCurrentYear = 0.0;
        for (Integer year : new TreeSet<>(realizedByYear.keySet())) {
            double net = realizedByYear.getOrDefault(year, 0.0);
            if (net < 0) {
                lossPool.merge(year, -net, Double::sum);
                continue;
            }
            double remainingGain = net;
            for (Map.Entry<Integer, Double> loss : lossPool.entrySet()) {
                if (remainingGain <= 0) {
                    break;
                }
                int lossYear = loss.getKey();
                if (lossYear < year - LOSS_CARRY_FORWARD_YEARS || lossYear >= year) {
                    continue; // outside the deduction window
                }
                double use = Math.min(remainingGain, loss.getValue());
                loss.setValue(loss.getValue() - use);
                remainingGain -= use;
                if (year == currentYear) {
                    appliedToCurrentYear += use;
                }
            }
            if (year == currentYear) {
                currentYearTaxable = remainingGain;
            }
        }
        return new TaxSummary(
                round(currentYearTaxable * RATE),
                round(appliedToCurrentYear));
    }

    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

