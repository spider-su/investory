package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.infrastructure.lifecycle.LongTermAssetHistoryRepository;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateEntity;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractTermEntity;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LongTermAssetHistoricalSnapshotServiceTest {
  private static final Long PORTFOLIO_ID = 1L;
  private static final int YEAR = 2025;

  private final RealEstateRepository estates = mock(RealEstateRepository.class);
  private final LongTermAssetRentalContractRepository contracts =
      mock(LongTermAssetRentalContractRepository.class);
  private final LongTermAssetHistoryRepository lifecycle =
      mock(LongTermAssetHistoryRepository.class);
  private final CurrencyConversion conversion = mock(CurrencyConversion.class);
  private final PortfolioContextReader portfolios = mock(PortfolioContextReader.class);
  private final LongTermAssetHistoricalSnapshotService service =
      new LongTermAssetHistoricalSnapshotService(
          estates, contracts, lifecycle, conversion, portfolios);

  @BeforeEach
  void setUp() {
    when(portfolios.findById(PORTFOLIO_ID))
        .thenReturn(
            Optional.of(new PortfolioContext(PORTFOLIO_ID, CurrencyType.USD, CurrencyType.USD)));
  }

  @Test
  void returnsUnavailableWithoutCompleteLifecycleProvenance() {
    var estate = estate(1L, LocalDate.of(2024, 1, 1));
    prepare(List.of(estate), List.of(), Set.of(), List.of());

    assertThat(service.snapshot(PORTFOLIO_ID, YEAR).rentalIncome()).isNull();
  }

  @Test
  void requiresArchiveIntervalsToMatchCurrentArchiveState() {
    var estate = estate(2L, LocalDate.of(2024, 1, 1));
    estate.setArchivedAt(LocalDate.of(2025, 7, 1));
    prepare(List.of(estate), List.of(), Set.of(estate.getId()), List.of());

    assertThat(service.snapshot(PORTFOLIO_ID, YEAR).rentalIncome()).isNull();
  }

  @Test
  void subtractsClosedArchiveIntervalsAndIgnoresTenantPaidExpenses() {
    var estate = estate(3L, LocalDate.of(2024, 1, 1));
    var contract =
        contract(31L, estate.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    contract.setTerms(
        List.of(
            term(CashFlowType.RENT, "365", false),
            term(CashFlowType.INSURANCE, "36.5", false),
            term(CashFlowType.UTILITIES, "100", true)));
    prepare(
        List.of(estate),
        List.of(contract),
        Set.of(estate.getId()),
        List.of(
            new LongTermAssetHistoryRepository.ArchiveInterval(
                estate.getId(), LocalDate.of(2025, 3, 1), LocalDate.of(2025, 4, 1))));

    assertThat(service.snapshot(PORTFOLIO_ID, YEAR).rentalIncome()).isEqualByComparingTo("300.6");
  }

  @Test
  void rejectsOverlappingHistoricalContracts() {
    var estate = estate(4L, LocalDate.of(2024, 1, 1));
    var first = contract(41L, estate.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
    var second =
        contract(42L, estate.getId(), LocalDate.of(2025, 6, 30), LocalDate.of(2025, 12, 31));
    prepare(List.of(estate), List.of(first, second), Set.of(estate.getId()), List.of());

    assertThatThrownBy(() -> service.snapshot(PORTFOLIO_ID, YEAR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Overlapping rental contracts");
  }

  @Test
  void ignoresEstateAcquiredAfterRequestedYear() {
    var estate = estate(5L, LocalDate.of(2026, 1, 1));
    prepare(List.of(estate), List.of(), Set.of(), List.of());

    assertThat(service.snapshot(PORTFOLIO_ID, YEAR).rentalIncome()).isEqualByComparingTo("0");
  }

  @Test
  void convertsHistoricalNetRentalIncomeUsingYearEndRate() {
    var estate = estate(6L, LocalDate.of(2024, 1, 1));
    estate.setCurrency(CurrencyType.EUR);
    estate.setTaxBase(new BigDecimal("1000"));
    var contract =
        contract(61L, estate.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    contract.setTerms(
        List.of(
            term(CashFlowType.RENT, "12000", false),
            term(CashFlowType.INSURANCE, "1200", false),
            term(CashFlowType.UTILITIES, "500", true)));
    prepare(List.of(estate), List.of(contract), Set.of(estate.getId()), List.of());
    when(conversion.convertToBaseCurrency(
            any(BigDecimal.class),
            eq(CurrencyType.USD),
            eq(CurrencyType.EUR),
            eq(LocalDate.of(2025, 12, 31))))
        .thenAnswer(invocation -> invocation.<BigDecimal>getArgument(0).multiply(BigDecimal.TWO));

    assertThat(service.snapshot(PORTFOLIO_ID, YEAR).rentalIncome()).isEqualByComparingTo("21430");
    var convertedValue = org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
    verify(conversion)
        .convertToBaseCurrency(
            convertedValue.capture(),
            eq(CurrencyType.USD),
            eq(CurrencyType.EUR),
            eq(LocalDate.of(2025, 12, 31)));
    assertThat(convertedValue.getValue()).isEqualByComparingTo("10715");
  }

  @Test
  void stopsHistoricalAccrualOnActualTerminationDate() {
    var estate = estate(7L, LocalDate.of(2024, 1, 1));
    var contract =
        contract(71L, estate.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    contract.setTerminatedDate(LocalDate.of(2025, 3, 31));
    contract.setTerms(List.of(term(CashFlowType.RENT, "365", false)));
    prepare(List.of(estate), List.of(contract), Set.of(estate.getId()), List.of());

    assertThat(service.snapshot(PORTFOLIO_ID, YEAR).rentalIncome()).isEqualByComparingTo("90");
  }

  @Test
  void archiveIntervalExcludesArchiveDayAndIncludesReactivationDay() {
    var estate = estate(8L, LocalDate.of(2024, 1, 1));
    var contract =
        contract(81L, estate.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    contract.setTerms(List.of(term(CashFlowType.RENT, "365", false)));
    prepare(
        List.of(estate),
        List.of(contract),
        Set.of(estate.getId()),
        List.of(
            new LongTermAssetHistoryRepository.ArchiveInterval(
                estate.getId(), LocalDate.of(2025, 3, 1), LocalDate.of(2025, 4, 1))));

    assertThat(service.snapshot(PORTFOLIO_ID, YEAR).rentalIncome()).isEqualByComparingTo("334");
  }

  private void prepare(
      List<RealEstateEntity> estateRows,
      List<LongTermAssetRentalContractEntity> contractRows,
      Set<Long> completeIds,
      List<LongTermAssetHistoryRepository.ArchiveInterval> archiveIntervals) {
    var ids = estateRows.stream().map(RealEstateEntity::getId).toList();
    when(estates.findAllByPortfolioIdOrderByName(PORTFOLIO_ID)).thenReturn(estateRows);
    when(contracts.findAllWithTermsByAssetIdIn(ids)).thenReturn(contractRows);
    when(lifecycle.completeRealEstateIds(ids)).thenReturn(completeIds);
    when(lifecycle.intervals(ids)).thenReturn(archiveIntervals);
  }

  private static RealEstateEntity estate(Long id, LocalDate acquisitionDate) {
    var estate = new RealEstateEntity();
    estate.setId(id);
    estate.setPortfolioId(PORTFOLIO_ID);
    estate.setName("Historical estate " + id);
    estate.setCurrency(CurrencyType.USD);
    estate.setValue(new BigDecimal("100000"));
    estate.setTaxBase(BigDecimal.ZERO);
    estate.setAcquisitionDate(acquisitionDate);
    return estate;
  }

  private static LongTermAssetRentalContractEntity contract(
      Long id, Long assetId, LocalDate start, LocalDate end) {
    var contract = new LongTermAssetRentalContractEntity();
    contract.setId(id);
    contract.setAssetId(assetId);
    contract.setStartDate(start);
    contract.setEndDate(end);
    return contract;
  }

  private static LongTermAssetRentalContractTermEntity term(
      CashFlowType type, String amount, boolean paidByTenant) {
    var term = new LongTermAssetRentalContractTermEntity();
    term.setType(type);
    term.setAmount(new BigDecimal(amount));
    term.setFrequency(Frequency.ANNUAL);
    term.setPaidByTenant(paidByTenant);
    return term;
  }
}
