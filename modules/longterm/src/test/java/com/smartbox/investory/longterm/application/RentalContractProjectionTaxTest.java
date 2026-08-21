package com.smartbox.investory.longterm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.api.model.RentalIncomeProjectionModel;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RentalContractProjectionTaxTest {
  @Test
  void contractTaxOwnershipOverridesAssetAndMissingTermsDoNotCarryForward() {
    var a =
        contract(
            LocalDate.of(2026, 1, 1),
            true,
            term(CashFlowTypeModel.RENT, "3000", false),
            term(CashFlowTypeModel.PARKING_RENT, "300", false));
    var b = contract(LocalDate.of(2027, 1, 1), false, term(CashFlowTypeModel.RENT, "3500", false));
    var asset = asset(List.of(a, b), false);
    var result = RentalIncomeProjectionModel.project(asset, Map.of(), 2027, BigDecimal.ZERO);
    assertEquals(new BigDecimal("42000"), result.grossIncome());
    assertEquals(BigDecimal.ZERO, result.expenses());
    assertEquals(0, new BigDecimal("850").compareTo(result.tax()));
    assertEquals(0, new BigDecimal("41150").compareTo(result.netIncome()));
  }

  @Test
  void nullContractTaxOwnershipUsesAssetDefault() {
    var asset =
        asset(
            List.of(
                contract(LocalDate.of(2026, 1, 1), null, term(CashFlowTypeModel.RENT, "3000", false))),
            true);
    assertEquals(
        BigDecimal.ZERO,
        RentalIncomeProjectionModel.project(asset, Map.of(), 2026, BigDecimal.ZERO).tax());
  }

  private static RentalContractModel contract(
      LocalDate start, Boolean tax, RentalContractModel.Term... terms) {
    return new RentalContractModel(null, start, null, null, tax, List.of(terms));
  }

  private static RentalContractModel.Term term(CashFlowTypeModel type, String amount, boolean tenant) {
    return new RentalContractModel.Term(type, new BigDecimal(amount), FrequencyModel.MONTHLY, tenant);
  }

  private static LongTermAssetProjectionModel asset(
      List<RentalContractModel> contracts, boolean taxTenant) {
    return new LongTermAssetProjectionModel(
        1L,
        "A",
        LongTermAssetTypeModel.REAL_ESTATE,
        CurrencyType.USD,
        BigDecimal.ZERO,
        List.of(),
        contracts,
        null,
        null,
        (InterestTreatmentModel) null,
        new BigDecimal("0.085"),
        new BigDecimal("10000"),
        taxTenant);
  }
}
