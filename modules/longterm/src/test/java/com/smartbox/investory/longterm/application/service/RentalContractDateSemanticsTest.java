package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.RentalContractStatusModel;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateEntity;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractTermEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RentalContractDateSemanticsTest {
  private static final Long PORTFOLIO_ID = 1L;
  private static final Long ASSET_ID = 3L;
  private static final LocalDate TERMINATION = LocalDate.of(2026, 6, 15);

  @Test
  void terminationDateIsInclusiveForApplicabilityAndStatus() {
    var contract = contract();

    assertThat(RentalContractService.effectiveEnd(contract)).isEqualTo(TERMINATION);
    assertThat(RentalContractService.applies(contract, LocalDate.of(2026, 6, 14))).isTrue();
    assertThat(RentalContractService.applies(contract, TERMINATION)).isTrue();
    assertThat(RentalContractService.applies(contract, TERMINATION.plusDays(1))).isFalse();
    assertThat(RentalContractService.status(contract, LocalDate.of(2026, 6, 14)))
        .isEqualTo(RentalContractStatusModel.CURRENT);
    assertThat(RentalContractService.status(contract, TERMINATION))
        .isEqualTo(RentalContractStatusModel.CURRENT);
    assertThat(RentalContractService.status(contract, TERMINATION.plusDays(1)))
        .isEqualTo(RentalContractStatusModel.TERMINATED);
  }

  @Test
  void paymentAuditUsesTheSameInclusiveTerminationBoundary() {
    var realEstates = mock(RealEstateRepository.class);
    var contracts = mock(LongTermAssetRentalContractRepository.class);
    var estate = new RealEstateEntity();
    estate.setId(ASSET_ID);
    estate.setPortfolioId(PORTFOLIO_ID);
    estate.setName("Rental");
    estate.setCurrency(CurrencyType.PLN);
    when(realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(estate));
    when(contracts.findAllWithTermsByAssetIdIn(anyCollection())).thenReturn(List.of(contract()));
    var audit = new LongTermAssetPaymentAuditService(realEstates, contracts);

    assertThat(audit.paymentAudit(PORTFOLIO_ID, LocalDate.of(2026, 6, 14))).hasSize(1);
    assertThat(audit.paymentAudit(PORTFOLIO_ID, TERMINATION)).hasSize(1);
    assertThat(audit.paymentAudit(PORTFOLIO_ID, TERMINATION.plusDays(1))).isEmpty();
  }

  private static LongTermAssetRentalContractEntity contract() {
    var contract = new LongTermAssetRentalContractEntity();
    contract.setAssetId(ASSET_ID);
    contract.setStartDate(LocalDate.of(2026, 1, 1));
    contract.setEndDate(LocalDate.of(2026, 12, 31));
    contract.setTerminatedDate(TERMINATION);
    contract.setTenantName("Tenant");
    var term = new LongTermAssetRentalContractTermEntity();
    term.setType(CashFlowType.ADMIN_FEE);
    term.setAmount(java.math.BigDecimal.ONE);
    term.setFrequency(Frequency.MONTHLY);
    term.setPaidByTenant(true);
    contract.setTerms(List.of(term));
    return contract;
  }
}
