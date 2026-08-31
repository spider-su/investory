package com.smartbox.investory.longterm.web;

import com.smartbox.investory.longterm.api.LongTermAssetRateConversion;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Validated HTTP request contracts for the Long-Term REST adapter. */
final class LongTermAssetRestContracts {
  private LongTermAssetRestContracts() {}

  static BigDecimal percentToRate(BigDecimal percent) {
    return LongTermAssetRateConversion.percentToRate(percent);
  }

  public record AssetRequest(
      @NotBlank @Size(max = 200) String name,
      @NotNull LongTermAssetType type,
      @NotNull CurrencyType currency,
      @NotNull LocalDate acquisitionDate,
      @PositiveOrZero BigDecimal acquisitionValue,
      @PositiveOrZero BigDecimal currentValue,
      @PositiveOrZero BigDecimal taxBase,
      boolean active,
      @Size(max = 4000) String notes,
      boolean rentalTaxPaidByTenant) {
    @AssertTrue(message = "generic asset endpoint accepts OTHER assets only")
    public boolean isOtherAsset() {
      return type == null || type == LongTermAssetType.OTHER;
    }

    AssetCommand toCommand(Long portfolioId, Long id) {
      return new AssetCommand(
          portfolioId,
          id,
          name,
          type,
          currency,
          acquisitionDate,
          acquisitionValue,
          currentValue,
          taxBase,
          active,
          notes,
          rentalTaxPaidByTenant);
    }
  }

  public record AssetPatchRequest(
      @Size(max = 200) String name,
      LongTermAssetType type,
      CurrencyType currency,
      LocalDate acquisitionDate,
      @PositiveOrZero BigDecimal acquisitionValue,
      @PositiveOrZero BigDecimal currentValue,
      @PositiveOrZero BigDecimal taxBase,
      Boolean active,
      @Size(max = 4000) String notes,
      Boolean rentalTaxPaidByTenant) {
    @AssertTrue(message = "generic asset endpoint accepts OTHER assets only")
    public boolean isOtherAsset() {
      return type == null || type == LongTermAssetType.OTHER;
    }

    AssetPatchCommand toCommand(Long portfolioId, Long id) {
      return new AssetPatchCommand(
          portfolioId,
          id,
          name,
          type,
          currency,
          acquisitionDate,
          acquisitionValue,
          currentValue,
          taxBase,
          active,
          notes,
          rentalTaxPaidByTenant);
    }
  }

  public record CashReserveRequest(
      @NotBlank @Size(max = 200) String name,
      @NotNull CurrencyType currency,
      @NotNull @PositiveOrZero BigDecimal value,
      @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal annualReturnPercent,
      @NotNull LocalDate effectiveFrom,
      @Size(max = 4000) String notes) {
    CashReserveCommand toCommand(Long portfolioId, Long id) {
      return new CashReserveCommand(
          portfolioId, id, name, currency, value, percentToRate(annualReturnPercent), notes);
    }
  }

  public record BondRequest(
      @NotBlank @Size(max = 200) String name,
      @NotNull CurrencyType currency,
      @NotNull @PositiveOrZero BigDecimal value,
      @NotNull LocalDate acquisitionDate,
      @NotNull LocalDate maturityDate,
      @NotNull InterestTreatment interestTreatment,
      @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal annualRatePercent,
      @Size(max = 4000) String notes) {
    @AssertTrue(message = "maturityDate must not precede acquisitionDate")
    public boolean hasValidDateRange() {
      return acquisitionDate == null
          || maturityDate == null
          || !maturityDate.isBefore(acquisitionDate);
    }

    BondCommand toCommand(Long portfolioId, Long id) {
      return new BondCommand(
          portfolioId,
          id,
          name,
          currency,
          value,
          acquisitionDate,
          maturityDate,
          interestTreatment,
          percentToRate(annualRatePercent),
          notes);
    }
  }

