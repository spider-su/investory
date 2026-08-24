package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractTermEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RentalContractServiceTest {
  @Test
  void rejectsContractsForNonRealEstateAssets() {
    var assets = mock(LongTermAssetRepository.class);
    var contracts = mock(LongTermAssetRentalContractRepository.class);
    var asset = new LongTermAssetEntity();
    asset.setId(7L);
    asset.setPortfolioId(1L);
    asset.setType(LongTermAssetType.BOND);
    when(assets.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(asset));

    var service = new RentalContractService(assets, contracts);

    assertThatThrownBy(
            () ->
                service.create(
                    1L, 7L, java.time.LocalDate.of(2026, 1, 1), null, java.util.List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("real-estate");
  }

  @Test
  void replacementContractInheritsTermsThatWereNotSupplied() {
    var assets = mock(LongTermAssetRepository.class);
    var contracts = mock(LongTermAssetRentalContractRepository.class);
    var asset = realEstate();
    var previous = contract(10L, LocalDate.of(2025, 1, 1), null);
    previous.getTerms().add(term(previous, CashFlowType.RENT, "3000", Frequency.MONTHLY, false));
    previous
        .getTerms()
        .add(term(previous, CashFlowType.PROPERTY_TAX, "600", Frequency.ANNUAL, false));
    previous.getTerms().add(term(previous, CashFlowType.INSURANCE, "900", Frequency.ANNUAL, false));
    when(assets.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(asset));
    when(contracts.findAllByAssetIdOrderByStartDate(7L)).thenReturn(List.of(previous));
    when(contracts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var created =
        new RentalContractService(assets, contracts)
            .create(
                1L,
                7L,
                LocalDate.of(2026, 1, 1),
                null,
                List.of(
                    new RentalContractModel.Term(
                        CashFlowTypeModel.RENT,
                        new BigDecimal("3300"),
                        FrequencyModel.MONTHLY,
                        false)));

    assertThat(previous.getTerminatedDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    assertThat(created.getTerms())
        .extracting(LongTermAssetRentalContractTermEntity::getType)
        .containsExactlyInAnyOrder(
            CashFlowType.RENT, CashFlowType.PROPERTY_TAX, CashFlowType.INSURANCE);
    assertThat(created.getTerms())
        .filteredOn(t -> t.getType() == CashFlowType.RENT)
        .singleElement()
        .extracting(LongTermAssetRentalContractTermEntity::getAmount)
        .isEqualTo(new BigDecimal("3300"));
  }

  @Test
  void terminationRejectsOverlapWithSuccessorBeforeMutation() {
    var assets = mock(LongTermAssetRepository.class);
    var contracts = mock(LongTermAssetRentalContractRepository.class);
    var current = contract(10L, LocalDate.of(2025, 1, 1), null);
    current.setTerminatedDate(LocalDate.of(2025, 12, 31));
    var successor = contract(11L, LocalDate.of(2026, 1, 1), null);
    when(assets.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(realEstate()));
    when(contracts.findById(10L)).thenReturn(Optional.of(current));
    when(contracts.findAllByAssetIdOrderByStartDate(7L)).thenReturn(List.of(current, successor));

    assertThatThrownBy(
            () ->
                new RentalContractService(assets, contracts)
                    .terminate(1L, 7L, 10L, LocalDate.of(2026, 1, 31)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Overlapping");

    assertThat(current.getTerminatedDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    verify(contracts, never()).save(current);
  }

  private static LongTermAssetEntity realEstate() {
    var asset = new LongTermAssetEntity();
    asset.setId(7L);
    asset.setPortfolioId(1L);
    asset.setType(LongTermAssetType.REAL_ESTATE);
    return asset;
  }

  private static LongTermAssetRentalContractEntity contract(
      Long id, LocalDate start, LocalDate end) {
    var contract = new LongTermAssetRentalContractEntity();
    contract.setId(id);
    contract.setAssetId(7L);
    contract.setStartDate(start);
    contract.setEndDate(end);
    return contract;
  }

  private static LongTermAssetRentalContractTermEntity term(
      LongTermAssetRentalContractEntity contract,
      CashFlowType type,
      String amount,
      Frequency frequency,
      boolean tenant) {
    var term = new LongTermAssetRentalContractTermEntity();
    term.setContract(contract);
    term.setType(type);
    term.setAmount(new BigDecimal(amount));
    term.setFrequency(frequency);
    term.setPaidByTenant(tenant);
    return term;
  }
}
