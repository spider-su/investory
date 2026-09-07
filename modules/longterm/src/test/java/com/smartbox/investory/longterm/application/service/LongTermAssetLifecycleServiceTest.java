package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.AssetNotFoundException;
import com.smartbox.investory.longterm.infrastructure.bond.BondEntity;
import com.smartbox.investory.longterm.infrastructure.bond.BondRepository;
import com.smartbox.investory.longterm.infrastructure.cash.CashReserveEntity;
import com.smartbox.investory.longterm.infrastructure.cash.CashReserveRepository;
import com.smartbox.investory.longterm.infrastructure.personal.PersonalAssetEntity;
import com.smartbox.investory.longterm.infrastructure.personal.PersonalAssetRepository;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateEntity;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LongTermAssetLifecycleServiceTest {
  private final BondRepository bonds = mock(BondRepository.class);
  private final RealEstateRepository estates = mock(RealEstateRepository.class);
  private final CashReserveRepository cash = mock(CashReserveRepository.class);
  private final PersonalAssetRepository personal = mock(PersonalAssetRepository.class);
  private final com.smartbox.investory.longterm.infrastructure.lifecycle
          .LongTermAssetHistoryRepository
      history =
          mock(
              com.smartbox.investory.longterm.infrastructure.lifecycle
                  .LongTermAssetHistoryRepository.class);
  private final LongTermAssetLifecycleService service =
      new LongTermAssetLifecycleService(
          bonds,
          estates,
          cash,
          personal,
          history,
          java.time.Clock.fixed(
              java.time.Instant.parse("2026-09-07T00:00:00Z"), java.time.ZoneOffset.UTC));

  @Test
  void archiveDispatchesToEveryClosedAssetSubtype() {
    var date = LocalDate.of(2026, 9, 7);
    var bond = new BondEntity();
    var estate = new RealEstateEntity();
    var reserve = new CashReserveEntity();
    var personalAsset = new PersonalAssetEntity();
    when(bonds.findByIdAndPortfolioId(1L, 7L)).thenReturn(Optional.of(bond));
    when(estates.findByIdAndPortfolioId(2L, 7L)).thenReturn(Optional.of(estate));
    when(cash.findByIdAndPortfolioId(3L, 7L)).thenReturn(Optional.of(reserve));
    when(personal.findByIdAndPortfolioId(4L, 7L)).thenReturn(Optional.of(personalAsset));

    service.changeArchive(7L, 1L, date);
    service.changeArchive(7L, 2L, date);
    service.changeArchive(7L, 3L, date);
    service.changeArchive(7L, 4L, date);

    assertThat(bond.getArchivedAt()).isEqualTo(date);
    assertThat(estate.getArchivedAt()).isEqualTo(date);
    assertThat(reserve.getArchivedAt()).isEqualTo(date);
    assertThat(personalAsset.getArchivedAt()).isEqualTo(date);
    verify(bonds).save(bond);
    verify(estates).save(estate);
    verify(cash).save(reserve);
    verify(personal).save(personalAsset);
  }

  @Test
  void reactivateClearsArchiveDateAndMissingAssetFailsClosed() {
    var estate = new RealEstateEntity();
    estate.setArchivedAt(LocalDate.of(2026, 1, 1));
    when(estates.findByIdAndPortfolioId(2L, 7L)).thenReturn(Optional.of(estate));

    service.changeArchive(7L, 2L, null);

    assertThat(estate.getArchivedAt()).isNull();
    verify(history)
        .transition(2L, "REAL_ESTATE", LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 9, 7));
    assertThatThrownBy(() -> service.changeArchive(7L, 99L, LocalDate.now()))
        .isInstanceOf(AssetNotFoundException.class);
  }

  @Test
  void ambiguousIdentityFailsBeforeAnyMutation() {
    var bond = new BondEntity();
    var estate = new RealEstateEntity();
    when(bonds.findByIdAndPortfolioId(2L, 7L)).thenReturn(Optional.of(bond));
    when(estates.findByIdAndPortfolioId(2L, 7L)).thenReturn(Optional.of(estate));
    assertThatThrownBy(() -> service.changeArchive(7L, 2L, LocalDate.of(2026, 9, 7)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Ambiguous");
    assertThat(bond.getArchivedAt()).isNull();
    assertThat(estate.getArchivedAt()).isNull();
    org.mockito.Mockito.verifyNoInteractions(history);
    org.mockito.Mockito.verify(bonds, org.mockito.Mockito.never())
        .save(org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.verify(estates, org.mockito.Mockito.never())
        .save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void repeatedArchiveAndReactivateAreIdempotent() {
    var estate = new RealEstateEntity();
    when(estates.findByIdAndPortfolioId(2L, 7L)).thenReturn(Optional.of(estate));
    service.changeArchive(7L, 2L, null);
    estate.setArchivedAt(LocalDate.of(2026, 1, 1));
    service.changeArchive(7L, 2L, LocalDate.of(2026, 9, 7));
    assertThat(estate.getArchivedAt()).isEqualTo(LocalDate.of(2026, 1, 1));
    org.mockito.Mockito.verifyNoInteractions(history);
  }
}
