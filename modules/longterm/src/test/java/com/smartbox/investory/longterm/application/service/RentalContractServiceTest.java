package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.api.model.RentalContractStatusModel;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractTermEntity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Rental Contract Service")
class RentalContractServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2025-06-30T12:00:00Z"), ZoneOffset.UTC);

  private LongTermAssetRepository assets;
  private LongTermAssetRentalContractRepository contracts;
  private RentalContractService service;

  @BeforeEach
  void setUp() {
    assets = mock(LongTermAssetRepository.class);
    contracts = mock(LongTermAssetRentalContractRepository.class);
    service = new RentalContractService(assets, contracts, CLOCK);
    when(assets.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(realEstate()));
    when(contracts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @DisplayName("rejects Contracts For Non Real Estate Assets")
  @Test
  void rejectsContractsForNonRealEstateAssets() {
    var asset = realEstate();
    asset.setType(LongTermAssetType.BOND);
    when(assets.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(asset));

    assertThatThrownBy(() -> service.create(1L, 7L, LocalDate.of(2026, 1, 1), null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("real-estate");
  }

  @DisplayName("create Rejects Overlap Without Mutating Existing Contract")
  @Test
  void createRejectsOverlapWithoutMutatingExistingContract() {
    var previous = contract(10L, LocalDate.of(2025, 1, 1), null);
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(7L)).thenReturn(List.of(previous));

    assertThatThrownBy(
            () ->
                service.create(
                    1L,
                    7L,
                    "Tenant",
                    "tenant@example.com",
                    "+48 123",
                    LocalDate.of(2026, 1, 1),
                    null,
                    null,
                    List.of(termModel(CashFlowType.RENT, "3300", false)),
                    false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Overlapping");

    assertThat(previous.getEndDate()).isNull();
    assertThat(previous.getTerminatedDate()).isNull();
    verify(contracts, never()).save(previous);
  }

  @DisplayName("explicit Rollover Closes Expected Period Without Termination Or Implicit Terms")
  @Test
  void explicitRolloverClosesExpectedPeriodWithoutTerminationOrImplicitTerms() {
    var previous = contract(10L, LocalDate.of(2025, 1, 1), null);
    previous.getTerms().add(term(previous, CashFlowType.PROPERTY_TAX, "600", false));
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(7L)).thenReturn(List.of(previous));

    var created =
        service.create(
            1L,
            7L,
            "New tenant",
            "new@example.com",
            "555-0100",
            LocalDate.of(2026, 1, 1),
            null,
            null,
            List.of(termModel(CashFlowType.RENT, "3300", false)),
            true);

    assertThat(previous.getEndDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    assertThat(previous.getTerminatedDate()).isNull();
    assertThat(created.getTenantName()).isEqualTo("New tenant");
    assertThat(created.getMonthlyTaxBase()).isEqualByComparingTo("2800");
    assertThat(created.getRentalTaxPaidByTenant()).isFalse();
    assertThat(created.getTerms())
        .extracting(LongTermAssetRentalContractTermEntity::getType)
        .containsExactly(CashFlowType.RENT);
    verify(contracts).flush();
  }

  @DisplayName(
      "explicit Rollover Rejects Overlapping Terminated Contract Without Changing Expected End")
  @Test
  void explicitRolloverRejectsOverlappingTerminatedContractWithoutChangingExpectedEnd() {
    var previous = contract(10L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    previous.setTerminatedDate(LocalDate.of(2025, 6, 30));
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(7L)).thenReturn(List.of(previous));

    assertThatThrownBy(
            () ->
                service.create(
                    1L,
                    7L,
                    null,
                    null,
                    null,
                    LocalDate.of(2025, 4, 1),
                    null,
                    null,
                    List.of(),
                    true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("terminated");

    assertThat(previous.getEndDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    assertThat(previous.getTerminatedDate()).isEqualTo(LocalDate.of(2025, 6, 30));
    verify(contracts, never()).save(previous);
  }

  @DisplayName("update Preserves Identity And Atomically Replaces Tenant Dates Tax Payer And Terms")
  @Test
  void updatePreservesIdentityAndAtomicallyReplacesTenantDatesTaxPayerAndTerms() {
    var existing = contract(10L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    existing.getTerms().add(term(existing, CashFlowType.RENT, "3000", false));
    existing.getTerms().add(term(existing, CashFlowType.INSURANCE, "900", false));
    when(contracts.findById(10L)).thenReturn(Optional.of(existing));
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(7L)).thenReturn(List.of(existing));

    var updated =
        service.update(
            1L,
            7L,
            10L,
            "Updated tenant",
            "updated@example.com",
            "+48 999",
            LocalDate.of(2025, 2, 1),
            LocalDate.of(2026, 1, 31),
            Boolean.TRUE,
            List.of(
                termModel(CashFlowType.RENT, "3500", false),
                termModel(CashFlowType.UTILITIES, "500", true)));

    assertThat(updated).isSameAs(existing);
    assertThat(updated.getId()).isEqualTo(10L);
    assertThat(updated.getTenantName()).isEqualTo("Updated tenant");
    assertThat(updated.getTenantEmail()).isEqualTo("updated@example.com");
    assertThat(updated.getTenantPhone()).isEqualTo("+48 999");
    assertThat(updated.getStartDate()).isEqualTo(LocalDate.of(2025, 2, 1));
    assertThat(updated.getEndDate()).isEqualTo(LocalDate.of(2026, 1, 31));
    assertThat(updated.getRentalTaxPaidByTenant()).isTrue();
    assertThat(updated.getTerms())
        .extracting(LongTermAssetRentalContractTermEntity::getType)
        .containsExactly(CashFlowType.RENT, CashFlowType.UTILITIES);
    assertThat(updated.getTerms())
        .filteredOn(term -> term.getType() == CashFlowType.UTILITIES)
        .singleElement()
        .matches(LongTermAssetRentalContractTermEntity::isPaidByTenant);
  }

  @DisplayName("update Can Correct Tax Base And Apply Current Property Tax Payer Default")
  @Test
  void updateCanCorrectTaxBaseAndApplyCurrentPropertyTaxPayerDefault() {
    var existing = contract(10L, LocalDate.of(2025, 1, 1), null);
    existing.setMonthlyTaxBase(new BigDecimal("1000"));
    existing.setRentalTaxPaidByTenant(true);
    when(contracts.findById(10L)).thenReturn(Optional.of(existing));
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(7L)).thenReturn(List.of(existing));

    var updated =
        service.update(
            1L,
            7L,
            10L,
            null,
            null,
            null,
            LocalDate.of(2025, 1, 1),
            null,
            new BigDecimal("3200"),
            null,
            true,
            List.of());

    assertThat(updated.getMonthlyTaxBase()).isEqualByComparingTo("3200");
    assertThat(updated.getRentalTaxPaidByTenant()).isFalse();
  }

  @DisplayName("rejects Negative Monthly Tax Base")
  @Test
  void rejectsNegativeMonthlyTaxBase() {
    assertThatThrownBy(
            () ->
                service.create(
                    1L,
                    7L,
                    null,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    new BigDecimal("-1"),
                    null,
                    List.of(),
                    false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tax base");
  }

  @DisplayName("update Rejects Overlap But Excludes Itself")
  @Test
  void updateRejectsOverlapButExcludesItself() {
    var existing = contract(10L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    var other = contract(11L, LocalDate.of(2026, 1, 1), null);
    when(contracts.findById(10L)).thenReturn(Optional.of(existing));
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(7L))
        .thenReturn(List.of(other, existing));

    assertThatThrownBy(
            () ->
                service.update(
                    1L,
                    7L,
                    10L,
                    null,
                    null,
                    null,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2026, 1, 1),
                    null,
                    List.of()))
        .hasMessageContaining("Overlapping");
  }

  @DisplayName("rejects Duplicate Terms And Invalid Email")
  @Test
  void rejectsDuplicateTermsAndInvalidEmail() {
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(7L)).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.create(
                    1L,
                    7L,
                    null,
                    "not-an-email",
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    null,
                    List.of(),
                    false))
        .hasMessageContaining("email");
    assertThatThrownBy(
            () ->
                service.create(
                    1L,
                    7L,
                    null,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    null,
                    List.of(
                        termModel(CashFlowType.RENT, "1", false),
                        termModel(CashFlowType.RENT, "2", false)),
                    false))
        .hasMessageContaining("Duplicate");
  }

  @DisplayName("delete Verifies Ownership And Does Not Mutate Adjacent Contracts")
  @Test
  void deleteVerifiesOwnershipAndDoesNotMutateAdjacentContracts() {
    var removed = contract(10L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    var adjacent = contract(11L, LocalDate.of(2026, 1, 1), null);
    when(contracts.findById(10L)).thenReturn(Optional.of(removed));

    service.delete(1L, 7L, 10L);

    verify(contracts).delete(removed);
    assertThat(adjacent.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(adjacent.getEndDate()).isNull();
    assertThat(adjacent.getTerminatedDate()).isNull();

    removed.setAssetId(99L);
    assertThatThrownBy(() -> service.delete(1L, 7L, 10L))
        .isInstanceOf(RentalContractNotFoundException.class)
        .hasMessageContaining("contract");
  }

  @DisplayName("validates Early Termination And Calculates Effective End And Status")
  @Test
  void validatesEarlyTerminationAndCalculatesEffectiveEndAndStatus() {
    var current = contract(10L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    when(contracts.findById(10L)).thenReturn(Optional.of(current));
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(7L)).thenReturn(List.of(current));

    service.terminate(1L, 7L, 10L, LocalDate.of(2025, 6, 30));

    assertThat(RentalContractService.effectiveEnd(current)).isEqualTo(LocalDate.of(2025, 6, 30));
    assertThat(RentalContractService.status(current, LocalDate.of(2025, 6, 1)))
        .isEqualTo(RentalContractStatusModel.CURRENT);
    assertThat(RentalContractService.status(current, LocalDate.of(2025, 7, 1)))
        .isEqualTo(RentalContractStatusModel.TERMINATED);
    assertThatThrownBy(() -> service.terminate(1L, 7L, 10L, LocalDate.of(2025, 7, 1)))
        .hasMessageContaining("already terminated");
  }

  @DisplayName("rejects Future Actual Termination Using Application Clock")
  @Test
  void rejectsFutureActualTerminationUsingApplicationClock() {
    var current = contract(10L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    when(contracts.findById(10L)).thenReturn(Optional.of(current));

    assertThatThrownBy(() -> service.terminate(1L, 7L, 10L, LocalDate.of(2025, 7, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("later than today");

    assertThat(current.getTerminatedDate()).isNull();
    verify(contracts, never()).save(current);
  }

  @DisplayName("list Uses Newest First Repository Order")
  @Test
  void listUsesNewestFirstRepositoryOrder() {
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(7L)).thenReturn(List.of());
    service.list(1L, 7L);
    verify(contracts).findAllByAssetIdOrderByStartDateDescIdDesc(7L);
  }

  private static LongTermAssetEntity realEstate() {
    var asset = new LongTermAssetEntity();
    asset.setId(7L);
    asset.setPortfolioId(1L);
    asset.setType(LongTermAssetType.REAL_ESTATE);
    asset.setTaxBase(new BigDecimal("2800"));
    asset.setRentalTaxPaidByTenant(false);
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
      boolean tenant) {
    var term = new LongTermAssetRentalContractTermEntity();
    term.setContract(contract);
    term.setType(type);
    term.setAmount(new BigDecimal(amount));
    term.setFrequency(
        type == CashFlowType.PROPERTY_TAX || type == CashFlowType.INSURANCE
            ? Frequency.ANNUAL
            : Frequency.MONTHLY);
    term.setPaidByTenant(tenant);
    return term;
  }

  private static RentalContractModel.Term termModel(
      CashFlowType type, String amount, boolean tenant) {
    return new RentalContractModel.Term(
        type,
        new BigDecimal(amount),
        type == CashFlowType.PROPERTY_TAX || type == CashFlowType.INSURANCE
            ? Frequency.ANNUAL
            : Frequency.MONTHLY,
        tenant);
  }
}
