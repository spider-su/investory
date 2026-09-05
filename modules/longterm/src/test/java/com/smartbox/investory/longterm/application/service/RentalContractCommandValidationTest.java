package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.infrastructure.realestate.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RentalContractCommandValidationTest {
  private final RealEstateRepository estates = mock(RealEstateRepository.class);
  private final LongTermAssetRentalContractRepository contracts =
      mock(LongTermAssetRentalContractRepository.class);
  private final RentalContractService service =
      new RentalContractService(
          estates, contracts, Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC));
  private static final LocalDate START = LocalDate.of(2026, 1, 1);

  @Test
  void nullTerminationIsRejectedWithoutPersistence() {
    assertThatThrownBy(() -> service.terminate(1L, 2L, 3L, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required");
    verifyNoInteractions(estates, contracts);
  }

  @Test
  void terminationMustBeWithinContractAndNotInFuture() {
    var contract = owned();
    contract.setEndDate(LocalDate.of(2026, 5, 31));
    for (var date :
        List.of(START.minusDays(1), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 16))) {
      assertThatThrownBy(() -> service.terminate(1L, 2L, 3L, date))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(contract.getTerminatedDate()).isNull();
    }
    verify(contracts, never()).save(any());
  }

  @Test
  void foreignContractCannotBeEditedOrDeleted() {
    var contract = owned();
    contract.setAssetId(99L);
    assertThatThrownBy(() -> service.delete(1L, 2L, 3L))
        .isInstanceOf(RentalContractNotFoundException.class);
    verify(contracts, never()).delete(any());
  }

  @Test
  void duplicateOrNegativeTermsCannotMutateExistingContract() {
    var contract = owned();
    var rent =
        new RentalContractModel.Term(CashFlowType.RENT, BigDecimal.TEN, Frequency.MONTHLY, false);
    var negative =
        new RentalContractModel.Term(
            CashFlowType.RENT, BigDecimal.ONE.negate(), Frequency.MONTHLY, false);
    for (var terms : List.of(List.of(rent, rent), List.of(negative))) {
      assertThatThrownBy(
              () -> service.update(1L, 2L, 3L, "Changed", null, null, START, null, terms))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(contract.getTenantName()).isEqualTo("Original");
      assertThat(contract.getTerms()).isEmpty();
    }
    verify(contracts, never()).save(any());
  }

  @Test
  void touchingInclusivePeriodsOverlapButFollowingDayDoesNot() {
    var previous = owned();
    previous.setEndDate(LocalDate.of(2026, 2, 1));
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(2L)).thenReturn(List.of(previous));
    assertThatThrownBy(() -> service.create(1L, 2L, previous.getEndDate(), null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Overlapping");
    when(contracts.save(any())).thenAnswer(call -> call.getArgument(0));
    assertThat(
            service
                .create(1L, 2L, previous.getEndDate().plusDays(1), null, List.of())
                .getStartDate())
        .isEqualTo(LocalDate.of(2026, 2, 2));
    verify(estates, times(2)).lockByIdAndPortfolioId(2L, 1L);
  }

  @Test
  void updateReplacesTenantAndCompleteTermStateWithoutChangingIdentity() {
    var contract = owned();
    var old = entityTerm(CashFlowType.ADMIN_FEE, "20", false);
    old.setContract(contract);
    contract.getTerms().add(old);
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(2L)).thenReturn(List.of(contract));
    when(contracts.save(any())).thenAnswer(call -> call.getArgument(0));

    var updated =
        service.update(
            1L,
            2L,
            3L,
            "  New tenant  ",
            "tenant@example.com",
            "+48 123",
            START,
            LocalDate.of(2026, 12, 31),
            List.of(
                new RentalContractModel.Term(
                    CashFlowType.RENT, new BigDecimal("1000"), Frequency.MONTHLY, false)));

    assertThat(updated.getId()).isEqualTo(3L);
    assertThat(updated.getTenantName()).isEqualTo("New tenant");
    assertThat(updated.getTerms())
        .singleElement()
        .satisfies(
            term -> {
              assertThat(term.getType()).isEqualTo(CashFlowType.RENT);
              assertThat(term.getAmount()).isEqualByComparingTo("1000");
              assertThat(term.getContract()).isSameAs(contract);
            });
  }

  @Test
  void endTerminateAndDeleteUseTheOwnedStableContract() {
    var contract = owned();
    contract.setEndDate(LocalDate.of(2026, 12, 31));
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(2L)).thenReturn(List.of(contract));
    when(contracts.save(any())).thenAnswer(call -> call.getArgument(0));

    var ended = service.end(1L, 2L, 3L, LocalDate.of(2026, 11, 30));
    service.terminate(1L, 2L, 3L, LocalDate.of(2026, 6, 15));
    service.delete(1L, 2L, 3L);

    assertThat(ended).isSameAs(contract);
    assertThat(contract.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    assertThat(contract.getTerminatedDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    verify(contracts).delete(contract);
  }

  @Test
  void explicitRolloverShortensAndFlushesThePredecessorBeforeInsert() {
    var previous = owned();
    previous.setEndDate(null);
    when(contracts.findAllByAssetIdOrderByStartDateDescIdDesc(2L)).thenReturn(List.of(previous));
    when(contracts.save(any())).thenAnswer(call -> call.getArgument(0));

    var successor =
        service.create(
            1L, 2L, "Tenant", null, null, LocalDate.of(2026, 2, 1), null, List.of(), true);

    assertThat(previous.getEndDate()).isEqualTo(LocalDate.of(2026, 1, 31));
    assertThat(successor.getStartDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    verify(contracts).flush();
  }

  private LongTermAssetRentalContractEntity owned() {
    when(estates.lockByIdAndPortfolioId(2L, 1L)).thenReturn(Optional.of(new RealEstateEntity()));
    var contract = new LongTermAssetRentalContractEntity();
    contract.setId(3L);
    contract.setAssetId(2L);
    contract.setStartDate(START);
    contract.setTenantName("Original");
    when(contracts.findById(3L)).thenReturn(Optional.of(contract));
    return contract;
  }

  private static LongTermAssetRentalContractTermEntity entityTerm(
      CashFlowType type, String amount, boolean paidByTenant) {
    var term = new LongTermAssetRentalContractTermEntity();
    term.setType(type);
    term.setAmount(new BigDecimal(amount));
    term.setFrequency(Frequency.MONTHLY);
    term.setPaidByTenant(paidByTenant);
    return term;
  }
}
