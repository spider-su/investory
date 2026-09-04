package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationFundingStrategy;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Bound query model for the simulation page, including legacy plan overrides. */
@Getter
@Setter
public final class SimulationQuery {
  private Long portfolioId;
  private Long planId;
  private Integer currentAge;
  private Integer endAge;
  private BigDecimal annualExpenses;
  private BigDecimal annualExpensesCanonical;
  private boolean annualExpensesEdited;
  private BigDecimal discretionaryExpenses;
  private BigDecimal discretionaryExpensesCanonical;
  private boolean discretionaryExpensesEdited;
  private BigDecimal inflation;
  private BigDecimal rentalIncomeGrowthSpread;
  private BigDecimal spendingGrowthSpread;
  private SimulationFundingStrategy fundingStrategy;
  private String fundingOrder;
  private BigDecimal safeReserveYears;
  private BigDecimal equityHarvestMinimumReturn;
  private BigDecimal equityGainHarvest;
  private Boolean allowEmergencyEquityWithdrawal;
  private BigDecimal fixedIncomeReturn;
  private BigDecimal equityReturn;
  private Integer pensionStartAge;
  private BigDecimal annualPension;
  private BigDecimal annualPensionCanonical;
  private boolean annualPensionEdited;
  private BigDecimal capitalGainTaxRate;
  private CurrencyType planningDisplayCurrency;
  private CurrencyType submittedPlanningDisplayCurrency;
  private SimulationScenario selectedScenario = SimulationScenario.BASE;

  SimulationRequestMapper.LegacyQueryOverrides legacyOverrides() {
    return new SimulationRequestMapper.LegacyQueryOverrides(
        currentAge,
        endAge,
        annualExpenses,
        annualExpensesCanonical,
        annualExpensesEdited,
        discretionaryExpenses,
        discretionaryExpensesCanonical,
        discretionaryExpensesEdited,
        inflation,
        rentalIncomeGrowthSpread,
        spendingGrowthSpread,
        fundingStrategy,
        fundingOrder,
        safeReserveYears,
        equityHarvestMinimumReturn,
        equityGainHarvest,
        allowEmergencyEquityWithdrawal,
        fixedIncomeReturn,
        equityReturn,
        pensionStartAge,
        annualPension,
        annualPensionCanonical,
        annualPensionEdited,
        capitalGainTaxRate,
        planningDisplayCurrency,
        submittedPlanningDisplayCurrency);
  }
}
