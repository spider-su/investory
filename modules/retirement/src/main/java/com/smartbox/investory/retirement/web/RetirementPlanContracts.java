package com.smartbox.investory.retirement.web;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.ExpenseProfile;
import com.smartbox.investory.retirement.api.model.ExpenseProfileStep;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.api.model.RetirementFundingSource;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationEvent;
import com.smartbox.investory.retirement.api.model.SimulationEventType;
import com.smartbox.investory.retirement.api.model.SimulationFundingStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RetirementPlanContracts {
  private RetirementPlanContracts() {}

  public record PlanCreateRequest(
      @NotBlank String name,
      @NotNull @Valid AssumptionsDto assumptions,
      @Valid BaselineDto baseline) {}

  public record PlanUpdateRequest(
      @NotBlank String name, @NotNull @Valid AssumptionsDto assumptions) {}

  public record EventWriteRequest(
      @NotNull @Min(1900) @Max(9999) Integer year,
      @NotBlank String name,
      @NotNull @PositiveOrZero BigDecimal amount,
      @NotNull SimulationEventType type,
      String notes) {}

  public record PlanMutationResponse(Long id) {}

  public record PlanSummaryDto(Long id, String name) {
    static PlanSummaryDto from(com.smartbox.investory.retirement.api.model.PlanSummary source) {
      return new PlanSummaryDto(source.id(), source.name());
    }
  }

  public record RevisionDto(Long id, int revisionNumber, Instant createdAt) {
    static RevisionDto from(com.smartbox.investory.retirement.api.model.RevisionSummary source) {
      return source == null
          ? null
          : new RevisionDto(source.id(), source.revisionNumber(), source.createdAt());
    }
  }

  public record PlanDetailsDto(
      Long id,
      String name,
      AssumptionsDto assumptions,
      Long currentRevisionId,
      RevisionDto currentRevision,
      BaselineDto baseline) {
    static PlanDetailsDto from(com.smartbox.investory.retirement.api.model.PlanDetails source) {
      return new PlanDetailsDto(
          source.id(),
          source.name(),
          AssumptionsDto.from(source.assumptions()),
          source.currentRevisionId(),
          RevisionDto.from(source.currentRevision()),
          BaselineDto.from(source.baseline()));
    }
  }

  public record BaselineDto(
      @Min(1900) @Max(9999) int asOfYear,
      @NotNull @PositiveOrZero BigDecimal reserve,
      @NotNull @PositiveOrZero BigDecimal investmentCapital,
      @NotNull @PositiveOrZero BigDecimal longTermCapital,
      @NotNull @PositiveOrZero BigDecimal rentalAnnualIncome,
      @NotNull @PositiveOrZero BigDecimal longTermAnnualIncome) {
    PlanningBaseline toDomain() {
      return new PlanningBaseline(
          asOfYear,
          reserve,
          investmentCapital,
          longTermCapital,
          rentalAnnualIncome,
          longTermAnnualIncome);
    }

    static BaselineDto from(PlanningBaseline source) {
      return source == null
          ? null
          : new BaselineDto(
              source.asOfYear(),
              source.reserve(),
              source.investmentCapital(),
              source.longTermCapital(),
              source.rentalAnnualIncome(),
              source.longTermAnnualIncome());
    }
  }

  public record ExpenseStepDto(
      @PositiveOrZero int fromYear, @NotNull @PositiveOrZero BigDecimal factor) {
    ExpenseProfileStep toDomain() {
      return new ExpenseProfileStep(fromYear, factor);
    }

    static ExpenseStepDto from(ExpenseProfileStep source) {
      return new ExpenseStepDto(source.fromYear(), source.factor());
    }
  }

  public record EventDto(
      Long id,
      @Min(1900) @Max(9999) int year,
      @NotBlank String name,
      @NotNull @PositiveOrZero BigDecimal amount,
      @NotNull SimulationEventType type,
      String notes) {
    SimulationEvent toDomain() {
      return new SimulationEvent(id, year, name, amount, type, notes);
    }

    static EventDto from(SimulationEvent source) {
      return new EventDto(
          source.id(),
          source.year(),
          source.name(),
          source.amount(),
          source.type(),
          source.notes());
    }
  }

  public record AssumptionsDto(
      @Min(0) @Max(150) int currentAge,
      @Min(0) @Max(150) int endAge,
      @NotNull @PositiveOrZero BigDecimal annualLivingExpenses,
      @NotNull BigDecimal inflationRate,
      @NotNull BigDecimal fixedIncomeReturnRate,
      @NotNull BigDecimal equityReturnRate,
      @Min(0) Integer pensionStartAge,
      @NotNull @PositiveOrZero BigDecimal annualPension,
      @NotNull BigDecimal capitalGainTaxRate,
      @Min(1900) @Max(9999) int startYear,
      @NotNull @PositiveOrZero BigDecimal annualDiscretionaryExpenses,
      @NotNull List<@Valid EventDto> futureEvents,
      @NotNull BigDecimal rentalIncomeGrowthSpread,
      @NotNull BigDecimal spendingGrowthSpread,
      @NotNull SimulationFundingStrategy fundingStrategy,
      @NotNull @PositiveOrZero BigDecimal safeReserveYears,
      @NotNull BigDecimal equityHarvestMinimumReturnRate,
      @NotNull @PositiveOrZero BigDecimal equityGainHarvestRate,
      boolean allowEmergencyEquityWithdrawal,
      @Min(0) @Max(150) int retirementAge,
      @NotNull @PositiveOrZero BigDecimal annualEmploymentIncome,
      @NotNull @PositiveOrZero BigDecimal annualPreRetirementContribution,
      @NotEmpty List<RetirementFundingSource> fundingOrder,
      @NotNull List<@Valid ExpenseStepDto> expenseProfile) {

    SimulationAssumptions toDomain() {
      return new SimulationAssumptions(
          currentAge,
          endAge,
          annualLivingExpenses,
          inflationRate,
          fixedIncomeReturnRate,
          equityReturnRate,
          pensionStartAge,
          annualPension,
          capitalGainTaxRate,
          startYear,
          annualDiscretionaryExpenses,
          futureEvents.stream().map(EventDto::toDomain).toList(),
          rentalIncomeGrowthSpread,
          spendingGrowthSpread,
          fundingStrategy,
          safeReserveYears,
          equityHarvestMinimumReturnRate,
          equityGainHarvestRate,
          allowEmergencyEquityWithdrawal,
          retirementAge,
          annualEmploymentIncome,
          annualPreRetirementContribution,
          fundingOrder,
          new ExpenseProfile(expenseProfile.stream().map(ExpenseStepDto::toDomain).toList()));
    }

    static AssumptionsDto from(SimulationAssumptions source) {
      return new AssumptionsDto(
          source.currentAge(),
          source.endAge(),
          source.annualLivingExpenses(),
          source.inflationRate(),
          source.fixedIncomeReturnRate(),
          source.equityReturnRate(),
          source.pensionStartAge(),
          source.annualPension(),
          source.capitalGainTaxRate(),
          source.startYear(),
          source.annualDiscretionaryExpenses(),
          source.futureEvents().stream().map(EventDto::from).toList(),
          source.rentalIncomeGrowthSpread(),
          source.spendingGrowthSpread(),
          source.fundingStrategy(),
          source.safeReserveYears(),
          source.equityHarvestMinimumReturnRate(),
          source.equityGainHarvestRate(),
          source.allowEmergencyEquityWithdrawal(),
          source.retirementAge(),
          source.annualEmploymentIncome(),
          source.annualPreRetirementContribution(),
          source.fundingOrder(),
          source.expenseProfile().steps().stream().map(ExpenseStepDto::from).toList());
    }
  }
}
