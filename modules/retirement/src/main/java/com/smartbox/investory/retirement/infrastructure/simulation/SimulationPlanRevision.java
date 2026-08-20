package com.smartbox.investory.retirement.infrastructure.simulation;

import com.smartbox.investory.retirement.simulation.SimulationFundingStrategy;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/** Immutable snapshot of the assumptions used by a logical simulation plan. */
@Entity
@Immutable
@Table(
    name = "simulation_plan_revisions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_simulation_plan_revisions_plan_number",
            columnNames = {"simulation_plan_id", "revision_number"}))
@Getter
@Setter
public class SimulationPlanRevision {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "simulation_plan_id", nullable = false)
  private Long simulationPlanId;

  @Column(name = "revision_number", nullable = false)
  private int revisionNumber;

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
  private BigDecimal rentalIncomeGrowthRate;

  @Column(name = "spending_growth_rate", nullable = false, precision = 20, scale = 12)
  private BigDecimal spendingGrowthRate;

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

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }
}
