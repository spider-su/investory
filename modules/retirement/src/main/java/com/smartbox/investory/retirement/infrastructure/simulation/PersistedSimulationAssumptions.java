package com.smartbox.investory.retirement.infrastructure.simulation;

import com.smartbox.investory.retirement.simulation.ProjectedIncomePolicy.IncomeMode;
import com.smartbox.investory.retirement.simulation.SimulationFundingStrategy;
import java.math.BigDecimal;

/** Shared persistence shape for a mutable plan row and an immutable revision row. */
public interface PersistedSimulationAssumptions {
  int getCurrentAge();

  void setCurrentAge(int value);

  int getStartYear();

  void setStartYear(int value);

  int getEndAge();

  void setEndAge(int value);

  Integer getRetirementAge();

  void setRetirementAge(Integer value);

  BigDecimal getAnnualEmploymentIncome();

  void setAnnualEmploymentIncome(BigDecimal value);

  BigDecimal getAnnualPreRetirementContribution();

  void setAnnualPreRetirementContribution(BigDecimal value);

  BigDecimal getAnnualLivingExpenses();

  void setAnnualLivingExpenses(BigDecimal value);

  BigDecimal getAnnualDiscretionaryExpenses();

  void setAnnualDiscretionaryExpenses(BigDecimal value);

  BigDecimal getInflationRate();

  void setInflationRate(BigDecimal value);

  BigDecimal getRentalIncomeGrowthSpread();

  void setRentalIncomeGrowthSpread(BigDecimal value);

  BigDecimal getSpendingGrowthSpread();

  void setSpendingGrowthSpread(BigDecimal value);

  IncomeMode getRentalIncomeMode();

  void setRentalIncomeMode(IncomeMode value);

  BigDecimal getManualRentalIncome();

  void setManualRentalIncome(BigDecimal value);

  IncomeMode getBondCashIncomeMode();

  void setBondCashIncomeMode(IncomeMode value);

  BigDecimal getManualBondCashIncome();

  void setManualBondCashIncome(BigDecimal value);

  SimulationFundingStrategy getFundingStrategy();

  void setFundingStrategy(SimulationFundingStrategy value);

  String getFundingOrder();

  void setFundingOrder(String value);

  String getExpenseProfile();

  void setExpenseProfile(String value);

  BigDecimal getSafeReserveYears();

  void setSafeReserveYears(BigDecimal value);

  BigDecimal getEquityHarvestMinimumReturnRate();

  void setEquityHarvestMinimumReturnRate(BigDecimal value);

  BigDecimal getEquityGainHarvestRate();

  void setEquityGainHarvestRate(BigDecimal value);

  Boolean getAllowEmergencyEquityWithdrawal();

  void setAllowEmergencyEquityWithdrawal(Boolean value);

  BigDecimal getCashReturnRate();

  void setCashReturnRate(BigDecimal value);

  BigDecimal getFixedIncomeReturnRate();

  void setFixedIncomeReturnRate(BigDecimal value);

  BigDecimal getEquityReturnRate();

  void setEquityReturnRate(BigDecimal value);

  BigDecimal getRealEstateReturnRate();

  void setRealEstateReturnRate(BigDecimal value);

  BigDecimal getOtherReturnRate();

  void setOtherReturnRate(BigDecimal value);

  int getPensionStartAge();

  void setPensionStartAge(int value);

  BigDecimal getAnnualPension();

  void setAnnualPension(BigDecimal value);

  BigDecimal getCapitalGainTaxRate();

  void setCapitalGainTaxRate(BigDecimal value);
}
