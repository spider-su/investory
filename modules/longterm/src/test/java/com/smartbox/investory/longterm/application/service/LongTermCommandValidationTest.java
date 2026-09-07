package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.cash.*;
import com.smartbox.investory.longterm.infrastructure.personal.*;
import com.smartbox.investory.longterm.infrastructure.realestate.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LongTermCommandValidationTest {
  private final PortfolioContextReader portfolios = mock(PortfolioContextReader.class);
  private final BondRepository bondRows = mock(BondRepository.class);
  private final CashReserveRepository cashRows = mock(CashReserveRepository.class);
  private final PersonalAssetRepository personalRows = mock(PersonalAssetRepository.class);
  private final RealEstateRepository estateRows = mock(RealEstateRepository.class);
  private final BondCommandService bonds = new BondCommandService(bondRows, portfolios);
  private final CashReserveCommandService cash =
      new CashReserveCommandService(cashRows, portfolios);
  private final PersonalAssetCommandService personal =
      new PersonalAssetCommandService(personalRows, portfolios);
  private final RealEstateCommandService estates =
      new RealEstateCommandService(
          estateRows,
          portfolios,
          mock(
              com.smartbox.investory.longterm.infrastructure.lifecycle
                  .LongTermAssetHistoryRepository.class));

  @BeforeEach
  void portfolioExists() {
    when(portfolios.findById(1L))
        .thenReturn(Optional.of(new PortfolioContext(1L, CurrencyType.PLN)));
  }

  @Test
  void createCannotOverwriteAndUpdateCannotInsertForAnySubtype() {
    List<Runnable> invalid =
        List.of(
            () -> bonds.create(bond(1L, 9L, CurrencyType.PLN)),
            () -> bonds.update(bond(1L, null, CurrencyType.PLN)),
            () -> cash.create(cash(1L, 9L)),
            () -> cash.update(cash(1L, null)),
            () -> personal.create(personal(1L, 9L)),
            () -> personal.update(personal(1L, null)),
            () -> estates.create(estate(1L, 9L, BigDecimal.ONE)),
            () -> estates.update(estate(1L, null, BigDecimal.ONE)));
    invalid.forEach(
        action -> assertThatThrownBy(action::run).isInstanceOf(IllegalArgumentException.class));
    verifyNoInteractions(bondRows, cashRows, personalRows, estateRows, portfolios);
  }

  @Test
  void missingPortfolioFailsBeforeAnyAssetWrite() {
    List<Runnable> invalid =
        List.of(
            () -> bonds.create(bond(2L, null, CurrencyType.PLN)),
            () -> cash.create(cash(2L, null)),
            () -> personal.create(personal(2L, null)),
            () -> estates.create(estate(2L, null, BigDecimal.ONE)));
    invalid.forEach(
        action -> assertThatThrownBy(action::run).isInstanceOf(PortfolioNotFoundException.class));
    verifyNoInteractions(bondRows, cashRows, personalRows, estateRows);
  }

  @Test
  void missingOrForeignAssetCannotBecomeAnInsert() {
    List<Runnable> invalid =
        List.of(
            () -> bonds.update(bond(1L, 9L, CurrencyType.PLN)),
            () -> cash.update(cash(1L, 9L)),
            () -> personal.update(personal(1L, 9L)),
            () -> estates.update(estate(1L, 9L, BigDecimal.ONE)));
    invalid.forEach(
        action -> assertThatThrownBy(action::run).isInstanceOf(AssetNotFoundException.class));
    verify(bondRows, never()).save(any());
    verify(cashRows, never()).save(any());
    verify(personalRows, never()).save(any());
    verify(estateRows, never()).save(any());
  }

  @Test
  void changingCurrencyDoesNotMutateTheExistingBond() {
    var existing = new BondEntity();
    existing.setId(9L);
    existing.setCurrency(CurrencyType.PLN);
    existing.setName("Original");
    when(bondRows.findByIdAndPortfolioId(9L, 1L)).thenReturn(Optional.of(existing));
    assertThatThrownBy(() -> bonds.update(bond(1L, 9L, CurrencyType.EUR)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("currency");
    assertThat(existing.getName()).isEqualTo("Original");
    assertThat(existing.getCurrency()).isEqualTo(CurrencyType.PLN);
    verify(bondRows, never()).save(any());
  }

  @Test
  void negativeAnnualTaxBaseFailsBeforeLoadingOrMutatingEstate() {
    assertThatThrownBy(() -> estates.update(estate(1L, 9L, BigDecimal.ONE.negate())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tax base");
    verifyNoInteractions(estateRows);
  }

  @Test
  void invalidAmountsNamesAndMaturityFailBeforePersistence() {
    var today = LocalDate.of(2026, 1, 1);
    List<Runnable> invalid =
        List.of(
            () ->
                bonds.create(
                    new BondCommand(
                        1L,
                        null,
                        "Bond",
                        CurrencyType.PLN,
                        BigDecimal.ONE,
                        today,
                        BigDecimal.ZERO,
                        today.minusDays(1),
                        null)),
            () ->
                cash.create(
                    new CashReserveCommand(
                        1L,
                        null,
                        "Cash",
                        CurrencyType.PLN,
                        BigDecimal.ONE,
                        today,
                        BigDecimal.ZERO,
                        today.minusDays(1),
                        null)),
            () ->
                personal.create(
                    new PersonalAssetCommand(
                        1L,
                        null,
                        " ",
                        PersonalAssetCategory.OTHER,
                        CurrencyType.PLN,
                        BigDecimal.ONE,
                        null,
                        null)),
            () ->
                personal.create(
                    new PersonalAssetCommand(
                        1L,
                        null,
                        "Car",
                        PersonalAssetCategory.VEHICLE,
                        CurrencyType.PLN,
                        BigDecimal.ONE.negate(),
                        null,
                        null)),
            () ->
                bonds.create(
                    new BondCommand(
                        1L,
                        null,
                        "Bond",
                        CurrencyType.PLN,
                        BigDecimal.ONE,
                        null,
                        BigDecimal.ONE.negate(),
                        today,
                        null)),
            () ->
                estates.create(
                    new RealEstateCommand(
                        1L,
                        null,
                        "X".repeat(256),
                        CurrencyType.PLN,
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        null)));
    invalid.forEach(
        action -> assertThatThrownBy(action::run).isInstanceOf(IllegalArgumentException.class));
    verifyNoInteractions(bondRows, cashRows, personalRows, estateRows);
  }

  private static BondCommand bond(Long portfolio, Long id, CurrencyType currency) {
    return new BondCommand(
        portfolio,
        id,
        "Bond",
        currency,
        BigDecimal.TEN,
        null,
        BigDecimal.ZERO,
        LocalDate.of(2030, 1, 1),
        null);
  }

  private static CashReserveCommand cash(Long portfolio, Long id) {
    return new CashReserveCommand(
        portfolio, id, "Cash", CurrencyType.PLN, BigDecimal.TEN, null, BigDecimal.ZERO, null, null);
  }

  private static PersonalAssetCommand personal(Long portfolio, Long id) {
    return new PersonalAssetCommand(
        portfolio,
        id,
        "Car",
        PersonalAssetCategory.VEHICLE,
        CurrencyType.PLN,
        BigDecimal.TEN,
        null,
        null);
  }

  private static RealEstateCommand estate(Long portfolio, Long id, BigDecimal taxBase) {
    return new RealEstateCommand(
        portfolio, id, "Home", CurrencyType.PLN, BigDecimal.TEN, taxBase, null, null, null);
  }
}
