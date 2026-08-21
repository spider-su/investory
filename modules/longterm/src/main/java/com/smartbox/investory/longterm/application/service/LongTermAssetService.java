package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.BondPlanningSummary;
import com.smartbox.investory.longterm.application.model.LongTermAssetProjectionInput;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.application.model.RealEstateGroupPlanningSummary;
import com.smartbox.investory.longterm.application.model.RealEstatePlanningSummary;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LongTermAssetService {
  public static final BigDecimal REAL_ESTATE_TAX_RATE = new BigDecimal("0.085");
  public static final BigDecimal BOND_TAX_RATE = new BigDecimal("0.19");
  private final LongTermAssetRepository assets;
  private final LongTermAssetCashFlowRepository cashFlows;
  private final LongTermAssetValuationPeriodRepository valuations;
  private final LongTermAssetBondRatePeriodRepository bondRates;
  private final LongTermAssetBondDetailsRepository bonds;
  private final LongTermAssetDepositDetailsRepository deposits;
  private final RentalTaxPolicyRepository taxPolicies;
  private final PortfolioContextReader portfolioContextReader;
  private final CurrencyConversion currencyRates;
  private final LongTermAssetLifecycleService lifecycle;
  private final LongTermAssetCashFlowService cashFlowService;
  private final Clock applicationClock;

  /** Optional for old unit fixtures; production uses normalized contracts first. */
  @Autowired private LongTermAssetRentalContractRepository rentalContracts;

  @Value("${app.long-term-assets.planning-currency:PLN}")
  private CurrencyType planningCurrency = CurrencyType.PLN;

  @Value("${app.history-start:2025-01-01}")
  private LocalDate historyStart = LocalDate.of(2025, 1, 1);

  private final RealEstatePlanningCalculator realEstatePlanningCalculator =
      new RealEstatePlanningCalculator();
  private final BondPlanningCalculator bondPlanningCalculator = new BondPlanningCalculator();

  public LongTermAssetService(
      LongTermAssetRepository assets,
      LongTermAssetCashFlowRepository cashFlows,
      LongTermAssetValuationPeriodRepository valuations,
      LongTermAssetBondRatePeriodRepository bondRates,
      LongTermAssetBondDetailsRepository bonds,
      LongTermAssetDepositDetailsRepository deposits,
      RentalTaxPolicyRepository taxPolicies,
      PortfolioContextReader portfolioContextReader,
      CurrencyConversion currencyRates,
      LongTermAssetLifecycleService lifecycle,
      LongTermAssetCashFlowService cashFlowService,
      Clock applicationClock) {
    this.assets = assets;
    this.cashFlows = cashFlows;
    this.valuations = valuations;
    this.bondRates = bondRates;
    this.bonds = bonds;
    this.deposits = deposits;
    this.taxPolicies = taxPolicies;
    this.portfolioContextReader = portfolioContextReader;
    this.currencyRates = currencyRates;
    this.lifecycle = lifecycle;
    this.cashFlowService = cashFlowService;
    this.applicationClock = applicationClock;
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> list(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    return assets.findAllByPortfolioIdOrderByName(portfolioId).stream()
        .filter(LongTermAssetEntity::isActive)
        .map(a -> summary(a, effectiveDate))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AssetGroupSummary> grouped(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    List<LongTermAssetSummary> rows = list(portfolioId, effectiveDate);
    return groupSummaries(rows, planningCurrency, effectiveDate);
  }

  public List<AssetGroupSummary> groupSummaries(
      List<LongTermAssetSummary> rows, CurrencyType base, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    return List.of(
        group(
            "REAL_ESTATE",
            "Real estate",
            rows.stream()
                .filter(r -> r.type() == LongTermAssetType.REAL_ESTATE)
                .sorted(
                    Comparator.comparing(LongTermAssetSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList(),
            base,
            effectiveDate),
        group(
            "BOND",
            "Bonds",
            rows.stream()
                .filter(r -> r.type() == LongTermAssetType.BOND)
                .sorted(
                    Comparator.comparing(
                            LongTermAssetSummary::maturityDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LongTermAssetSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList(),
            base,
            effectiveDate),
        group(
            "CASH_RESERVE",
            "Cash reserves",
            rows.stream()
                .filter(r -> r.type() == LongTermAssetType.CASH_RESERVE)
                .sorted(
                    Comparator.comparing(LongTermAssetSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList(),
            base,
            effectiveDate),
        group(
            "OTHER",
            "Other assets",
            rows.stream()
                .filter(
                    r ->
                        r.type() == LongTermAssetType.DEPOSIT
                            || r.type() == LongTermAssetType.OTHER)
                .sorted(
                    Comparator.comparing(LongTermAssetSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList(),
            base,
            effectiveDate));
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetEntity> get(Long portfolioId, Long id) {
    return assets.findByIdAndPortfolioId(id, portfolioId);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetCashFlowEntity> cashFlows(Long portfolioId, Long id) {
    owned(portfolioId, id);
    return cashFlows.findAllByAssetIdOrderByValidFrom(id).stream()
        .sorted(
            Comparator.comparingInt(
                    (LongTermAssetCashFlowEntity flow) -> cashFlowOrder(flow.getType()))
                .thenComparing(LongTermAssetCashFlowEntity::getValidFrom)
                .thenComparing(flow -> Optional.ofNullable(flow.getId()).orElse(Long.MAX_VALUE)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetCashFlowEntity> currentCashFlows(
      Long portfolioId, Long id, LocalDate date) {
    return cashFlows(portfolioId, id).stream()
        .filter(flow -> LongTermAssetCalculator.applies(flow, effectiveDate(date)))
        .toList();
  }

  @Transactional(readOnly = true)
  public RentalPeriod rentalPeriod(Long portfolioId, Long id, LocalDate date) {
    List<LongTermAssetCashFlowEntity> current = currentCashFlows(portfolioId, id, date);
    return current.stream()
        .min(Comparator.comparing(LongTermAssetCashFlowEntity::getValidFrom))
        .map(
            flow ->
                new RentalPeriod(
                    flow.getValidFrom(),
                    current.stream()
                        .map(LongTermAssetCashFlowEntity::getValidTo)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null)))
        .orElse(new RentalPeriod(null, null));
  }

  @Transactional(readOnly = true)
  public List<CashFlowType> availableCashFlowTypes(Long portfolioId, Long id, LocalDate date) {
    Set<CashFlowType> currentTypes =
        currentCashFlows(portfolioId, id, date).stream()
            .map(LongTermAssetCashFlowEntity::getType)
            .collect(java.util.stream.Collectors.toSet());
    return Arrays.stream(CashFlowType.values())
        .filter(type -> !currentTypes.contains(type))
        .sorted(Comparator.comparingInt(LongTermAssetService::cashFlowOrder))
        .toList();
  }

  private static int cashFlowOrder(CashFlowType type) {
    return switch (type) {
      case RENT -> 0;
      case PARKING_RENT -> 1;
      case ADMIN_FEE -> 2;
      case UTILITIES -> 3;
      case PROPERTY_TAX -> 4;
      case OTHER_INCOME, INSURANCE, OTHER_EXPENSE -> 5;
    };
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetBondDetailsEntity> bondDetails(Long portfolioId, Long id) {
    owned(portfolioId, id);
    return bonds.findById(id);
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetDepositDetailsEntity> depositDetails(Long portfolioId, Long id) {
    owned(portfolioId, id);
    return deposits.findById(id);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetValuationPeriodEntity> valuationPeriods(Long portfolioId, Long id) {
    owned(portfolioId, id);
    return valuations.findAllByAssetIdOrderByValidFrom(id);
  }

  /** Returns the effective property-growth assumption for presentation and simulation inputs. */
  public BigDecimal expectedPropertyGrowth(Long portfolioId, Long id, LocalDate date) {
    return valuationPeriods(portfolioId, id).stream()
        .filter(
            period ->
                !period.getValidFrom().isAfter(date)
                    && (period.getValidTo() == null || !period.getValidTo().isBefore(date)))
        .max(java.util.Comparator.comparing(LongTermAssetValuationPeriodEntity::getValidFrom))
        .map(LongTermAssetValuationPeriodEntity::getExpectedAnnualGrowthRate)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetBondRatePeriodEntity> bondRatePeriods(Long portfolioId, Long id) {
    owned(portfolioId, id);
    return bondRates.findAllByAssetIdOrderByValidFrom(id);
  }

  @Transactional(readOnly = true)
  public Optional<RentalTaxPolicyEntity> rentalTaxPolicy(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    return taxPolicies
        .findFirstByPortfolioIdAndValidFromLessThanEqualAndValidToGreaterThanEqualOrderByValidFromDesc(
            portfolioId, effectiveDate, effectiveDate)
        .or(
            () ->
                taxPolicies
                    .findFirstByPortfolioIdAndValidFromLessThanEqualAndValidToIsNullOrderByValidFromDesc(
                        portfolioId, effectiveDate));
  }

  /** Builds native-domain projection data; the public reader normalizes its money to USD. */
  @Transactional(readOnly = true)
  public List<LongTermAssetProjectionInput> projectionInputs(
      Long portfolioId, LocalDate policyDate) {
    return projectionInputsAt(portfolioId, policyDate, true);
  }

  private List<LongTermAssetProjectionInput> projectionInputsAt(
      Long portfolioId, LocalDate policyDate, boolean activeOnly) {
    LocalDate effectivePolicyDate = effectiveDate(policyDate);
    List<LongTermAssetProjectionInput> result = new ArrayList<>();
    for (LongTermAssetEntity asset : assets.findAllByPortfolioIdOrderByName(portfolioId)) {
      if (activeOnly && !asset.isActive()) continue;
      if (!activeOnly && !activeOn(asset, effectivePolicyDate)) continue;
      List<LongTermAssetProjectionInput.Period> periods = new ArrayList<>();
      List<RentalContractModel> contracts =
          rentalContracts == null
              ? List.of()
              : rentalContracts.findAllByAssetIdOrderByStartDate(asset.getId()).stream()
                  .map(
                      c ->
                          new RentalContractModel(
                              c.getId(),
                              c.getStartDate(),
                              c.getEndDate(),
                              c.getTerminatedDate(),
                              c.getRentalTaxPaidByTenant(),
                              c.getTerms().stream()
                                  .map(
                                      t ->
                                          new RentalContractModel.Term(
                                              com.smartbox.investory.longterm.api.model.CashFlowTypeModel.valueOf(t.getType().name()),
                                              t.getAmount(),
                                              com.smartbox.investory.longterm.api.model.FrequencyModel.valueOf(t.getFrequency().name()),
                                              t.isPaidByTenant()))
                                  .toList()))
                  .toList();
      for (LongTermAssetCashFlowEntity flow :
          contracts.isEmpty()
              ? cashFlows.findAllByAssetIdOrderByValidFrom(asset.getId())
              : List.<LongTermAssetCashFlowEntity>of()) {
        BigDecimal annual = LongTermAssetCalculator.annualAmount(flow);
        periods.add(
            new LongTermAssetProjectionInput.Period(
                flow.getValidFrom(),
                flow.getValidTo(),
                isIncome(flow.getType()) ? annual : BigDecimal.ZERO,
                isIncome(flow.getType()) ? BigDecimal.ZERO : annual,
                BigDecimal.ZERO,
                flow.getType(),
                flow.isPaidByTenant()));
      }
      for (LongTermAssetValuationPeriodEntity period :
          valuations.findAllByAssetIdOrderByValidFrom(asset.getId()))
        periods.add(
            new LongTermAssetProjectionInput.Period(
                period.getValidFrom(),
                period.getValidTo(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                period.getExpectedAnnualGrowthRate()));
      for (LongTermAssetBondRatePeriodEntity period :
          bondRates.findAllByAssetIdOrderByValidFrom(asset.getId()))
        periods.add(
            new LongTermAssetProjectionInput.Period(
                period.getValidFrom(),
                period.getValidTo(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                period.getAnnualInterestRate()));
      LocalDate maturity = null;
      BigDecimal redemption = null, tax = BigDecimal.ZERO, taxBase = null;
      InterestTreatment treatment = null;
      if (asset.getType() == LongTermAssetType.REAL_ESTATE) {
        tax =
            rentalTaxPolicy(portfolioId, effectivePolicyDate)
                .map(RentalTaxPolicyEntity::getRate)
                .orElse(REAL_ESTATE_TAX_RATE);
        taxBase = asset.getTaxBase();
      } else if (asset.getType() == LongTermAssetType.BOND) {
        LongTermAssetBondDetailsEntity d = bonds.findById(asset.getId()).orElse(null);
        if (d != null) {
          maturity = d.getMaturityDate();
          redemption = d.getRedemptionValue();
          treatment = d.getInterestTreatment();
          tax = d.getTaxRate() == null ? BOND_TAX_RATE : d.getTaxRate();
        }
      } else if (asset.getType() == LongTermAssetType.DEPOSIT) {
        LongTermAssetDepositDetailsEntity d = deposits.findById(asset.getId()).orElse(null);
        if (d != null) {
          maturity = d.getMaturityDate();
          tax = d.getTaxRate();
          treatment = d.getInterestTreatment();
          periods.add(
              new LongTermAssetProjectionInput.Period(
                  asset.getAcquisitionDate() == null
                      ? effectivePolicyDate
                      : asset.getAcquisitionDate(),
                  maturity,
                  BigDecimal.ZERO,
                  BigDecimal.ZERO,
                  d.getAnnualInterestRate()));
        }
      }
      result.add(
          new LongTermAssetProjectionInput(
              asset.getId(),
              asset.getName(),
              asset.getType(),
              asset.getCurrency(),
              asset.getCurrentValue(),
              periods,
              contracts,
              maturity,
              redemption,
              treatment,
              tax,
              taxBase,
              asset.isRentalTaxPaidByTenant()));
    }
    return result;
  }

  public RentalTaxPolicyEntity saveRentalTaxPolicy(Long portfolioId, RentalTaxPolicyEntity policy) {
    policy.setPortfolioId(portfolioId);
    validateRange(policy.getValidFrom(), policy.getValidTo());
    if (policy.getRate() == null
        || policy.getRate().signum() < 0
        || policy.getRate().compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Tax rate must be between 0 and 1");
    for (RentalTaxPolicyEntity existing : taxPolicies.findAll())
      if (Objects.equals(existing.getPortfolioId(), portfolioId)
          && !Objects.equals(existing.getId(), policy.getId())
          && rangesOverlap(
              policy.getValidFrom(),
              policy.getValidTo(),
              existing.getValidFrom(),
              existing.getValidTo()))
        throw new IllegalArgumentException("Overlapping rental tax policies are not allowed");
    return taxPolicies.save(policy);
  }

  public LongTermAssetEntity updateTaxBase(Long portfolioId, Long assetId, BigDecimal taxBase) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("Tax base applies only to real estate");
    if (taxBase == null || taxBase.signum() < 0)
      throw new IllegalArgumentException("Tax base must be non-negative");
    asset.setTaxBase(taxBase);
    return assets.save(asset);
  }

  public LongTermAssetEntity save(LongTermAssetEntity asset) {
    validate(asset);
    if (asset.getId() != null) {
      LongTermAssetEntity existing =
          assets
              .findByIdAndPortfolioId(asset.getId(), asset.getPortfolioId())
              .orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
      LongTermAssetType originalType =
          assets
              .findTypeByIdAndPortfolioId(asset.getId(), asset.getPortfolioId())
              .orElse(existing.getType());
      if (originalType != asset.getType())
        throw new IllegalArgumentException("AssetEntity type cannot be changed after creation");
      if (asset.getType() != LongTermAssetType.BOND && bonds.existsById(asset.getId()))
        throw new IllegalArgumentException("Bond details require a bond asset");
      if (asset.getType() != LongTermAssetType.DEPOSIT && deposits.existsById(asset.getId()))
        throw new IllegalArgumentException("Deposit details require a deposit asset");
      if (asset.getType() != LongTermAssetType.REAL_ESTATE
          && !cashFlows.findAllByAssetIdOrderByValidFrom(asset.getId()).isEmpty())
        throw new IllegalArgumentException("Cash flows require a real-estate asset");
      if (asset.getType() != LongTermAssetType.REAL_ESTATE
          && asset.getType() != LongTermAssetType.CASH_RESERVE
          && !valuations.findAllByAssetIdOrderByValidFrom(asset.getId()).isEmpty())
        throw new IllegalArgumentException("Valuation periods require a supported asset type");
      if (asset.getType() != LongTermAssetType.BOND
          && !bondRates.findAllByAssetIdOrderByValidFrom(asset.getId()).isEmpty())
        throw new IllegalArgumentException("Bond-rate periods require a bond asset");
      if (asset.getType() != LongTermAssetType.REAL_ESTATE && asset.isRentalTaxPaidByTenant())
        throw new IllegalArgumentException("Rental-tax settings require a real-estate asset");
    }
    LongTermAssetEntity saved = assets.save(asset);
    lifecycle.ensureInitialPeriod(saved);
    return saved;
  }

  public LongTermAssetEntity saveCashReserve(
      Long portfolioId,
      Long id,
      String name,
      CurrencyType currency,
      BigDecimal value,
      BigDecimal annualReturnRate,
      String notes,
      LocalDate effectiveFrom) {
    LongTermAssetEntity asset = id == null ? new LongTermAssetEntity() : owned(portfolioId, id);
    if (id != null && asset.getType() != LongTermAssetType.CASH_RESERVE)
      throw new IllegalArgumentException("AssetEntity type cannot be changed after creation");
    asset.setPortfolioId(portfolioId);
    asset.setName(name);
    asset.setType(LongTermAssetType.CASH_RESERVE);
    asset.setCurrency(currency);
    asset.setCurrentValue(value);
    asset.setAcquisitionValue(value);
    asset.setAcquisitionDate(effectiveFrom);
    asset.setNotes(notes);
    asset.setActive(true);
    save(asset);
    if (annualReturnRate != null) validateGrowthRate(annualReturnRate);
    closeValuationPeriods(asset.getId(), effectiveFrom);
    if (annualReturnRate != null) {
      LongTermAssetValuationPeriodEntity period = new LongTermAssetValuationPeriodEntity();
      period.setAssetId(asset.getId());
      period.setValidFrom(effectiveFrom);
      period.setExpectedAnnualGrowthRate(annualReturnRate);
      valuations.save(period);
    }
    return asset;
  }

  public LongTermAssetEntity saveRealEstateEntry(
      Long portfolioId, Long id, RealEstateEntryModel entry) {
    if (entry == null || entry.effectiveFrom() == null)
      throw new IllegalArgumentException("Effective-from date is required");
    LongTermAssetEntity asset = id == null ? new LongTermAssetEntity() : owned(portfolioId, id);
    if (id != null && asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("AssetEntity type cannot be changed after creation");
    asset.setPortfolioId(portfolioId);
    asset.setName(entry.name());
    asset.setType(LongTermAssetType.REAL_ESTATE);
    asset.setCurrency(entry.currency());
    asset.setAcquisitionDate(entry.acquisitionDate());
    asset.setAcquisitionValue(entry.acquisitionValue());
    asset.setCurrentValue(entry.currentValue());
    asset.setTaxBase(entry.taxBase());
    asset.setRentalTaxPaidByTenant(entry.rentalTaxPaidByTenant());
    asset.setNotes(entry.notes());
    asset.setActive(true);
    save(asset);
    LocalDate from = entry.effectiveFrom();
    upsertCashFlow(asset.getId(), CashFlowType.RENT, entry.monthlyRent(), Frequency.MONTHLY, from);
    upsertCashFlow(
        asset.getId(),
        CashFlowType.PARKING_RENT,
        entry.monthlyParkingIncome(),
        Frequency.MONTHLY,
        from);
    upsertCashFlow(
        asset.getId(),
        CashFlowType.ADMIN_FEE,
        entry.monthlyAdministrationCost(),
        Frequency.MONTHLY,
        from);
    upsertCashFlow(
        asset.getId(),
        CashFlowType.OTHER_EXPENSE,
        entry.monthlyOtherCost(),
        Frequency.MONTHLY,
        from);
    upsertCashFlow(
        asset.getId(),
        CashFlowType.PROPERTY_TAX,
        entry.annualPropertyTax(),
        Frequency.ANNUAL,
        from);
    upsertCashFlow(
        asset.getId(), CashFlowType.INSURANCE, entry.annualInsurance(), Frequency.ANNUAL, from);
    if (entry.expectedAnnualGrowthRate() != null) {
      validateGrowthRate(entry.expectedAnnualGrowthRate());
      closeValuationPeriods(asset.getId(), from);
      LongTermAssetValuationPeriodEntity period = new LongTermAssetValuationPeriodEntity();
      period.setAssetId(asset.getId());
      period.setValidFrom(from);
      period.setExpectedAnnualGrowthRate(entry.expectedAnnualGrowthRate());
      valuations.save(period);
    }
    return asset;
  }

  public LongTermAssetBondDetailsEntity saveBondDetails(
      Long portfolioId, Long assetId, LongTermAssetBondDetailsEntity details) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.BOND)
      throw new IllegalArgumentException("AssetEntity is not a bond");
    if (details.getMaturityDate() == null
        || (asset.getAcquisitionDate() != null
            && details.getMaturityDate().isBefore(asset.getAcquisitionDate())))
      throw new IllegalArgumentException("Bond maturity must be on or after acquisition");
    details.setAssetId(assetId);
    if (details.getTaxRate() == null) details.setTaxRate(BOND_TAX_RATE);
    validateTaxRate(details.getTaxRate(), "Bond tax rate");
    return bonds.save(details);
  }

  public void saveSimpleBond(
      Long portfolioId,
      Long assetId,
      BigDecimal rate,
      LocalDate maturityDate,
      InterestTreatment treatment) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.BOND)
      throw new IllegalArgumentException("AssetEntity is not a bond");
    LongTermAssetBondDetailsEntity details =
        bonds.findById(assetId).orElseGet(LongTermAssetBondDetailsEntity::new);
    details.setMaturityDate(maturityDate);
    details.setInterestTreatment(treatment);
    if (details.getTaxRate() == null) details.setTaxRate(BOND_TAX_RATE);
    details.setRedemptionValue(asset.getAcquisitionValue());
    saveBondDetails(portfolioId, assetId, details);
    LocalDate from = asset.getAcquisitionDate() == null ? today() : asset.getAcquisitionDate();
    LongTermAssetBondRatePeriodEntity period =
        bondRates.findAllByAssetIdOrderByValidFrom(assetId).stream()
            .filter(p -> p.getValidFrom().equals(from))
            .findFirst()
            .orElseGet(LongTermAssetBondRatePeriodEntity::new);
    period.setAssetId(assetId);
    period.setValidFrom(from);
    period.setValidTo(maturityDate);
    period.setAnnualInterestRate(rate);
    validateRange(period.getValidFrom(), period.getValidTo());
    if (period.getId() == null) validateBondRateOverlap(assetId, period);
    bondRates.save(period);
  }

  public LongTermAssetDepositDetailsEntity saveDepositDetails(
      Long portfolioId, Long assetId, LongTermAssetDepositDetailsEntity details) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.DEPOSIT)
      throw new IllegalArgumentException("AssetEntity is not a deposit");
    if (details.getMaturityDate() != null
        && asset.getAcquisitionDate() != null
        && details.getMaturityDate().isBefore(asset.getAcquisitionDate()))
      throw new IllegalArgumentException("Deposit maturity must be on or after acquisition");
    details.setAssetId(assetId);
    if (details.getTaxRate() == null) details.setTaxRate(BOND_TAX_RATE);
    validateTaxRate(details.getTaxRate(), "Deposit tax rate");
    if (details.getAnnualInterestRate() == null || details.getAnnualInterestRate().signum() < 0)
      throw new IllegalArgumentException("Deposit interest rate must be non-negative");
    return deposits.save(details);
  }

  public void archive(Long portfolioId, Long id) {
    lifecycle.archive(portfolioId, id);
  }

  public void reactivate(Long portfolioId, Long id) {
    lifecycle.reactivate(portfolioId, id);
  }

  public LongTermAssetCashFlowEntity addCashFlow(
      Long portfolioId, Long assetId, LongTermAssetCashFlowEntity flow) {
    return cashFlowService.add(portfolioId, assetId, flow);
  }

  public LongTermAssetCashFlowEntity addCashFlow(
      Long portfolioId, Long assetId, LongTermAssetCashFlowEntity flow, LocalDate date) {
    RentalPeriod period = rentalPeriod(portfolioId, assetId, date);
    if (period.effectiveFrom() != null) {
      flow.setValidFrom(period.effectiveFrom());
      flow.setValidTo(period.endDate());
    } else if (flow.getValidFrom() == null) {
      flow.setValidFrom(effectiveDate(date));
    }
    return addCashFlow(portfolioId, assetId, flow);
  }

  public void saveRentalPeriod(
      Long portfolioId, Long assetId, LocalDate effectiveFrom, LocalDate endDate, LocalDate date) {
    cashFlowService.saveRentalPeriod(
        portfolioId, assetId, effectiveFrom, endDate, currentCashFlows(portfolioId, assetId, date));
  }

  public LongTermAssetCashFlowEntity changeCashFlow(
      Long portfolioId,
      Long assetId,
      Long flowId,
      BigDecimal amount,
      Frequency frequency,
      LocalDate effectiveFrom,
      LocalDate validTo) {
    return cashFlowService.change(
        portfolioId, assetId, flowId, amount, frequency, effectiveFrom, validTo);
  }

  public LongTermAssetCashFlowEntity changeCurrentCashFlow(
      Long portfolioId, Long assetId, Long flowId, BigDecimal amount, Frequency frequency) {
    owned(portfolioId, assetId);
    LongTermAssetCashFlowEntity old = cashFlows.findById(flowId).orElseThrow();
    if (!Objects.equals(old.getAssetId(), assetId))
      throw new NoSuchElementException("Cash flow not found");
    return changeCashFlow(
        portfolioId, assetId, flowId, amount, frequency, old.getValidFrom(), old.getValidTo());
  }

  /** Compatibility overload for callers that keep the existing period open. */
  public LongTermAssetCashFlowEntity changeCashFlow(
      Long portfolioId,
      Long assetId,
      Long flowId,
      BigDecimal amount,
      Frequency frequency,
      LocalDate effectiveFrom) {
    return changeCashFlow(portfolioId, assetId, flowId, amount, frequency, effectiveFrom, null);
  }

  public void saveExpectedPropertyGrowth(
      Long portfolioId, Long assetId, BigDecimal growthRate, LocalDate effectiveFrom) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("AssetEntity is not real estate");
    if (growthRate != null) validateGrowthRate(growthRate);
    closeValuationPeriods(assetId, effectiveFrom);
    if (growthRate != null) {
      LongTermAssetValuationPeriodEntity period = new LongTermAssetValuationPeriodEntity();
      period.setAssetId(assetId);
      period.setValidFrom(effectiveFrom);
      period.setExpectedAnnualGrowthRate(growthRate);
      valuations.save(period);
    }
  }

  public void deleteCashFlow(Long portfolioId, Long assetId, Long flowId) {
    cashFlowService.delete(portfolioId, assetId, flowId);
  }

  public void setCashFlowPaidByTenant(
      Long portfolioId, Long assetId, Long flowId, boolean paidByTenant) {
    cashFlowService.setPaidByTenant(portfolioId, assetId, flowId, paidByTenant);
  }

  public LongTermAssetValuationPeriodEntity addValuationPeriod(
      Long portfolioId, Long assetId, LongTermAssetValuationPeriodEntity period) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.REAL_ESTATE
        && asset.getType() != LongTermAssetType.CASH_RESERVE)
      throw new IllegalArgumentException(
          "Valuation periods apply only to real estate or cash reserve");
    validateRange(period.getValidFrom(), period.getValidTo());
    validateGrowthRate(period.getExpectedAnnualGrowthRate());
    validateValuationOverlap(assetId, period);
    period.setAssetId(assetId);
    return valuations.save(period);
  }

  public LongTermAssetBondRatePeriodEntity addBondRatePeriod(
      Long portfolioId, Long assetId, LongTermAssetBondRatePeriodEntity period) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.BOND)
      throw new IllegalArgumentException("Bond-rate periods apply only to bonds");
    validateRange(period.getValidFrom(), period.getValidTo());
    if (period.getAnnualInterestRate() == null || period.getAnnualInterestRate().signum() < 0)
      throw new IllegalArgumentException("Bond interest rate must be non-negative");
    validateBondRateOverlap(assetId, period);
    period.setAssetId(assetId);
    return bondRates.save(period);
  }

  public LongTermAssetSummary summary(LongTermAssetEntity a, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    BigDecimal gross = BigDecimal.ZERO,
        expenses = BigDecimal.ZERO,
        tax = BigDecimal.ZERO,
        rate = BigDecimal.ZERO;
    LocalDate maturity = null;
    List<LongTermAssetCashFlowEntity> flows = rentalAwareFlows(a.getId());
    RealEstatePlanningSummary realEstatePlanning = null;
    BondPlanningSummary bondPlanning = null;
    LocalDate rentEnd = null;
    if (a.getType() == LongTermAssetType.REAL_ESTATE) {
      var contractModels =
          rentalContracts == null
              ? List.<RentalContractModel>of()
              : rentalContracts.findAllByAssetIdOrderByStartDate(a.getId()).stream()
                  .map(
                      c ->
                          new RentalContractModel(
                              c.getId(),
                              c.getStartDate(),
                              c.getEndDate(),
                              c.getTerminatedDate(),
                              c.getRentalTaxPaidByTenant(),
                              c.getTerms().stream()
                                  .map(
                                      t ->
                                          new RentalContractModel.Term(
                                              com.smartbox.investory.longterm.api.model.CashFlowTypeModel.valueOf(t.getType().name()),
                                              t.getAmount(),
                                              com.smartbox.investory.longterm.api.model.FrequencyModel.valueOf(t.getFrequency().name()),
                                              t.isPaidByTenant()))
                                  .toList()))
                  .toList();
      if (contractModels.isEmpty()) {
        rentEnd = currentRentEnd(flows, effectiveDate);
        for (LongTermAssetCashFlowEntity flow : flows) {
          if (!LongTermAssetCalculator.applies(flow, effectiveDate)) continue;
          BigDecimal annual = LongTermAssetCalculator.annualAmount(flow);
          if (isIncome(flow.getType())) gross = gross.add(annual);
          else if (!flow.isPaidByTenant()) expenses = expenses.add(annual);
        }
        realEstatePlanning =
            realEstatePlanningCalculator.calculate(
                a.getCurrentValue(),
                a.getTaxBase(),
                flows,
                effectiveDate,
                rentalTaxPolicy(a.getPortfolioId(), effectiveDate)
                    .map(RentalTaxPolicyEntity::getRate)
                    .orElse(REAL_ESTATE_TAX_RATE),
                a.isRentalTaxPaidByTenant());
      } else {
        rentEnd =
            contractModels.stream()
                .filter(c -> c.startDate() != null && !c.startDate().isAfter(effectiveDate))
                .max(Comparator.comparing(RentalContractModel::startDate))
                .map(RentalContractModel::endDate)
                .orElse(null);
        realEstatePlanning =
            realEstatePlanningCalculator.calculate(
                a.getCurrentValue(),
                a.getTaxBase(),
                a.isRentalTaxPaidByTenant(),
                contractModels,
                effectiveDate,
                rentalTaxPolicy(a.getPortfolioId(), effectiveDate)
                    .map(RentalTaxPolicyEntity::getRate)
                    .orElse(REAL_ESTATE_TAX_RATE));
        gross = realEstatePlanning.monthlyIncome().multiply(BigDecimal.valueOf(12));
        expenses =
            realEstatePlanning
                .monthlyReduce()
                .multiply(BigDecimal.valueOf(12))
                .subtract(realEstatePlanning.annualTax());
      }
      tax = realEstatePlanning.annualTax();
    } else if (a.getType() == LongTermAssetType.BOND) {
      LongTermAssetBondDetailsEntity d = bonds.findById(a.getId()).orElse(null);
      if (d != null) maturity = d.getMaturityDate();
      rate = currentRate(a.getId(), effectiveDate);
      bondPlanning =
          bondPlanningCalculator.calculate(
              a.getCurrentValue(),
              rate,
              maturity,
              d == null ? null : d.getInterestTreatment(),
              d == null ? BOND_TAX_RATE : d.getTaxRate());
      gross = bondPlanning.grossInterest();
      tax = bondPlanning.annualTax();
    } else if (a.getType() == LongTermAssetType.DEPOSIT) {
      LongTermAssetDepositDetailsEntity d = deposits.findById(a.getId()).orElse(null);
      if (d != null) {
        maturity = d.getMaturityDate();
        rate = d.getAnnualInterestRate();
        gross = a.getCurrentValue().multiply(rate);
        tax = gross.multiply(d.getTaxRate());
      }
    } else if (a.getType() == LongTermAssetType.CASH_RESERVE) {
      rate =
          valuations.findAllByAssetIdOrderByValidFrom(a.getId()).stream()
              .filter(
                  p ->
                      LongTermAssetCalculator.applies(
                          p.getValidFrom(), p.getValidTo(), effectiveDate))
              .reduce((first, second) -> second)
              .map(LongTermAssetValuationPeriodEntity::getExpectedAnnualGrowthRate)
              .orElse(BigDecimal.ZERO);
    }
    AnnualEconomics economics = AnnualEconomics.of(a.getCurrentValue(), gross, expenses, tax);
    return new LongTermAssetSummary(
        a.getId(),
        a.getName(),
        a.getType(),
        a.getCurrency(),
        a.getCurrentValue(),
        maturity,
        rate,
        economics,
        realEstatePlanning,
        bondPlanning,
        rentEnd);
  }

  private static LocalDate currentRentEnd(List<LongTermAssetCashFlowEntity> flows, LocalDate date) {
    return flows.stream()
        .filter(flow -> flow.getType() == CashFlowType.RENT)
        .filter(flow -> LongTermAssetCalculator.applies(flow, date))
        .max(
            Comparator.comparing(LongTermAssetCashFlowEntity::getValidFrom)
                .thenComparing(flow -> Optional.ofNullable(flow.getId()).orElse(Long.MAX_VALUE)))
        .map(LongTermAssetCashFlowEntity::getValidTo)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public AggregateSummary aggregate(Long portfolioId, LocalDate date) {
    date = effectiveDate(date);
    com.smartbox.investory.shared.currency.CurrencyType base =
        portfolioContextReader
            .findById(portfolioId)
            .orElseThrow(() -> new NoSuchElementException("Portfolio not found"))
            .baseCurrency();
    return AggregateSummary.of(list(portfolioId, date), base, currencyRates, date);
  }

  @Transactional(readOnly = true)
  public AggregateSummary aggregateForLongTermAssets(Long portfolioId, LocalDate date) {
    date = effectiveDate(date);
    return AggregateSummary.of(list(portfolioId, date), planningCurrency, currencyRates, date);
  }

  /**
   * Returns the same normalized annual economics used by the Long-term Assets overview.
   *
   * <p>Current values are deliberately not exposed as historical values: this model has no dated
   * valuation table from which a prior Dec-31 balance could be proven.
   */
  /** Returns historical Long-Term facts in the canonical USD application currency. */
  @Transactional(readOnly = true)
  public LongTermAssetAnnualSnapshotModel historicalAnnualSnapshot(Long portfolioId, int year) {
    LocalDate date = LocalDate.of(year, 12, 31);
    Set<Long> historicalAssetIds =
        assets.findAllByPortfolioIdOrderByName(portfolioId).stream()
            .filter(
                asset ->
                    (asset.getAcquisitionDate() == null
                            || !asset.getAcquisitionDate().isAfter(date))
                        && activeOn(asset, date))
            .map(LongTermAssetEntity::getId)
            .collect(java.util.stream.Collectors.toSet());
    List<LongTermAssetSummary> rows =
        assets.findAllByPortfolioIdOrderByName(portfolioId).stream()
            .filter(asset -> historicalAssetIds.contains(asset.getId()))
            .map(asset -> summary(asset, date))
            .filter(row -> historicalAssetIds.contains(row.id()))
            .toList();
    Map<Long, LongTermAssetProjectionInput> projectionInputs =
        projectionInputsAt(portfolioId, date, false).stream()
            .collect(java.util.stream.Collectors.toMap(LongTermAssetProjectionInput::id, x -> x));
    BigDecimal rentalIncome =
        rows.stream()
            .filter(row -> row.type() == LongTermAssetType.REAL_ESTATE)
            .map(
                row -> {
                  LongTermAssetProjectionInput input = projectionInputs.get(row.id());
                  BigDecimal amount;
                  // Historical snapshots expose the annual economics effective at the
                  // boundary date.  They intentionally do not prorate a period that began
                  // during the requested year; that is the established snapshot contract and
                  // keeps historical planning aligned with the asset summary.
                  amount = row.annualEconomics().netAnnualIncomeAfterTax();
                  return row.currency() == CurrencyType.USD
                      ? amount
                      : currencyRates.convertToBaseCurrency(
                          amount, CurrencyType.USD, row.currency(), date);
                })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    boolean hasRealEstate =
        rows.stream().anyMatch(row -> row.type() == LongTermAssetType.REAL_ESTATE);
    boolean rentalDataComplete =
        rows.stream()
            .filter(row -> row.type() == LongTermAssetType.REAL_ESTATE)
            .allMatch(
                row ->
                    !cashFlows.findAllByAssetIdOrderByValidFrom(row.id()).isEmpty()
                        || (rentalContracts != null
                            && !rentalContracts
                                .findAllByAssetIdOrderByStartDate(row.id())
                                .isEmpty()));
    return new LongTermAssetAnnualSnapshotModel(
        null, !hasRealEstate || rentalDataComplete ? rentalIncome : null, null, null, null, null);
  }

  /** Returns current Long-Term facts in the canonical USD application currency. */
  @Transactional(readOnly = true)
  public LongTermAssetAnnualSnapshotModel currentAnnualSnapshot(Long portfolioId, LocalDate date) {
    List<LongTermAssetSummary> rows = list(portfolioId, date);
    BigDecimal realEstateValue =
        sumCanonical(rows, LongTermAssetType.REAL_ESTATE, LongTermAssetSummary::currentValue, date);
    BigDecimal rentalIncome =
        sumCanonical(
            rows,
            LongTermAssetType.REAL_ESTATE,
            r -> r.annualEconomics().netAnnualIncomeAfterTax(),
            date);
    BigDecimal bondValue =
        sumCanonical(rows, LongTermAssetType.BOND, LongTermAssetSummary::currentValue, date);
    BigDecimal bondIncome =
        sumCanonical(
            rows, LongTermAssetType.BOND, r -> r.annualEconomics().netAnnualIncomeAfterTax(), date);
    BigDecimal cashReserveValue =
        sumCanonical(
            rows, LongTermAssetType.CASH_RESERVE, LongTermAssetSummary::currentValue, date);
    BigDecimal otherAssetValue = sumCanonical(rows, null, LongTermAssetSummary::currentValue, date);
    return new LongTermAssetAnnualSnapshotModel(
        realEstateValue, rentalIncome, bondValue, bondIncome, cashReserveValue, otherAssetValue);
  }

  private BigDecimal sumCanonical(
      List<LongTermAssetSummary> rows,
      LongTermAssetType type,
      java.util.function.Function<LongTermAssetSummary, BigDecimal> value,
      LocalDate date) {
    return rows.stream()
        .filter(
            row ->
                type == null
                    ? row.type() == LongTermAssetType.DEPOSIT
                        || row.type() == LongTermAssetType.OTHER
                    : row.type() == type)
        .map(
            row -> {
              BigDecimal amount = value.apply(row);
              if (amount == null) return BigDecimal.ZERO;
              return row.currency() == CurrencyType.USD
                  ? amount
                  : currencyRates.convertToBaseCurrency(
                      amount, CurrencyType.USD, row.currency(), date);
            })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private LocalDate effectiveDate(LocalDate date) {
    return date != null && date.isBefore(historyStart) ? historyStart : date;
  }

  private LocalDate today() {
    return LocalDate.now(applicationClock);
  }

  private boolean activeOn(LongTermAssetEntity asset, LocalDate date) {
    return lifecycle.activeOn(asset, date);
  }

  public record AggregateSummary(
      CurrencyType currency, BigDecimal totalCurrentValue, AnnualEconomics annualEconomics) {
    public String totalCurrentValueWholeDisplay() {
      return FinancialPresentation.wholeNumber(totalCurrentValue);
    }

    public String netAnnualIncomeWholeDisplay() {
      return FinancialPresentation.wholeNumber(annualEconomics.netAnnualIncomeAfterTax());
    }

    public String netYieldDisplay() {
      return FinancialPresentation.percentage(netYieldAfterTax());
    }

    public String netYieldWithLabelDisplay() {
      return netYieldDisplay() + " net yield";
    }

    public String monthlyNetIncomeWholeDisplay() {
      return FinancialPresentation.wholeNumber(
          annualEconomics
              .netAnnualIncomeAfterTax()
              .divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP));
    }

    public String grossAnnualIncomeWholeDisplay() {
      return FinancialPresentation.wholeNumber(annualEconomics.grossAnnualIncome());
    }

    public String annualExpensesAndTaxWholeDisplay() {
      return FinancialPresentation.wholeNumber(
          annualEconomics.annualExpenses().add(annualEconomics.annualTax()));
    }

    public String grossYieldDisplay() {
      return FinancialPresentation.percentage(annualEconomics.grossYield());
    }

    private BigDecimal netYieldAfterTax() {
      return annualEconomics.netYieldAfterTax();
    }

    @Deprecated
    public AggregateSummary(
        CurrencyType currency,
        BigDecimal totalCurrentValue,
        BigDecimal gross,
        BigDecimal expenses,
        BigDecimal netBeforeTax,
        BigDecimal tax,
        BigDecimal netAfterTax) {
      this(
          currency, totalCurrentValue, AnnualEconomics.of(totalCurrentValue, gross, expenses, tax));
    }

    public BigDecimal weightedGrossYield() {
      return annualEconomics.grossYield();
    }

    public BigDecimal weightedNetYield() {
      return annualEconomics.netYieldAfterTax();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal totalGrossAnnualIncome() {
      return annualEconomics.grossAnnualIncome();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal totalAnnualExpenses() {
      return annualEconomics.annualExpenses();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal totalNetAnnualIncomeBeforeTax() {
      return annualEconomics.netAnnualIncomeBeforeTax();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal estimatedAnnualTax() {
      return annualEconomics.annualTax();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal totalNetAnnualIncomeAfterTax() {
      return annualEconomics.netAnnualIncomeAfterTax();
    }

    static AggregateSummary of(
        List<LongTermAssetSummary> rows,
        CurrencyType base,
        CurrencyConversion rates,
        LocalDate date) {
      BigDecimal value = sum(rows, LongTermAssetSummary::currentValue, base, rates, date);
      BigDecimal gross = sum(rows, r -> r.annualEconomics().grossAnnualIncome(), base, rates, date);
      BigDecimal expenses = sum(rows, r -> r.annualEconomics().annualExpenses(), base, rates, date);
      BigDecimal tax = sum(rows, r -> r.annualEconomics().annualTax(), base, rates, date);
      return new AggregateSummary(
          base, value, AnnualEconomics.aggregateOf(value, gross, expenses, tax));
    }

    private static BigDecimal sum(
        List<LongTermAssetSummary> rows,
        java.util.function.Function<LongTermAssetSummary, BigDecimal> f,
        CurrencyType base,
        CurrencyConversion rates,
        LocalDate date) {
      return rows.stream()
          .map(
              r -> {
                BigDecimal amount = Optional.ofNullable(f.apply(r)).orElse(BigDecimal.ZERO);
                return r.currency() == base
                    ? amount
                    : rates.convertToBaseCurrency(amount, base, r.currency(), date);
              })
          .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
  }

  public record AssetGroupSummary(
      String key,
      String title,
      CurrencyType currency,
      List<LongTermAssetSummary> assets,
      BigDecimal totalValue,
      AnnualEconomics annualEconomics,
      RealEstateGroupPlanningSummary realEstatePlanning) {
    public BigDecimal netMonthlyIncome() {
      return realEstatePlanning == null ? BigDecimal.ZERO : realEstatePlanning.netMonthlyIncome();
    }

    public String shareDisplay(BigDecimal total) {
      if (total == null || total.signum() == 0) return "0.0%";
      return FinancialPresentation.percentage(
          totalValue.divide(total, 8, java.math.RoundingMode.HALF_UP));
    }

    public BigDecimal shareWidth(BigDecimal total) {
      if (total == null || total.signum() == 0) return BigDecimal.ZERO;
      return totalValue.divide(total, 8, java.math.RoundingMode.HALF_UP).movePointRight(2);
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal grossAnnualIncome() {
      return annualEconomics.grossAnnualIncome();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal grossYield() {
      return annualEconomics.grossYield();
    }

    /**
     * @deprecated Use {@link #realEstatePlanning()}.
     */
    @Deprecated
    public BigDecimal totalPaymentMonthly() {
      return realEstatePlanning == null
          ? BigDecimal.ZERO
          : realEstatePlanning.totalPaymentMonthly();
    }
  }

  private AssetGroupSummary group(
      String key,
      String title,
      List<LongTermAssetSummary> rows,
      CurrencyType base,
      LocalDate date) {
    BigDecimal value =
        AggregateSummary.sum(rows, LongTermAssetSummary::currentValue, base, currencyRates, date);
    BigDecimal income =
        AggregateSummary.sum(
            rows, r -> r.annualEconomics().grossAnnualIncome(), base, currencyRates, date);
    BigDecimal expenses =
        AggregateSummary.sum(
            rows, r -> r.annualEconomics().annualExpenses(), base, currencyRates, date);
    BigDecimal annualTax =
        AggregateSummary.sum(rows, r -> r.annualEconomics().annualTax(), base, currencyRates, date);
    BigDecimal payment =
        AggregateSummary.sum(
            rows,
            r -> planning(r, BigDecimal.ZERO).totalPaymentMonthly(),
            base,
            currencyRates,
            date);
    BigDecimal monthlyIncome =
        AggregateSummary.sum(
            rows, r -> planning(r, BigDecimal.ZERO).monthlyIncome(), base, currencyRates, date);
    BigDecimal monthlyReduce =
        AggregateSummary.sum(
            rows, r -> planning(r, BigDecimal.ZERO).monthlyReduce(), base, currencyRates, date);
    BigDecimal monthlyRentTax =
        AggregateSummary.sum(
                rows, r -> planning(r, BigDecimal.ZERO).annualTax(), base, currencyRates, date)
            .divide(BigDecimal.valueOf(12), 18, java.math.RoundingMode.HALF_UP);
    AnnualEconomics economics = AnnualEconomics.aggregateOf(value, income, expenses, annualTax);
    RealEstateGroupPlanningSummary planning =
        "REAL_ESTATE".equals(key)
            ? new RealEstateGroupPlanningSummary(
                payment,
                monthlyIncome.subtract(monthlyReduce),
                monthlyRentTax,
                LongTermAssetCalculator.ratio(
                    monthlyIncome.subtract(monthlyReduce).multiply(BigDecimal.valueOf(12)), value))
            : null;
    return new AssetGroupSummary(key, title, base, rows, value, economics, planning);
  }

  private static RealEstatePlanningSummary planning(LongTermAssetSummary row, BigDecimal zero) {
    return row.realEstatePlanning() == null
        ? new RealEstatePlanningSummary(zero, zero, zero, zero, zero, zero)
        : row.realEstatePlanning();
  }

  private BigDecimal currentRate(Long id, LocalDate date) {
    return bondRates.findAllByAssetIdOrderByValidFrom(id).stream()
        .filter(p -> LongTermAssetCalculator.applies(p.getValidFrom(), p.getValidTo(), date))
        .findFirst()
        .map(LongTermAssetBondRatePeriodEntity::getAnnualInterestRate)
        .orElse(BigDecimal.ZERO);
  }

  private static boolean isIncome(CashFlowType t) {
    return t == CashFlowType.RENT
        || t == CashFlowType.PARKING_RENT
        || t == CashFlowType.OTHER_INCOME;
  }

  private List<LongTermAssetCashFlowEntity> rentalAwareFlows(Long assetId) {
    if (rentalContracts == null) return cashFlows.findAllByAssetIdOrderByValidFrom(assetId);
    List<LongTermAssetRentalContractEntity> contracts =
        rentalContracts.findAllByAssetIdOrderByStartDate(assetId);
    if (contracts.isEmpty()) return cashFlows.findAllByAssetIdOrderByValidFrom(assetId);
    List<LongTermAssetCashFlowEntity> result = new ArrayList<>();
    for (var contract : contracts) {
      for (var term : contract.getTerms()) {
        var flow = new LongTermAssetCashFlowEntity();
        flow.setAssetId(assetId);
        flow.setType(term.getType());
        flow.setAmount(term.getAmount());
        flow.setFrequency(term.getFrequency());
        flow.setValidFrom(contract.getStartDate());
        flow.setValidTo(effectiveContractEnd(contract));
        flow.setPaidByTenant(term.isPaidByTenant());
        result.add(flow);
      }
    }
    return result;
  }

  private static LocalDate effectiveContractEnd(LongTermAssetRentalContractEntity c) {
    if (c.getEndDate() == null) return c.getTerminatedDate();
    if (c.getTerminatedDate() == null) return c.getEndDate();
    return c.getEndDate().isBefore(c.getTerminatedDate()) ? c.getEndDate() : c.getTerminatedDate();
  }

  public record RentalPeriod(LocalDate effectiveFrom, LocalDate endDate) {}

  private LongTermAssetEntity owned(Long portfolioId, Long id) {
    return assets
        .findByIdAndPortfolioId(id, portfolioId)
        .orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
  }

  private static void validate(LongTermAssetEntity a) {
    if (a.getName() == null || a.getName().isBlank())
      throw new IllegalArgumentException("Name is required");
    if (a.getCurrentValue() == null || a.getCurrentValue().signum() < 0)
      throw new IllegalArgumentException("Current value must be non-negative");
  }

  private static void validateGrowthRate(BigDecimal rate) {
    if (rate == null
        || rate.compareTo(BigDecimal.ONE.negate()) < 0
        || rate.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Expected property growth rate must be between -1 and 1");
  }

  private static void validateTaxRate(BigDecimal rate, String label) {
    if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException(label + " must be between 0 and 1");
  }

  private static void validateRange(LocalDate from, LocalDate to) {
    if (from == null || (to != null && to.isBefore(from)))
      throw new IllegalArgumentException("Invalid period");
  }

  private void upsertCashFlow(
      Long assetId, CashFlowType type, BigDecimal amount, Frequency frequency, LocalDate from) {
    if (amount == null) return;
    List<LongTermAssetCashFlowEntity> existing =
        cashFlows.findAllByAssetIdOrderByValidFrom(assetId).stream()
            .filter(
                f ->
                    f.getType() == type
                        && (f.getValidTo() == null || !f.getValidTo().isBefore(from)))
            .toList();
    for (LongTermAssetCashFlowEntity old : existing) {
      if (old.getValidFrom().isBefore(from)) {
        old.setValidTo(from.minusDays(1));
        cashFlows.save(old);
      } else cashFlows.delete(old);
    }
    if (amount.signum() > 0) {
      LongTermAssetCashFlowEntity flow = new LongTermAssetCashFlowEntity();
      flow.setAssetId(assetId);
      flow.setType(type);
      flow.setAmount(amount);
      flow.setFrequency(frequency);
      flow.setValidFrom(from);
      flow.setPaidByTenant(type == CashFlowType.ADMIN_FEE || type == CashFlowType.UTILITIES);
      LongTermAssetPeriodRules.ensurePaidByTenant(flow);
      cashFlows.save(flow);
    }
  }

  private void closeValuationPeriods(Long id, LocalDate from) {
    for (LongTermAssetValuationPeriodEntity old :
        valuations.findAllByAssetIdOrderByValidFrom(id).stream()
            .filter(p -> p.getValidTo() == null || !p.getValidTo().isBefore(from))
            .toList()) {
      if (old.getValidFrom().isBefore(from)) {
        old.setValidTo(from.minusDays(1));
        valuations.save(old);
      } else valuations.delete(old);
    }
  }

  private void validateValuationOverlap(Long id, LongTermAssetValuationPeriodEntity p) {
    if (valuations.findAllByAssetIdOrderByValidFrom(id).stream()
        .anyMatch(
            x -> rangesOverlap(x.getValidFrom(), x.getValidTo(), p.getValidFrom(), p.getValidTo())))
      throw new IllegalArgumentException("Overlapping valuation period");
  }

  private void validateBondRateOverlap(Long id, LongTermAssetBondRatePeriodEntity p) {
    if (bondRates.findAllByAssetIdOrderByValidFrom(id).stream()
        .anyMatch(
            x -> rangesOverlap(x.getValidFrom(), x.getValidTo(), p.getValidFrom(), p.getValidTo())))
      throw new IllegalArgumentException("Overlapping bond-rate period");
  }

  private static boolean rangesOverlap(LocalDate a, LocalDate b, LocalDate c, LocalDate d) {
    return LongTermAssetPeriodRules.overlaps(a, b, c, d);
  }
}
