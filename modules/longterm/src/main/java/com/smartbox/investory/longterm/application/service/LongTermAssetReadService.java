package com.smartbox.investory.longterm.application.service;

import static com.smartbox.investory.longterm.application.service.LongTermAssetEconomics.*;

import com.smartbox.investory.longterm.api.*;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.infrastructure.bond.BondEntity;
import com.smartbox.investory.longterm.infrastructure.bond.BondRepository;
import com.smartbox.investory.longterm.infrastructure.cash.CashReserveEntity;
import com.smartbox.investory.longterm.infrastructure.cash.CashReserveRepository;
import com.smartbox.investory.longterm.infrastructure.personal.PersonalAssetEntity;
import com.smartbox.investory.longterm.infrastructure.personal.PersonalAssetRepository;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateEntity;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.shared.assets.AssetEconomicCategory;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Loads one portfolio read set and maps its monetary facts in one repeatable-read transaction. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class LongTermAssetReadService {
  private static final Logger log = LoggerFactory.getLogger(LongTermAssetReadService.class);
  private final BondRepository bondRepository;
  private final RealEstateRepository realEstateRepository;
  private final CashReserveRepository cashReserveRepository;
  private final PersonalAssetRepository personalAssetRepository;
  private final LongTermAssetRentalContractRepository contractRepository;
  private final CurrencyConversion conversion;
  private final PortfolioContextReader portfolios;

  public LongTermAssetReadService(
      BondRepository bondRepository,
      RealEstateRepository realEstateRepository,
      CashReserveRepository cashReserveRepository,
      PersonalAssetRepository personalAssetRepository,
      LongTermAssetRentalContractRepository contractRepository,
      CurrencyConversion conversion,
      PortfolioContextReader portfolios) {
    this.bondRepository = bondRepository;
    this.realEstateRepository = realEstateRepository;
    this.cashReserveRepository = cashReserveRepository;
    this.personalAssetRepository = personalAssetRepository;
    this.contractRepository = contractRepository;
    this.conversion = conversion;
    this.portfolios = portfolios;
  }

  public LongTermOverviewView overview(Long portfolioId, LocalDate date) {
    CurrencyType currency = localCurrency(portfolioId);
    List<AssetSummaryView> assets = summaries(portfolioId, date, currency, false);
    Map<LongTermAssetType, List<AssetSummaryView>> byType =
        assets.stream().collect(Collectors.groupingBy(AssetSummaryView::type));
    List<AssetGroupView> groups =
        List.of(
            group(LongTermAssetType.REAL_ESTATE, byType, currency),
            group(LongTermAssetType.BOND, byType, currency),
            group(LongTermAssetType.CASH_RESERVE, byType, currency),
            group(LongTermAssetType.PERSONAL_ASSET, byType, currency));
    BigDecimal total =
        assets.stream()
            .map(AssetSummaryView::currentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal personal = valueOf(byType.get(LongTermAssetType.PERSONAL_ASSET));
    BigDecimal investment = total.subtract(personal);
    List<AssetSummaryView> investmentAssets =
        assets.stream().filter(asset -> asset.type() != LongTermAssetType.PERSONAL_ASSET).toList();
    AnnualEconomicsView economics = aggregateEconomics(investmentAssets, currency);
    AssetGroupView largest =
        groups.stream()
            .max(java.util.Comparator.comparing(AssetGroupView::totalValue))
            .orElse(null);
    BigDecimal largestShare =
        largest == null || total.signum() == 0
            ? BigDecimal.ZERO
            : largest.totalValue().divide(total, 8, RoundingMode.HALF_UP);
    return new LongTermOverviewView(
        currency,
        total,
        investment,
        personal,
        economics,
        groups,
        largest == null ? null : largest.title(),
        largestShare);
  }

  public List<AssetSummaryView> archived(Long portfolioId, LocalDate date) {
    return summaries(portfolioId, date, localCurrency(portfolioId), true);
  }

  public AssetSummaryView realEstateSummary(Long portfolioId, Long id, LocalDate date) {
    CurrencyType currency = localCurrency(portfolioId);
    var estate =
        realEstateRepository
            .findByIdAndPortfolioId(id, portfolioId)
            .orElseThrow(() -> new ResourceNotFoundException("Real estate not found"));
    return realEstate(estate, currency, date, contracts(List.of(estate)));
  }

  public LongTermAssetProfileSnapshotModel snapshot(Long portfolioId, LocalDate date) {
    CurrencyType currency = localCurrency(portfolioId);
    var data = load(portfolioId, false, date);
    var rows = summaries(data, date, currency);
    var profileAssets =
        rows.stream()
            .map(
                row ->
                    new LongTermAssetProfileAssetModel(
                        category(row.type()),
                        currency,
                        row.currentValue(),
                        row.type() == LongTermAssetType.CASH_RESERVE
                            && (row.maturityDate() == null || !row.maturityDate().isAfter(date))))
            .toList();
    var projections =
        rows.stream()
            .filter(row -> row.type() != LongTermAssetType.PERSONAL_ASSET)
            .map(row -> projection(row, date, currency, data))
            .toList();
    return new LongTermAssetProfileSnapshotModel(
        new LongTermAssetProfileSummaryModel(
            currency, valueOf(rows), aggregateEconomics(rows, currency).netAnnualIncomeAfterTax()),
        profileAssets,
        projections,
        annual(rows, currency));
  }

  public BigDecimal currentWeightedEffectiveReturn(Long portfolioId, LocalDate date) {
    CurrencyType currency = localCurrency(portfolioId);
    var bonds =
        summaries(load(portfolioId, false, date), date, currency).stream()
            .filter(row -> row.type() == LongTermAssetType.BOND)
            .filter(row -> row.maturityDate() == null || date.isBefore(row.maturityDate()))
            .filter(row -> row.currentValue() != null && row.currentValue().signum() > 0)
            .filter(row -> row.currentAnnualRate() != null)
            .toList();
    BigDecimal capital =
        bonds.stream().map(AssetSummaryView::currentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (capital.signum() == 0) return null;
    return bonds.stream()
        .map(row -> row.currentValue().multiply(row.currentAnnualRate()))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(capital, 20, RoundingMode.HALF_UP);
  }

  private record ReadSet(
      List<BondEntity> bonds,
      List<RealEstateEntity> estates,
      List<CashReserveEntity> cash,
      List<PersonalAssetEntity> personal,
      Map<Long, List<LongTermAssetRentalContractEntity>> contracts) {}

  private ReadSet load(Long portfolioId, boolean archived, LocalDate date) {
    var bonds =
        archived
            ? bondRepository.findAllByPortfolioIdAndArchivedAtIsNotNullOrderByName(portfolioId)
            : bondRepository.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(portfolioId);
    var estates =
        archived
            ? realEstateRepository.findAllByPortfolioIdAndArchivedAtIsNotNullOrderByName(
                portfolioId)
            : realEstateRepository.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(portfolioId);
    var cash =
        archived
            ? cashReserveRepository.findAllByPortfolioIdAndArchivedAtIsNotNullOrderByName(
                portfolioId)
            : cashReserveRepository.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(portfolioId);
    var personal =
        archived
            ? personalAssetRepository.findAllByPortfolioIdAndArchivedAtIsNotNullOrderByName(
                portfolioId)
            : personalAssetRepository.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(
                portfolioId);
    bonds =
        bonds.stream().filter(row -> acquiredOnOrBefore(row.getAcquisitionDate(), date)).toList();
    estates =
        estates.stream().filter(row -> acquiredOnOrBefore(row.getAcquisitionDate(), date)).toList();
    cash = cash.stream().filter(row -> acquiredOnOrBefore(row.getAcquisitionDate(), date)).toList();
    personal =
        personal.stream()
            .filter(row -> acquiredOnOrBefore(row.getAcquisitionDate(), date))
            .toList();
    return new ReadSet(bonds, estates, cash, personal, contracts(estates));
  }

  private static boolean acquiredOnOrBefore(LocalDate acquisitionDate, LocalDate date) {
    return acquisitionDate == null || date == null || !acquisitionDate.isAfter(date);
  }

  private Map<Long, List<LongTermAssetRentalContractEntity>> contracts(
      List<RealEstateEntity> estates) {
    if (estates.isEmpty()) return Map.of();
    return contractRepository
        .findAllWithTermsByAssetIdIn(estates.stream().map(RealEstateEntity::getId).toList())
        .stream()
        .collect(Collectors.groupingBy(LongTermAssetRentalContractEntity::getAssetId));
  }

  private List<AssetSummaryView> summaries(
      Long portfolioId, LocalDate date, CurrencyType currency, boolean archived) {
    return summaries(load(portfolioId, archived, date), date, currency);
  }

  private List<AssetSummaryView> summaries(ReadSet data, LocalDate date, CurrencyType currency) {
    List<AssetSummaryView> rows = new ArrayList<>();
    data.bonds().forEach(row -> rows.add(bond(row, currency, date)));
    data.estates().forEach(row -> rows.add(realEstate(row, currency, date, data.contracts())));
    data.cash().forEach(row -> rows.add(cash(row, currency, date)));
    data.personal().forEach(row -> rows.add(personal(row, currency, date)));
    return List.copyOf(rows);
  }

  private AssetSummaryView bond(BondEntity row, CurrencyType currency, LocalDate date) {
    BigDecimal value = toBase(row.getValue(), row.getCurrency(), currency, date);
    BigDecimal gross = interestIncome(value, row.getInterestRate(), row.getMaturityDate(), date);
    return new AssetSummaryView(
        row.getId(),
        row.getName(),
        LongTermAssetType.BOND,
        currency,
        value,
        row.getMaturityDate(),
        row.getInterestRate(),
        LongTermAssetEconomics.economics(gross, BigDecimal.ZERO, value),
        BigDecimal.ZERO,
        null);
  }

  private AssetSummaryView realEstate(
      RealEstateEntity row,
      CurrencyType currency,
      LocalDate date,
      Map<Long, List<LongTermAssetRentalContractEntity>> contracts) {
    BigDecimal value = toBase(row.getValue(), row.getCurrency(), currency, date);
    var active =
        contracts.getOrDefault(row.getId(), List.of()).stream()
            .filter(contract -> RentalContractService.applies(contract, date))
            .toList();
    if (active.size() > 1) {
      log.error(
          "LONG_TERM_RENTAL_INTEGRITY_VIOLATION overlapping contracts for asset {} on {}",
          row.getId(),
          date);
      return new AssetSummaryView(
          row.getId(),
          row.getName(),
          LongTermAssetType.REAL_ESTATE,
          currency,
          value,
          null,
          null,
          null,
          BigDecimal.ZERO,
          null,
          true);
    }
    var contract = active.isEmpty() ? null : active.getFirst();
    var terms =
        contract == null
            ? List.<RentalContractModel.Term>of()
            : convertedTerms(contract, row.getCurrency(), currency, date);
    BigDecimal annualTaxBase =
        toBase(
            row.getTaxBase() == null ? BigDecimal.ZERO : row.getTaxBase(),
            row.getCurrency(),
            currency,
            date);
    var rental = rental(terms, annualTaxBase, value);
    return new AssetSummaryView(
        row.getId(),
        row.getName(),
        LongTermAssetType.REAL_ESTATE,
        currency,
        value,
        null,
        null,
        rental.economics(),
        rental.monthlyPayment(),
        contract == null ? null : RentalContractService.effectiveEnd(contract),
        false);
  }

  private AssetSummaryView cash(CashReserveEntity row, CurrencyType currency, LocalDate date) {
    BigDecimal value = toBase(row.getValue(), row.getCurrency(), currency, date);
    BigDecimal interestRate =
        row.getInterestRate() == null ? BigDecimal.ZERO : row.getInterestRate();
    BigDecimal gross = interestIncome(value, interestRate, row.getMaturityDate(), date);
    return new AssetSummaryView(
        row.getId(),
        row.getName(),
        LongTermAssetType.CASH_RESERVE,
        currency,
        value,
        row.getMaturityDate(),
        interestRate,
        LongTermAssetEconomics.economics(gross, BigDecimal.ZERO, value),
        BigDecimal.ZERO,
        null);
  }

  private AssetSummaryView personal(
      PersonalAssetEntity row, CurrencyType currency, LocalDate date) {
    BigDecimal value = toBase(row.getValue(), row.getCurrency(), currency, date);
    return new AssetSummaryView(
        row.getId(),
        row.getName(),
        LongTermAssetType.PERSONAL_ASSET,
        currency,
        value,
        null,
        null,
        LongTermAssetEconomics.economics(BigDecimal.ZERO, BigDecimal.ZERO, value),
        BigDecimal.ZERO,
        null);
  }

  private AssetGroupView group(
      LongTermAssetType type,
      Map<LongTermAssetType, List<AssetSummaryView>> byType,
      CurrencyType currency) {
    List<AssetSummaryView> rows = byType.getOrDefault(type, List.of());
    return new AssetGroupView(
        type.name(),
        type.name(),
        currency,
        rows,
        valueOf(rows),
        aggregateEconomics(rows, currency));
  }

  private AnnualEconomicsView aggregateEconomics(
      List<AssetSummaryView> rows, CurrencyType currency) {
    BigDecimal gross =
        rows.stream()
            .filter(row -> !row.integrityWarning())
            .map(row -> row.annualEconomics().grossAnnualIncome())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal expenses =
        rows.stream()
            .filter(row -> !row.integrityWarning())
            .map(row -> row.annualEconomics().annualExpenses())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal tax =
        rows.stream()
            .filter(row -> !row.integrityWarning())
            .map(row -> row.annualEconomics().annualTax())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal monthlyTaxBase =
        rows.stream()
            .filter(row -> !row.integrityWarning())
            .map(row -> row.annualEconomics().monthlyTaxBase())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return LongTermAssetEconomics.economics(gross, expenses, valueOf(rows), tax, monthlyTaxBase);
  }

  private LongTermAssetProjectionModel projection(
      AssetSummaryView row, LocalDate date, CurrencyType currency, ReadSet data) {
    List<LongTermAssetProjectionModel.Period> periods = new ArrayList<>();
    if ((row.type() == LongTermAssetType.BOND || row.type() == LongTermAssetType.CASH_RESERVE)
        && (row.maturityDate() == null || row.maturityDate().isAfter(date)))
      periods.add(
          new LongTermAssetProjectionModel.Period(
              date,
              row.maturityDate() == null ? null : row.maturityDate().minusDays(1),
              row.annualEconomics().grossAnnualIncome(),
              BigDecimal.ZERO,
              null,
              null,
              false));
    List<RentalContractProjectionModel> rentalContracts =
        row.type() == LongTermAssetType.REAL_ESTATE
            ? data.contracts().getOrDefault(row.id(), List.of()).stream()
                .map(
                    contract ->
                        rentalContractProjection(
                            contract,
                            data.estates().stream()
                                .filter(estate -> estate.getId().equals(row.id()))
                                .findFirst()
                                .orElseThrow()
                                .getCurrency(),
                            currency,
                            date))
                .toList()
            : List.of();
    return new LongTermAssetProjectionModel(
        row.id(),
        row.name(),
        category(row.type()),
        currency,
        row.currentValue(),
        periods,
        rentalContracts,
        row.maturityDate(),
        row.type() == LongTermAssetType.CASH_RESERVE
            && (row.maturityDate() == null || !row.maturityDate().isAfter(date)));
  }

  private RentalContractProjectionModel rentalContractProjection(
      LongTermAssetRentalContractEntity contract,
      CurrencyType source,
      CurrencyType target,
      LocalDate date) {
    return new RentalContractProjectionModel(
        contract.getId(),
        contract.getStartDate(),
        contract.getEndDate(),
        contract.getTerminatedDate(),
        convertedTerms(contract, source, target, date));
  }

  private List<RentalContractModel.Term> convertedTerms(
      LongTermAssetRentalContractEntity contract,
      CurrencyType source,
      CurrencyType target,
      LocalDate date) {
    return contract.getTerms().stream()
        .map(
            term ->
                new RentalContractModel.Term(
                    term.getType(),
                    toBase(term.getAmount(), source, target, date),
                    term.getFrequency(),
                    term.isPaidByTenant()))
        .toList();
  }

  private LongTermAssetAnnualSnapshotModel annual(
      List<AssetSummaryView> rows, CurrencyType currency) {
    return new LongTermAssetAnnualSnapshotModel(
        sumType(rows, LongTermAssetType.REAL_ESTATE),
        incomeType(rows, LongTermAssetType.REAL_ESTATE),
        sumType(rows, LongTermAssetType.BOND),
        incomeType(rows, LongTermAssetType.BOND),
        sumType(rows, LongTermAssetType.CASH_RESERVE),
        sumType(rows, LongTermAssetType.PERSONAL_ASSET),
        currency);
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

  private static BigDecimal valueOf(List<AssetSummaryView> rows) {
    return rows == null
        ? BigDecimal.ZERO
        : rows.stream()
            .map(AssetSummaryView::currentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static BigDecimal sumType(List<AssetSummaryView> rows, LongTermAssetType type) {
    return valueOf(rows.stream().filter(row -> row.type() == type).toList());
  }

  private static AssetEconomicCategory category(LongTermAssetType type) {
    return switch (type) {
      case REAL_ESTATE -> AssetEconomicCategory.REAL_ESTATE;
      case BOND -> AssetEconomicCategory.FIXED_INCOME;
      case CASH_RESERVE -> AssetEconomicCategory.LIQUID_CASH;
      case PERSONAL_ASSET -> AssetEconomicCategory.PERSONAL_ASSET;
    };
  }

  private static BigDecimal incomeType(List<AssetSummaryView> rows, LongTermAssetType type) {
    return rows.stream()
        .filter(row -> row.type() == type && !row.integrityWarning())
        .map(row -> row.annualEconomics().netAnnualIncomeAfterTax())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
