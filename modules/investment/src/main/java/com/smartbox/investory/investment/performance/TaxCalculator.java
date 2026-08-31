package com.smartbox.investory.investment.performance;

import com.smartbox.investory.investment.ledger.position.persistence.ClosedPosition;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.presentation.FinancialPrecision;
import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Estimates Polish capital-gains tax ("Belka", 19 %) for the current tax year, applying loss
 * carry-forward from the previous five years.
 *
 * <p>Extracted from {@code PortfolioService.calculateTotalProfitLoss()} so the tax algorithm can be
 * unit-tested without spinning up the full analytics pipeline.
 */
@Component
@RequiredArgsConstructor
public class TaxCalculator {

  /** Polish capital-gains tax rate. */
  private static final BigDecimal RATE = new BigDecimal("0.19");

  /** Polish rule: losses deductible against gains for the next 5 years. */
  private static final int LOSS_CARRY_FORWARD_YEARS = 5;

  private final CurrencyRateService currencyRateService;

  /** Tax result in {@code baseCurrency}, rounded to 2 decimal places. */
  public record TaxSummary(BigDecimal capitalGainsTax, BigDecimal lossCarryForward) {}

  public TaxSummary calculate(List<ClosedPosition> closedPositions, CurrencyType baseCurrency) {
    return calculate(closedPositions, baseCurrency, Year.now().getValue());
  }

  /** Year-injectable overload, used by tests so they don't drift across calendar boundaries. */
  public TaxSummary calculate(
      List<ClosedPosition> closedPositions, CurrencyType baseCurrency, int currentYear) {
    Map<Integer, BigDecimal> realizedByYear = new TreeMap<>();
    for (ClosedPosition position : closedPositions) {
      if (position.getCloseTime() == null) {
        continue;
      }
      realizedByYear.merge(
          position.getCloseTime().getYear(),
          netProfitInBase(position, baseCurrency),
          BigDecimal::add);
    }

    // Walk years chronologically: loss years feed a pool; gain years consume losses from the
    // previous 5 years (oldest first). Only the current year's resulting tax is reported.
    Map<Integer, BigDecimal> lossPool = new TreeMap<>();
    BigDecimal currentYearTaxable = BigDecimal.ZERO;
    BigDecimal appliedToCurrentYear = BigDecimal.ZERO;
    for (Integer year : new TreeSet<>(realizedByYear.keySet())) {
      BigDecimal net = realizedByYear.getOrDefault(year, BigDecimal.ZERO);
      if (net.compareTo(BigDecimal.ZERO) < 0) {
        lossPool.merge(year, net.abs(), BigDecimal::add);
        continue;
      }
      BigDecimal remainingGain = net;
      for (Map.Entry<Integer, BigDecimal> loss : lossPool.entrySet()) {
        if (remainingGain.compareTo(BigDecimal.ZERO) <= 0) {
          break;
        }
        int lossYear = loss.getKey();
        if (lossYear < year - LOSS_CARRY_FORWARD_YEARS || lossYear >= year) {
          continue; // outside the deduction window
        }
        BigDecimal use = remainingGain.min(loss.getValue());
        loss.setValue(loss.getValue().subtract(use));
        remainingGain = remainingGain.subtract(use);
        if (year == currentYear) {
          appliedToCurrentYear = appliedToCurrentYear.add(use);
        }
      }
      if (year == currentYear) {
        currentYearTaxable = remainingGain;
      }
    }
    return new TaxSummary(round(currentYearTaxable.multiply(RATE)), round(appliedToCurrentYear));
  }

  private BigDecimal netProfitInBase(ClosedPosition position, CurrencyType baseCurrency) {
    var date = position.getCloseTime().toLocalDate();
    return currencyRateService
        .convertToBaseCurrency(
            nz(position.getProfitValue()).add(nz(position.getSwapValue())),
            baseCurrency,
            position.getProfitCurrency(),
            date)
        .add(
            currencyRateService.convertToBaseCurrency(
                nz(position.getCommissionValue()),
                baseCurrency,
                position.getCommissionCurrency(),
                date));
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private static BigDecimal round(BigDecimal value) {
    return FinancialPrecision.money(value);
  }
}
