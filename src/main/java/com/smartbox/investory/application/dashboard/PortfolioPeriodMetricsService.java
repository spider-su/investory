package com.smartbox.investory.application.dashboard;

import java.util.List;
import org.springframework.stereotype.Service;

/** Shared period-level financial reductions used by dashboard views. */
@Service
public class PortfolioPeriodMetricsService {

  public Double compoundAccountReturn(List<Double> monthlyReturnPcts) {
    if (monthlyReturnPcts == null) {
      return null;
    }
    double factor = 1.0;
    boolean available = false;
    for (Double monthlyReturnPct : monthlyReturnPcts) {
      if (monthlyReturnPct == null || !Double.isFinite(monthlyReturnPct)) {
        continue;
      }
      double periodReturn = monthlyReturnPct / 100.0;
      factor = periodReturn <= -1.0 ? 0.0 : factor * (1.0 + periodReturn);
      available = true;
    }
    return available ? round((factor - 1.0) * 100.0) : null;
  }

  public double netIncome(double dividends, double withholdingTax, double interest) {
    return dividends + signedTax(withholdingTax) + interest;
  }

  /**
   * Yield uses average observed portfolio equity for the selected period. If that value is a
   * near-zero inception artifact, use net deposits as a conservative fallback.
   */
  public double incomeYield(double income, double averageEquity, double netDeposits) {
    boolean usableAverage =
        averageEquity > 0.0 && (netDeposits <= 0.0 || averageEquity >= netDeposits * 0.10);
    double capital = usableAverage ? averageEquity : netDeposits;
    return capital > 0.0 ? income / capital * 100.0 : 0.0;
  }

  public double signedTax(double tax) {
    return tax > 0.0 ? -tax : tax;
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