  public record DepositRequest(
      @NotBlank @Size(max = 200) String name,
      @NotNull CurrencyType currency,
      @NotNull @PositiveOrZero BigDecimal value,
      @NotNull LocalDate acquisitionDate,
      @NotNull LocalDate maturityDate,
      @NotNull InterestTreatment interestTreatment,
      @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal annualInterestRatePercent,
      @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal taxRatePercent,
      @Size(max = 4000) String notes) {
    @AssertTrue(message = "maturityDate must not precede acquisitionDate")
    public boolean hasValidDateRange() {
      return acquisitionDate == null
          || maturityDate == null
          || !maturityDate.isBefore(acquisitionDate);
    }

    DepositCommand toCommand(Long portfolioId) {
      return new DepositCommand(
          portfolioId,
          name,
          currency,
          value,
          acquisitionDate,
          maturityDate,
          interestTreatment,
          percentToRate(annualInterestRatePercent),
          percentToRate(taxRatePercent),
          notes);
    }

    AssetCommand toAssetCommand(Long portfolioId, Long id) {
      return new AssetCommand(
          portfolioId,
          id,
          name,
          LongTermAssetType.DEPOSIT,
          currency,
          acquisitionDate,
          value,
          value,
          null,
          true,
          notes,
          false);
    }

    DepositDetailsCommand toDetailsCommand() {
      return new DepositDetailsCommand(
          maturityDate,
          percentToRate(annualInterestRatePercent),
          percentToRate(taxRatePercent),
          interestTreatment);
    }
  }

  public record RealEstateRequest(
      @NotBlank @Size(max = 200) String name,
      @NotNull CurrencyType currency,
      @NotNull LocalDate acquisitionDate,
      @PositiveOrZero BigDecimal acquisitionValue,
      @PositiveOrZero BigDecimal currentValue,
      @PositiveOrZero BigDecimal taxBase,
      @PositiveOrZero BigDecimal monthlyRent,
      @PositiveOrZero BigDecimal monthlyParkingIncome,
      @PositiveOrZero BigDecimal monthlyAdministrationCost,
      @PositiveOrZero BigDecimal monthlyOtherCost,
      @PositiveOrZero BigDecimal annualPropertyTax,
      @PositiveOrZero BigDecimal annualInsurance,
      @NotNull LocalDate effectiveFrom,
      @DecimalMin("-100.0") @DecimalMax("100.0") BigDecimal expectedAnnualGrowthRatePercent,
      @Size(max = 4000) String notes,
      boolean rentalTaxPaidByTenant) {
    RealEstateEntryModel toModel() {
      return new RealEstateEntryModel(
          name,
          currency,
          acquisitionDate,
          acquisitionValue,
          currentValue,
          taxBase,
          monthlyRent,
          monthlyParkingIncome,
          monthlyAdministrationCost,
          monthlyOtherCost,
          annualPropertyTax,
          annualInsurance,
          effectiveFrom,
          percentToRate(expectedAnnualGrowthRatePercent),
          notes,
          rentalTaxPaidByTenant);
    }
  }

  public record RentalTermRequest(
      @NotNull CashFlowType type,
      @NotNull @PositiveOrZero BigDecimal amount,
      @NotNull Frequency frequency,
      boolean paidByTenant) {
    RentalTermCommand toCommand() {
      return new RentalTermCommand(type, amount, frequency, paidByTenant);
    }
  }

