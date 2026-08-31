package com.smartbox.investory.longterm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.application.service.RealEstatePlanningCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Real Estate Planning Calculator")
class RealEstatePlanningCalculatorTest {
  private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

  @DisplayName(
      "contract Uses Captured Tax Base And Excludes Landlord Paid Fees From Tenant Payment")
  @Test
  void contractUsesCapturedTaxBaseAndExcludesLandlordPaidFeesFromTenantPayment() {
    var contract =
        new RentalContractModel(
            1L,
            LocalDate.of(2026, 1, 1),
            null,
            null,
            false,
            new BigDecimal("2500"),
            null,
            null,
            null,
            List.of(
                new RentalContractModel.Term(
                    CashFlowType.RENT, new BigDecimal("3000"), Frequency.MONTHLY, false),
                new RentalContractModel.Term(
                    CashFlowType.ADMIN_FEE, new BigDecimal("600"), Frequency.MONTHLY, false),
                new RentalContractModel.Term(
                    CashFlowType.UTILITIES, new BigDecimal("200"), Frequency.MONTHLY, true)));

    var result =
        new RealEstatePlanningCalculator()
            .calculate(
                new BigDecimal("700000"),
                new BigDecimal("9999"),
                false,
                List.of(contract),
                DATE,
                new BigDecimal("0.085"));

    assertEquals(new BigDecimal("3200"), result.totalPaymentMonthly());
    assertEquals(new BigDecimal("2550.000"), result.annualTax());
    assertEquals(new BigDecimal("2500"), result.taxBase());
  }

  @DisplayName("vacant Property Has No Rental Tax Or Negative Income")
  @Test
  void vacantPropertyHasNoRentalTaxOrNegativeIncome() {
    var result =
        new RealEstatePlanningCalculator()
            .calculate(
                new BigDecimal("700000"),
                new BigDecimal("2500"),
                false,
                List.of(),
                DATE,
                new BigDecimal("0.085"));

    assertEquals(BigDecimal.ZERO, result.annualTax());
    assertEquals(BigDecimal.ZERO, result.netMonthlyIncome());
    assertEquals(BigDecimal.ZERO, result.incomeYield());
  }
}
