package com.smartbox.investory.retirement.infrastructure.simulation;

import com.smartbox.investory.retirement.simulation.ProjectedIncomePolicy.IncomeMode;
import com.smartbox.investory.retirement.simulation.SimulationFundingStrategy;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "simulation_plans",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_simulation_plans_portfolio_name",
            columnNames = {"portfolio_id", "name"}))
@Getter
@Setter
public class SimulationPlanEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "current_revision_id")
  private Long currentRevisionId;

  @Column(name = "archived", nullable = false)
  private boolean archived;

  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Column(nullable = false)
  private String name;

  @Column(name = "current_age", nullable = false)
  private int currentAge;

  @Column(name = "start_year", nullable = false)
  private int startYear;

  @Column(name = "end_age", nullable = false)
  private int endAge;

  @Column(name = "retirement_age")
  private Integer retirementAge;

  @Column(name = "annual_employment_income", precision = 30, scale = 12)
  private BigDecimal annualEmploymentIncome;

  @Column(name = "annual_pre_retirement_contribution", precision = 30, scale = 12)
  private BigDecimal annualPreRetirementContribution;

  @Column(name = "annual_living_expenses", nullable = false, precision = 30, scale = 12)
  private BigDecimal annualLivingExpenses;

  @Column(name = "annual_discretionary_expenses", nullable = false, precision = 30, scale = 12)
  private BigDecimal annualDiscretionaryExpenses;

  @Column(name = "inflation_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal inflationRate;

  @Column(name = "rental_income_growth_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal rentalIncomeGrowthSpread;

  @Column(name = "spending_growth_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal spendingGrowthSpread;

  @Enumerated(EnumType.STRING)
  @Column(name = "rental_income_mode", length = 16)
  private IncomeMode rentalIncomeMode;

  @Column(name = "manual_rental_income", precision = 30, scale = 12)
  private BigDecimal manualRentalIncome;

  @Enumerated(EnumType.STRING)
  @Column(name = "bond_cash_income_mode", length = 16)
  private IncomeMode bondCashIncomeMode;

  @Column(name = "manual_bond_cash_income", precision = 30, scale = 12)
  private BigDecimal manualBondCashIncome;

  @Enumerated(EnumType.STRING)
  @Column(name = "funding_strategy", length = 32)
  private SimulationFundingStrategy fundingStrategy;

  @Column(name = "funding_order", length = 64)
  private String fundingOrder;

  @Column(name = "expense_profile", length = 512)
  private String expenseProfile;

  @Column(name = "safe_reserve_years", precision = 20, scale = 12)
  private BigDecimal safeReserveYears;

  @Column(name = "equity_harvest_minimum_return_rate", precision = 20, scale = 12)
  private BigDecimal equityHarvestMinimumReturnRate;

  @Column(name = "equity_gain_harvest_rate", precision = 20, scale = 12)
  private BigDecimal equityGainHarvestRate;

  @Column(name = "allow_emergency_equity_withdrawal")
  private Boolean allowEmergencyEquityWithdrawal;

  @Column(name = "cash_return_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal cashReturnRate;

  @Column(name = "fixed_income_return_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal fixedIncomeReturnRate;

  @Column(name = "equity_return_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal equityReturnRate;

  @Column(name = "real_estate_return_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal realEstateReturnRate;

  @Column(name = "other_return_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal otherReturnRate;

  @Column(name = "pension_start_age", nullable = false)
  private int pensionStartAge;

  @Column(name = "annual_pension", nullable = false, precision = 30, scale = 12)
  private BigDecimal annualPension;

  @Column(name = "capital_gain_tax_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal capitalGainTaxRate;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
