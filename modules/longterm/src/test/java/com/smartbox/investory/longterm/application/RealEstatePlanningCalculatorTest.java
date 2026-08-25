package com.smartbox.investory.longterm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.longterm.application.model.RealEstatePlanningSummary;
import com.smartbox.investory.longterm.application.service.RealEstatePlanningCalculator;
import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetCashFlowEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealEstatePlanningCalculatorTest {
  private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

  @Test
  void preservesCurrentApartmentPlanningFormula() {
    RealEstatePlanningSummary result =
        new RealEstatePlanningCalculator()
            .calculate(
                new BigDecimal("780000"),
                new BigDecimal("2800"),
                List.of(
                    flow(CashFlowType.RENT, "2650", Frequency.MONTHLY),
                        flow(CashFlowType.PARKING_RENT, "250", Frequency.MONTHLY),
                    flow(CashFlowType.ADMIN_FEE, "687", Frequency.MONTHLY),
                        flow(CashFlowType.UTILITIES, "100", Frequency.MONTHLY),
                    flow(CashFlowType.OTHER_INCOME, "75", Frequency.MONTHLY),
                        flow(CashFlowType.OTHER_EXPENSE, "400", Frequency.MONTHLY),
                    flow(CashFlowType.PROPERTY_TAX, "320", Frequency.ANNUAL),
                        flow(CashFlowType.INSURANCE, "200", Frequency.ANNUAL)),
                DATE);

    assertEquals(new BigDecimal("3687"), result.totalPaymentMonthly());
    assertEquals(new BigDecimal("2975"), result.monthlyIncome());
    assertEquals(new BigDecimal("2856.000"), result.annualTax());
    assertEquals(new BigDecimal("681.333333333333333333"), result.monthlyReduce());
    assertEquals(new BigDecimal("2293.666666666666666667"), result.netMonthlyIncome());
    assertEquals(new BigDecimal("0.035287179487"), result.incomeYield());
  }

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
                    CashFlowTypeModel.RENT,
                    new BigDecimal("3000"),
                    FrequencyModel.MONTHLY,
                    false),
                new RentalContractModel.Term(
                    CashFlowTypeModel.ADMIN_FEE,
                    new BigDecimal("600"),
                    FrequencyModel.MONTHLY,
                    false),
                new RentalContractModel.Term(
                    CashFlowTypeModel.UTILITIES,
                    new BigDecimal("200"),
                    FrequencyModel.MONTHLY,
                    true)));

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

  private static LongTermAssetCashFlowEntity flow(
      CashFlowType type, String amount, Frequency frequency) {
    LongTermAssetCashFlowEntity flow = new LongTermAssetCashFlowEntity();
    flow.setType(type);
    flow.setAmount(new BigDecimal(amount));
    flow.setFrequency(frequency);
    flow.setValidFrom(DATE.minusDays(1));
    return flow;
  }
}
