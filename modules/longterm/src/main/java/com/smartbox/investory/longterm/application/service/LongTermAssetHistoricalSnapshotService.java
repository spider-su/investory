package com.smartbox.investory.longterm.application.service;

import static com.smartbox.investory.longterm.application.service.LongTermAssetEconomics.*;

import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.PortfolioNotFoundException;
import com.smartbox.investory.longterm.infrastructure.lifecycle.LongTermAssetHistoryRepository;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateEntity;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Reconstructs historical annual rental facts from complete persisted lifecycle history. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class LongTermAssetHistoricalSnapshotService {
  private final RealEstateRepository realEstates;
  private final LongTermAssetRentalContractRepository contracts;
  private final LongTermAssetHistoryRepository lifecycle;
  private final CurrencyConversion conversion;
  private final PortfolioContextReader portfolios;

  public LongTermAssetHistoricalSnapshotService(
      RealEstateRepository realEstates,
      LongTermAssetRentalContractRepository contracts,
      LongTermAssetHistoryRepository lifecycle,
      CurrencyConversion conversion,
      PortfolioContextReader portfolios) {
    this.realEstates = realEstates;
    this.contracts = contracts;
    this.lifecycle = lifecycle;
    this.conversion = conversion;
    this.portfolios = portfolios;
  }

  public LongTermAssetAnnualSnapshotModel snapshot(Long portfolioId, int year) {
    CurrencyType currency = localCurrency(portfolioId);
    LocalDate yearStart = LocalDate.of(year, 1, 1);
    LocalDate yearEnd = LocalDate.of(year, 12, 31);
    var estates = realEstates.findAllByPortfolioIdOrderByName(portfolioId);
    var history = contracts(estates);
    var ids = estates.stream().map(RealEstateEntity::getId).toList();
    var complete = lifecycle.completeRealEstateIds(ids);
    var intervals = lifecycle.intervals(ids);
    BigDecimal rentalIncome = BigDecimal.ZERO;
    for (var estate : estates) {
      if (estate.getAcquisitionDate() != null && estate.getAcquisitionDate().isAfter(yearEnd))
        continue;
      if (!complete.contains(estate.getId()) || estate.getAcquisitionDate() == null)
        return unavailable();
      var assetIntervals =
          intervals.stream().filter(interval -> interval.assetId().equals(estate.getId())).toList();
      if (!consistentArchiveState(estate, assetIntervals)) return unavailable();
      LocalDate ownedFrom =
          estate.getAcquisitionDate().isAfter(yearStart) ? estate.getAcquisitionDate() : yearStart;
      var assetContracts =
          history.getOrDefault(estate.getId(), List.of()).stream()
              .sorted(
                  java.util.Comparator.comparing(LongTermAssetRentalContractEntity::getStartDate))
              .toList();
      rentalIncome =
          rentalIncome.add(
              rentalIncome(estate, assetContracts, assetIntervals, ownedFrom, yearEnd, currency));
    }
    // Current balances and rates are not historical facts without dated observations.
    return new LongTermAssetAnnualSnapshotModel(null, rentalIncome, null, null, null, null);
  }

  private BigDecimal rentalIncome(
      RealEstateEntity estate,
      List<LongTermAssetRentalContractEntity> contracts,
      List<LongTermAssetHistoryRepository.ArchiveInterval> intervals,
      LocalDate ownedFrom,
      LocalDate yearEnd,
      CurrencyType currency) {
    BigDecimal result = BigDecimal.ZERO;
    LocalDate previousEnd = null;
    boolean previous = false;
    for (var contract : contracts) {
      LocalDate end = RentalContractService.effectiveEnd(contract);
      if (previous && (previousEnd == null || !contract.getStartDate().isAfter(previousEnd)))
        throw new IllegalStateException("Overlapping rental contracts for asset " + estate.getId());
      previous = true;
      previousEnd = end;
      LocalDate from =
          contract.getStartDate().isAfter(ownedFrom) ? contract.getStartDate() : ownedFrom;
      LocalDate to = end == null || end.isAfter(yearEnd) ? yearEnd : end;
      if (from.isAfter(to)) continue;
      for (var period : activePeriods(from, to, intervals)) {
        BigDecimal net = BigDecimal.ZERO;
        for (var term : contract.getTerms()) {
          BigDecimal accrued =
              accruedAmount(term.getAmount(), term.getFrequency(), period.from(), period.to());
          if (isRentalIncome(term.getType())) net = net.add(accrued);
          if (isRentalExpense(term.getType()) && !term.isPaidByTenant())
            net = net.subtract(accrued);
        }
        BigDecimal tax =
            accruedAmount(
                    estate.getTaxBase() == null ? BigDecimal.ZERO : estate.getTaxBase(),
                    Frequency.ANNUAL,
                    period.from(),
                    period.to())
                .multiply(FinancialPolicyDefaults.RENTAL_TAX_RATE);
        result = result.add(toBase(net.subtract(tax), estate.getCurrency(), currency, yearEnd));
      }
    }
    return result;
  }

  private static List<DatePeriod> activePeriods(
      LocalDate from,
      LocalDate to,
      List<LongTermAssetHistoryRepository.ArchiveInterval> intervals) {
    var periods = new ArrayList<>(List.of(new DatePeriod(from, to)));
    for (var interval : intervals) {
      var remaining = new ArrayList<DatePeriod>();
      for (var period : periods) {
        if (interval.from().isAfter(period.to())
            || (interval.to() != null && !interval.to().isAfter(period.from()))) {
          remaining.add(period);
        } else {
          if (period.from().isBefore(interval.from()))
            remaining.add(new DatePeriod(period.from(), interval.from().minusDays(1)));
          if (interval.to() != null && !interval.to().isAfter(period.to()))
            remaining.add(new DatePeriod(interval.to(), period.to()));
        }
      }
      periods = remaining;
    }
    return periods;
  }

  private static boolean consistentArchiveState(
      RealEstateEntity estate, List<LongTermAssetHistoryRepository.ArchiveInterval> intervals) {
    var open = intervals.stream().filter(interval -> interval.to() == null).toList();
    return open.size() <= 1
        && (estate.getArchivedAt() == null) == open.isEmpty()
        && (open.isEmpty() || open.getFirst().from().equals(estate.getArchivedAt()));
  }

  private Map<Long, List<LongTermAssetRentalContractEntity>> contracts(
      List<RealEstateEntity> estates) {
    if (estates.isEmpty()) return Map.of();
    return contracts
        .findAllWithTermsByAssetIdIn(estates.stream().map(RealEstateEntity::getId).toList())
        .stream()
        .collect(Collectors.groupingBy(LongTermAssetRentalContractEntity::getAssetId));
  }

  private CurrencyType localCurrency(Long portfolioId) {
    return portfolios
        .findById(portfolioId)
        .map(PortfolioContext::localCurrency)
        .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
  }

  private BigDecimal toBase(
      BigDecimal value, CurrencyType source, CurrencyType target, LocalDate date) {
    return value == null || source == target
        ? value
        : conversion.convertToBaseCurrency(value, target, source, date);
  }

  private static LongTermAssetAnnualSnapshotModel unavailable() {
    return new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null);
  }

  private record DatePeriod(LocalDate from, LocalDate to) {}
}
