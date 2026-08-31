package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondDetailsEntity;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondDetailsRepository;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodRepository;
import com.smartbox.investory.longterm.infrastructure.deposit.LongTermAssetDepositDetailsRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyEntity;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyRepository;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodEntity;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LongTermAssetRelatedDataLoaderTest {
  private LongTermAssetValuationPeriodRepository valuations;
  private LongTermAssetBondRatePeriodRepository bondRates;
  private LongTermAssetBondDetailsRepository bonds;
  private LongTermAssetDepositDetailsRepository deposits;
  private RentalTaxPolicyRepository taxPolicies;
  private LongTermAssetRentalContractRepository contracts;
  private LongTermAssetRelatedDataLoader loader;

  @BeforeEach
  void setUp() {
    valuations = mock(LongTermAssetValuationPeriodRepository.class);
    bondRates = mock(LongTermAssetBondRatePeriodRepository.class);
    bonds = mock(LongTermAssetBondDetailsRepository.class);
    deposits = mock(LongTermAssetDepositDetailsRepository.class);
    taxPolicies = mock(RentalTaxPolicyRepository.class);
    contracts = mock(LongTermAssetRentalContractRepository.class);
    loader =
        new LongTermAssetRelatedDataLoader(
            valuations, bondRates, bonds, deposits, taxPolicies, contracts);
  }

  @Test
  void emptyAssetSetDoesNotQueryRepositories() {
    assertThat(loader.load(1L, List.of(), LocalDate.of(2026, 1, 1)))
        .isEqualTo(LongTermAssetRelatedDataLoader.Data.empty());
    verifyNoInteractions(valuations, bondRates, bonds, deposits, taxPolicies, contracts);
  }

  @Test
  void loadGroupsRowsAndSelectsEffectiveTaxPolicy() {
    var oldPolicy = policy("0.07", "2025-01-01", "2025-12-31");
    var currentPolicy = policy("0.09", "2026-01-01", null);
    when(taxPolicies.findAllByPortfolioIdOrderByValidFrom(1L))
        .thenReturn(List.of(oldPolicy, currentPolicy));
    var valuation = new LongTermAssetValuationPeriodEntity();
    valuation.setAssetId(5L);
    when(valuations.findAllByAssetIdInOrderByAssetIdAscValidFromAsc(List.of(5L)))
        .thenReturn(List.of(valuation));
    when(bondRates.findAllByAssetIdInOrderByAssetIdAscValidFromAsc(List.of(5L)))
        .thenReturn(List.of());
    var bond = new LongTermAssetBondDetailsEntity();
    bond.setAssetId(5L);
    when(bonds.findAllById(List.of(5L))).thenReturn(List.of(bond));
    when(deposits.findAllById(List.of(5L))).thenReturn(List.of());
    when(contracts.findAllWithTermsByAssetIdIn(List.of(5L))).thenReturn(List.of());

    var result = loader.load(1L, List.of(5L), LocalDate.of(2026, 6, 1));

    assertThat(result.rentalTaxRate()).isEqualByComparingTo("0.09");
    assertThat(result.valuations().get(5L)).containsExactly(valuation);
    assertThat(result.bonds()).containsEntry(5L, bond);
  }

  private static RentalTaxPolicyEntity policy(String rate, String from, String to) {
    var policy = new RentalTaxPolicyEntity();
    policy.setRate(new BigDecimal(rate));
    policy.setValidFrom(LocalDate.parse(from));
    policy.setValidTo(to == null ? null : LocalDate.parse(to));
    return policy;
  }
}
