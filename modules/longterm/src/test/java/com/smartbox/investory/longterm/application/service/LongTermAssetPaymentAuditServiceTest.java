package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateEntity;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractTermEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LongTermAssetPaymentAuditServiceTest {
  @Test
  void monthlyTenantPaymentIncludesTenantPaidExpenses() {
    var realEstates = mock(RealEstateRepository.class);
    var contracts = mock(LongTermAssetRentalContractRepository.class);
    var estate = new RealEstateEntity();
    estate.setId(10L);
    estate.setPortfolioId(1L);
    estate.setName("Rental home");
    estate.setCurrency(CurrencyType.PLN);
    var contract = new LongTermAssetRentalContractEntity();
    contract.setAssetId(10L);
    contract.setTenantName("Tenant");
    contract.setStartDate(LocalDate.of(2026, 1, 1));
    contract.setTerms(
        List.of(
            term(CashFlowType.RENT, "1000", Frequency.MONTHLY, false),
            term(CashFlowType.OTHER_INCOME, "1200", Frequency.ANNUAL, false),
            term(CashFlowType.UTILITIES, "200", Frequency.MONTHLY, true),
            term(CashFlowType.ADMIN_FEE, "300", Frequency.MONTHLY, false)));
    when(realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(1L))
        .thenReturn(List.of(estate));
    when(contracts.findAllWithTermsByAssetIdIn(anyCollection())).thenReturn(List.of(contract));

    var rows =
        new LongTermAssetPaymentAuditService(realEstates, contracts)
            .paymentAudit(1L, LocalDate.of(2026, 6, 1));
    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.assetName()).isEqualTo("Rental home");
              assertThat(row.tenantName()).isEqualTo("Tenant");
              assertThat(row.totalMonthlyPayment()).isEqualByComparingTo("1300");
            });
  }

  private static LongTermAssetRentalContractTermEntity term(
      CashFlowType type, String amount, Frequency frequency, boolean paidByTenant) {
    var term = new LongTermAssetRentalContractTermEntity();
    term.setType(type);
    term.setAmount(new BigDecimal(amount));
    term.setFrequency(frequency);
    term.setPaidByTenant(paidByTenant);
    return term;
  }
}
