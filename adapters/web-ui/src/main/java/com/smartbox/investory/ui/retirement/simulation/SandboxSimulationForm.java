package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.SandboxSimulationInput;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Year;
import lombok.Getter;
import lombok.Setter;

/** Bindable form for the stateless retirement sandbox. */
@Getter
@Setter
public class SandboxSimulationForm {
  private Long portfolioId;
  private Long planId;

  @Min(0)
  @Max(120)
  private int currentAge = 40;

  @Min(0)
  @Max(120)
  private int retirementAge = 42;

  @Min(0)
  @Max(120)
  private int endAge = 80;

  @NotNull
  @DecimalMin("0")
  @Digits(integer = 18, fraction = 2)
  private BigDecimal annualSpending = new BigDecimal("240000");

  @NotNull
  @DecimalMin("-1")
  @DecimalMax("10")
  @Digits(integer = 4, fraction = 6)
  private BigDecimal inflationRate = new BigDecimal("0.021");

  @NotNull
  @DecimalMin("0")
  @Digits(integer = 18, fraction = 2)
  private BigDecimal cash = new BigDecimal("120000");

  @NotNull
  @DecimalMin("0")
  @Digits(integer = 18, fraction = 2)
  private BigDecimal bonds = new BigDecimal("750000");

  @NotNull
  @DecimalMin("-1")
  @DecimalMax("10")
  @Digits(integer = 4, fraction = 6)
  private BigDecimal bondReturnRate = new BigDecimal("0.040");

  @NotNull
  @DecimalMin("0")
  @Digits(integer = 18, fraction = 2)
  private BigDecimal equities = new BigDecimal("750000");

  @NotNull
  @DecimalMin("-1")
  @DecimalMax("10")
  @Digits(integer = 4, fraction = 6)
  private BigDecimal equityReturnRate = new BigDecimal("0.080");

  @NotNull
  @DecimalMin("0")
  @Digits(integer = 18, fraction = 2)
  private BigDecimal monthlyRentalIncome = new BigDecimal("14500");

  @NotNull
  @DecimalMin("0")
  @Digits(integer = 18, fraction = 2)
  private BigDecimal monthlyPensionIncome = new BigDecimal("5000");

  @Min(0)
  @Max(120)
  private int pensionAge = 67;

  @AssertTrue(message = "Current age must be no greater than retirement age")
  public boolean isAgeOrderValid() {
    return currentAge <= retirementAge;
  }

  @AssertTrue(message = "Retirement age must be no greater than end age")
  public boolean isRetirementBeforeEndAge() {
    return retirementAge <= endAge;
  }

  public SandboxSimulationInput input() {
    return new SandboxSimulationInput(
        currentAge,
        retirementAge,
        endAge,
        annualSpending,
        inflationRate,
        cash,
        bonds,
        bondReturnRate,
        equities,
        equityReturnRate,
        monthlyRentalIncome,
        monthlyPensionIncome,
        pensionAge,
        Year.now().getValue());
  }

  public void apply(SandboxSimulationInput input) {
    currentAge = input.currentAge();
    retirementAge = input.retirementAge();
    endAge = input.endAge();
    annualSpending = input.annualSpending();
    inflationRate = input.inflationRate();
    cash = input.cash();
    bonds = input.bonds();
    bondReturnRate = input.bondReturnRate();
    equities = input.equities();
    equityReturnRate = input.equityReturnRate();
    monthlyRentalIncome = input.monthlyRentalIncome();
    monthlyPensionIncome = input.monthlyPensionIncome();
    pensionAge = input.pensionAge();
  }
}
