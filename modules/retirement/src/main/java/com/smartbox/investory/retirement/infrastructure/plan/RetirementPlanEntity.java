package com.smartbox.investory.retirement.infrastructure.plan;

import com.smartbox.investory.retirement.api.model.SimulationFundingStrategy;
import com.smartbox.investory.retirement.infrastructure.assumptions.PersistedSimulationAssumptions;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Mutable canonical plan row. It stores intent, never generated projection output. */
@Entity
@Table(
    name = "retirement_plans",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_retirement_plans_portfolio_name",
            columnNames = {"portfolio_id", "name"}))
@Getter
@Setter
public class RetirementPlanEntity implements PersistedSimulationAssumptions {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Column(nullable = false)
  private String name;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "effective_year", nullable = false)
  private int effectiveYear;

  @Column(name = "end_age", nullable = false)
  private int endAge;

  @Column(name = "retirement_age")
  private Integer retirementAge;

  @Column(name = "annual_employment_income")
  private BigDecimal annualEmploymentIncome;

  @Column(name = "annual_pre_retirement_contribution")
  private BigDecimal annualPreRetirementContribution;

  @Column(name = "annual_living_expenses", nullable = false)
  private BigDecimal annualLivingExpenses;

  @Column(name = "annual_discretionary_expenses", nullable = false)
  private BigDecimal annualDiscretionaryExpenses;

  @Column(name = "inflation_rate", nullable = false)
  private BigDecimal inflationRate;

  @Column(name = "rental_income_growth_rate", nullable = false)
  private BigDecimal rentalIncomeGrowthSpread;

  @Column(name = "spending_growth_rate", nullable = false)
  private BigDecimal spendingGrowthSpread;

  @Enumerated(EnumType.STRING)
  @Column(name = "funding_strategy")
  private SimulationFundingStrategy fundingStrategy;

  @Column(name = "funding_order")
  private String fundingOrder;

  @Column(name = "expense_profile")
  private String expenseProfile;

  @Column(name = "safe_reserve_years")
  private BigDecimal safeReserveYears;

  @Column(name = "equity_harvest_minimum_return_rate")
  private BigDecimal equityHarvestMinimumReturnRate;

  @Column(name = "equity_gain_harvest_rate")
  private BigDecimal equityGainHarvestRate;

  @Column(name = "allow_emergency_equity_withdrawal")
  private Boolean allowEmergencyEquityWithdrawal;

  @Column(name = "fixed_income_return_rate", nullable = false)
  private BigDecimal fixedIncomeReturnRate;

  @Column(name = "equity_return_rate", nullable = false)
  private BigDecimal equityReturnRate;

  @Column(name = "pension_start_age", nullable = false)
  private int pensionStartAge;

  @Column(name = "annual_pension", nullable = false)
  private BigDecimal annualPension;

  @Column(name = "capital_gain_tax_rate", nullable = false)
  private BigDecimal capitalGainTaxRate;

  @Column(name = "baseline_as_of_year")
  private Integer baselineAsOfYear;

  @Column(name = "baseline_reserve")
  private BigDecimal baselineReserve;

  @Column(name = "baseline_investment_capital")
  private BigDecimal baselineInvestmentCapital;

  @Column(name = "baseline_long_term_capital")
  private BigDecimal baselineLongTermCapital;

  @Column(name = "baseline_rental_income")
  private BigDecimal baselineRentalIncome;

  @Column(name = "baseline_long_term_income")
  private BigDecimal baselineLongTermIncome;

  @Column(name = "baseline_long_term_state", columnDefinition = "text")
  private String baselineLongTermState;

  @Column(name = "baseline_long_term_state_version")
  private Integer baselineLongTermStateVersion;

  @Column(nullable = false)
  private boolean archived;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public int getEffectiveYear() {
    return effectiveYear;
  }

  @Override
  public void setEffectiveYear(int value) {
    this.effectiveYear = value;
  }
}
