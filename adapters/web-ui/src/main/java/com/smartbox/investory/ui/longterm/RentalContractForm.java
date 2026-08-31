package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Rental-contract HTML form and adapter-to-command mapping. */
@Getter
@Setter
public final class RentalContractForm {
  private String tenantName;
  private String tenantEmail;
  private String tenantPhone;
  private LocalDate startDate;
  private LocalDate endDate;
  private BigDecimal monthlyTaxBase;
  private String rentalTaxOwnership = "INHERIT";
  private boolean endCurrentContractBeforeStart;
  private BigDecimal rent;
  private Frequency rentFrequency = Frequency.MONTHLY;
  private BigDecimal parkingRent;
  private Frequency parkingRentFrequency = Frequency.MONTHLY;
  private BigDecimal administrationFee;
  private Frequency administrationFeeFrequency = Frequency.MONTHLY;
  private boolean administrationFeePaidByTenant;
  private BigDecimal utilities;
  private Frequency utilitiesFrequency = Frequency.MONTHLY;
  private boolean utilitiesPaidByTenant;
  private BigDecimal otherIncome;
  private Frequency otherIncomeFrequency = Frequency.MONTHLY;
  private BigDecimal otherExpense;
  private Frequency otherExpenseFrequency = Frequency.MONTHLY;
  private boolean otherExpensePaidByTenant;
  private BigDecimal annualPropertyTax;
  private Frequency propertyTaxFrequency = Frequency.ANNUAL;
  private boolean propertyTaxPaidByTenant;
  private BigDecimal annualInsurance;
  private Frequency insuranceFrequency = Frequency.ANNUAL;
  private boolean insurancePaidByTenant;

  public static RentalContractForm from(RentalContractView contract) {
    var form = new RentalContractForm();
    form.tenantName = contract.tenantName();
    form.tenantEmail = contract.tenantEmail();
    form.tenantPhone = contract.tenantPhone();
    form.startDate = contract.startDate();
    form.endDate = contract.endDate();
    form.monthlyTaxBase = contract.monthlyTaxBase();
    form.rentalTaxOwnership =
        contract.rentalTaxPaidByTenant() == null
            ? "INHERIT"
            : contract.rentalTaxPaidByTenant() ? "TENANT" : "LANDLORD";
    contract.terms().forEach(form::copy);
    return form;
  }

  RentalContractCommand createCommand(Long portfolioId, Long assetId) {
    return new RentalContractCommand(
        portfolioId,
        assetId,
        tenantName,
        tenantEmail,
        tenantPhone,
        startDate,
        endDate,
        monthlyTaxBase,
        rentalTaxPaidByTenant(),
        endCurrentContractBeforeStart,
        terms());
  }

  UpdateRentalContractCommand updateCommand(Long portfolioId, Long assetId, Long contractId) {
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
        rentalTaxPaidByTenant(),
        usesPropertyTaxPayerDefault(),
        terms());
  }

  private void copy(RentalTermView term) {
    switch (term.type()) {
      case RENT -> {
        rent = term.amount();
        rentFrequency = term.frequency();
      }
      case PARKING_RENT -> {
        parkingRent = term.amount();
        parkingRentFrequency = term.frequency();
      }
      case ADMIN_FEE -> {
        administrationFee = term.amount();
        administrationFeeFrequency = term.frequency();
        administrationFeePaidByTenant = term.paidByTenant();
      }
      case UTILITIES -> {
        utilities = term.amount();
        utilitiesFrequency = term.frequency();
        utilitiesPaidByTenant = term.paidByTenant();
      }
      case OTHER_INCOME -> {
        otherIncome = term.amount();
        otherIncomeFrequency = term.frequency();
      }
      case OTHER_EXPENSE -> {
        otherExpense = term.amount();
        otherExpenseFrequency = term.frequency();
        otherExpensePaidByTenant = term.paidByTenant();
      }
      case PROPERTY_TAX -> {
        annualPropertyTax = term.amount();
        propertyTaxFrequency = term.frequency();
        propertyTaxPaidByTenant = term.paidByTenant();
      }
      case INSURANCE -> {
        annualInsurance = term.amount();
        insuranceFrequency = term.frequency();
        insurancePaidByTenant = term.paidByTenant();
      }
    }
  }

  private Boolean rentalTaxPaidByTenant() {
    return switch (rentalTaxOwnership == null ? "INHERIT" : rentalTaxOwnership) {
      case "TENANT" -> Boolean.TRUE;
      case "LANDLORD" -> Boolean.FALSE;
      default -> null;
    };
  }

  private boolean usesPropertyTaxPayerDefault() {
    return rentalTaxOwnership == null || "INHERIT".equals(rentalTaxOwnership);
  }

  private List<RentalTermCommand> terms() {
    var terms = new ArrayList<RentalTermCommand>();
    add(terms, CashFlowType.RENT, rent, rentFrequency, false);
    add(terms, CashFlowType.PARKING_RENT, parkingRent, parkingRentFrequency, false);
    add(
        terms,
        CashFlowType.ADMIN_FEE,
        administrationFee,
        administrationFeeFrequency,
        administrationFeePaidByTenant);
    add(terms, CashFlowType.UTILITIES, utilities, utilitiesFrequency, utilitiesPaidByTenant);
    add(terms, CashFlowType.OTHER_INCOME, otherIncome, otherIncomeFrequency, false);
    add(
        terms,
        CashFlowType.OTHER_EXPENSE,
        otherExpense,
        otherExpenseFrequency,
        otherExpensePaidByTenant);
    add(
        terms,
        CashFlowType.PROPERTY_TAX,
        annualPropertyTax,
        propertyTaxFrequency,
        propertyTaxPaidByTenant);
    add(terms, CashFlowType.INSURANCE, annualInsurance, insuranceFrequency, insurancePaidByTenant);
    return List.copyOf(terms);
  }

  private static void add(
      List<RentalTermCommand> terms,
      CashFlowType type,
      BigDecimal amount,
      Frequency frequency,
      boolean paidByTenant) {
    if (amount != null) {
      terms.add(
          new RentalTermCommand(
              type, amount, frequency == null ? defaultFrequency(type) : frequency, paidByTenant));
    }
  }

  private static Frequency defaultFrequency(CashFlowType type) {
    return type == CashFlowType.PROPERTY_TAX || type == CashFlowType.INSURANCE
        ? Frequency.ANNUAL
        : Frequency.MONTHLY;
  }
}
