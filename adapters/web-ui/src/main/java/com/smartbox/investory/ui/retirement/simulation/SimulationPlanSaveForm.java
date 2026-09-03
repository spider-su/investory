package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationFundingStrategy;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Bound HTML form for plan creation and update. Percentage fields use percentage points. */
@Getter
@Setter
public final class SimulationPlanSaveForm {
  @NotNull @Positive private Long portfolioId;
  private Long planId;
  @NotBlank private String name;
  private int currentAge = 40;
  private Integer ageAtPlanStart;
  private Integer startYear;
  private int endAge = 95;
  private Integer retirementAge;
  private BigDecimal annualEmploymentIncome = BigDecimal.ZERO;
  private BigDecimal annualPreRetirementContribution = BigDecimal.ZERO;
  private BigDecimal annualExpenses;
  private BigDecimal monthlyLivingCosts;
  private BigDecimal discretionaryExpenses = BigDecimal.ZERO;
  @NotNull private BigDecimal inflation;
  private BigDecimal rentalIncomeGrowthSpread = new BigDecimal("2");
  private BigDecimal spendingGrowthSpread = new BigDecimal("2.5");
  private SimulationFundingStrategy fundingStrategy = SimulationFundingStrategy.SIMPLE_WATERFALL;
  private String fundingOrder = "CASH,BONDS,STOCKS";
  private String expenseProfile = "";
  private BigDecimal safeReserveYears = new BigDecimal("5");
  private BigDecimal equityHarvestMinimumReturn = new BigDecimal("7");
  private BigDecimal equityGainHarvest = new BigDecimal("75");
  private boolean allowEmergencyEquityWithdrawal;
  private BigDecimal fixedIncomeReturn;
  @NotNull private BigDecimal equityReturn;
  private Integer pensionStartAge;
  @NotNull private BigDecimal annualPension;
  private BigDecimal capitalGainTaxRate = BigDecimal.ZERO;
  private CurrencyType planningDisplayCurrency;
  private CurrencyType returnPlanningDisplayCurrency;
  private boolean saveAs;
  private SimulationScenario selectedScenario = SimulationScenario.BASE;

  public void setEquityHarvestThreshold(BigDecimal value) {
    equityHarvestMinimumReturn = value;
  }

  public void setEquityHarvestShare(BigDecimal value) {
    equityGainHarvest = value;
  }

  SimulationRequestMapper.SavePlanForm mappingInput() {
    return new SimulationRequestMapper.SavePlanForm(
        currentAge,
        ageAtPlanStart,
        startYear,
        endAge,
        retirementAge,
        annualEmploymentIncome,
        annualPreRetirementContribution,
        annualExpenses,
        monthlyLivingCosts,
        discretionaryExpenses,
        inflation,
        rentalIncomeGrowthSpread,
        spendingGrowthSpread,
        fundingStrategy,
        fundingOrder,
        expenseProfile,
        safeReserveYears,
        equityHarvestMinimumReturn,
        equityGainHarvest,
        allowEmergencyEquityWithdrawal,
        fixedIncomeReturn,
        equityReturn,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        planningDisplayCurrency);
  }
}