  public record RentalContractCreateRequest(
      @Size(max = 200) String tenantName,
      @Size(max = 320) String tenantEmail,
      @Size(max = 50) String tenantPhone,
      @NotNull LocalDate startDate,
      LocalDate endDate,
      @PositiveOrZero BigDecimal monthlyTaxBase,
      Boolean rentalTaxPaidByTenant,
      boolean endCurrentContractBeforeStart,
      @NotNull List<@Valid RentalTermRequest> terms) {
    @AssertTrue(message = "endDate must not precede startDate")
    public boolean hasValidDateRange() {
      return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    RentalContractCommand toCommand(Long portfolioId, Long assetId) {
      return new RentalContractCommand(
          portfolioId,
          assetId,
          tenantName,
          tenantEmail,
          tenantPhone,
          startDate,
          endDate,
          monthlyTaxBase,
          rentalTaxPaidByTenant,
          endCurrentContractBeforeStart,
          terms.stream().map(RentalTermRequest::toCommand).toList());
    }
  }

  public record RentalContractUpdateRequest(
      @Size(max = 200) String tenantName,
      @Size(max = 320) String tenantEmail,
      @Size(max = 50) String tenantPhone,
      @NotNull LocalDate startDate,
      LocalDate endDate,
      @PositiveOrZero BigDecimal monthlyTaxBase,
      Boolean rentalTaxPaidByTenant,
      boolean usePropertyTaxPayerDefault,
      @NotNull List<@Valid RentalTermRequest> terms) {
    @AssertTrue(message = "endDate must not precede startDate")
    public boolean hasValidDateRange() {
      return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    UpdateRentalContractCommand toCommand(Long portfolioId, Long assetId, Long contractId) {
      return new UpdateRentalContractCommand(
          portfolioId,
          assetId,
          contractId,
          tenantName,
          tenantEmail,
          tenantPhone,
          startDate,
          endDate,
          monthlyTaxBase,
          rentalTaxPaidByTenant,
          usePropertyTaxPayerDefault,
          terms.stream().map(RentalTermRequest::toCommand).toList());
    }
  }

  public record DateRequest(@NotNull LocalDate date) {}

  public record TaxBaseRequest(@NotNull @PositiveOrZero BigDecimal value) {}

  public record RentalTaxOwnershipRequest(boolean paidByTenant) {}

  public record PropertyGrowthRequest(
      @NotNull @DecimalMin("-100.0") @DecimalMax("100.0") BigDecimal growthRatePercent,
      @NotNull LocalDate from) {}

  public record BondDetailsRequest(
      @NotNull LocalDate maturityDate,
      @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal taxRatePercent,
      @NotNull InterestTreatment interestTreatment,
      @PositiveOrZero BigDecimal redemptionValue) {
    BondDetailsCommand toCommand() {
      return new BondDetailsCommand(
          maturityDate, percentToRate(taxRatePercent), interestTreatment, redemptionValue);
    }
  }

  public record DepositDetailsRequest(
      @NotNull LocalDate maturityDate,
      @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal annualInterestRatePercent,
      @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal taxRatePercent,
      @NotNull InterestTreatment interestTreatment) {
    DepositDetailsCommand toCommand() {
      return new DepositDetailsCommand(
          maturityDate,
          percentToRate(annualInterestRatePercent),
          percentToRate(taxRatePercent),
          interestTreatment);
    }
  }

  public record ValuationRequest(
      @NotNull LocalDate validFrom,
      LocalDate validTo,
      @NotNull @DecimalMin("-100.0") @DecimalMax("100.0") BigDecimal growthRatePercent) {
    @AssertTrue(message = "validTo must not precede validFrom")
    public boolean hasValidDateRange() {
      return validFrom == null || validTo == null || !validTo.isBefore(validFrom);
    }

    ValuationCommand toCommand() {
      return new ValuationCommand(validFrom, validTo, percentToRate(growthRatePercent));
    }
  }

  public record RentalTaxRequest(
      @NotNull LocalDate validFrom,
      LocalDate validTo,
      @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal ratePercent) {
    @AssertTrue(message = "validTo must not precede validFrom")
    public boolean hasValidDateRange() {
      return validFrom == null || validTo == null || !validTo.isBefore(validFrom);
    }

    RentalTaxCommand toCommand() {
      return new RentalTaxCommand(validFrom, validTo, percentToRate(ratePercent));
    }
  }
}
