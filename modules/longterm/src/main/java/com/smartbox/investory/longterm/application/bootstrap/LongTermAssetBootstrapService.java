package com.smartbox.investory.longterm.application.bootstrap;

import com.smartbox.investory.longterm.api.CashFlowType;
import com.smartbox.investory.longterm.api.Frequency;
import com.smartbox.investory.longterm.api.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.*;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LongTermAssetBootstrapService {
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

  private final LongTermAssetRepository assets;
  private final LongTermAssetCashFlowRepository cashFlows;
  private final LongTermAssetValuationPeriodRepository valuations;
  private final LongTermAssetBondRatePeriodRepository bondRates;
  private final LongTermAssetBondDetailsRepository bonds;
  private final LongTermAssetDepositDetailsRepository deposits;
  private final RentalTaxPolicyRepository taxPolicies;
  private final PortfolioContextReader portfolioContextReader;

  @Transactional
  public LongTermAssetBootstrapResult importDocument(
      LongTermAssetBootstrapDocument document, boolean dryRun) {
    validate(document);
    var existing =
        assets.findAllByPortfolioIdOrderByName(document.portfolioId()).stream()
            .filter(a -> a.getExternalKey() != null)
            .collect(java.util.stream.Collectors.toMap(LongTermAsset::getExternalKey, a -> a));
    int creates = 0;
    int updates = 0;
    for (var input : safe(document.assets())) {
      if (existing.containsKey(input.externalKey())) updates++;
      else creates++;
    }
    var existingPolicies =
        taxPolicies.findAll().stream()
            .filter(p -> p.getPortfolioId().equals(document.portfolioId()))
            .collect(java.util.stream.Collectors.toMap(RentalTaxPolicy::getValidFrom, p -> p));
    int policyCreates = 0;
    int policyUpdates = 0;
    for (var input : safe(document.rentalTaxPolicies())) {
      if (existingPolicies.containsKey(input.validFrom())) policyUpdates++;
      else policyCreates++;
    }
    var totals = calculateRealEstateTotals(document);
    if (!dryRun) {
      for (var policy : safe(document.rentalTaxPolicies()))
        upsertTaxPolicy(document.portfolioId(), policy, existingPolicies.get(policy.validFrom()));
      for (var input : safe(document.assets()))
        upsertAsset(document.portfolioId(), input, existing.get(input.externalKey()));
    }
    return new LongTermAssetBootstrapResult(
        creates,
        updates,
        policyCreates,
        policyUpdates,
        dryRun,
        totals.value(),
        totals.grossIncome(),
        totals.expenses(),
        totals.tax(),
        totals.netIncome());
  }

  private void validate(LongTermAssetBootstrapDocument document) {
    if (document == null || document.portfolioId() == null)
      throw new IllegalArgumentException("portfolioId is required");
    if (portfolioContextReader.findById(document.portfolioId()).isEmpty())
      throw new IllegalArgumentException("Portfolio not found: " + document.portfolioId());
    var keys = new HashSet<String>();
    for (var policy : safe(document.rentalTaxPolicies())) {
      if (policy == null) throw new IllegalArgumentException("Rental tax policy cannot be null");
      validatePeriod(policy.validFrom(), policy.validTo(), "rental tax policy");
      requireRate(policy.rate(), "rental tax rate", BigDecimal.ZERO, BigDecimal.ONE);
    }
    validateNoOverlap(
        safe(document.rentalTaxPolicies()).stream()
            .map(p -> new DatePeriod(p.validFrom(), p.validTo()))
            .toList(),
        "rental tax policies");
    var storedPolicies =
        taxPolicies.findAll().stream()
            .filter(p -> p.getPortfolioId().equals(document.portfolioId()))
            .toList();
    for (var input : safe(document.rentalTaxPolicies()))
      for (var stored : storedPolicies)
        if (!stored.getValidFrom().equals(input.validFrom())
            && new DatePeriod(input.validFrom(), input.validTo())
                .overlaps(new DatePeriod(stored.getValidFrom(), stored.getValidTo())))
          throw new IllegalArgumentException("Overlapping rental tax policies");
    for (var asset : safe(document.assets())) {
      if (asset == null) throw new IllegalArgumentException("Asset cannot be null");
      if (asset.externalKey() == null
          || asset.externalKey().isBlank()
          || asset.externalKey().length() > 128)
        throw new IllegalArgumentException(
            "Asset externalKey is required and must be at most 128 characters");
      if (!keys.add(asset.externalKey()))
        throw new IllegalArgumentException("Duplicate asset externalKey: " + asset.externalKey());
      if (asset.type() == null
          || asset.name() == null
          || asset.name().isBlank()
          || asset.currency() == null)
        throw new IllegalArgumentException(
            "Asset key " + asset.externalKey() + " requires type, name and currency");
      requireNonNegative(asset.currentValue(), "current value for " + asset.externalKey());
      requireNonNegative(asset.acquisitionValue(), "acquisition value for " + asset.externalKey());
      requireNonNegative(asset.taxBase(), "tax base for " + asset.externalKey());
      if (asset.effectiveFrom() == null)
        throw new IllegalArgumentException(
            "Effective-from date is required for " + asset.externalKey());
      for (var flow : safe(asset.cashFlows())) {
        if (flow == null || flow.type() == null || flow.frequency() == null)
          throw new IllegalArgumentException(
              "Cash flow type and frequency are required for " + asset.externalKey());
        requireNonNegative(flow.amount(), "cash-flow amount for " + asset.externalKey());
        validatePeriod(flow.validFrom(), flow.validTo(), "cash flow for " + asset.externalKey());
      }
      validateNoOverlapByKey(
          asset.cashFlows(),
          "cash flows for " + asset.externalKey(),
          f -> f.type().name(),
          f -> new DatePeriod(f.validFrom(), f.validTo()));
      if (asset.type() != LongTermAssetType.REAL_ESTATE && !safe(asset.cashFlows()).isEmpty())
        throw new IllegalArgumentException(
            "Cash flows require real estate: " + asset.externalKey());
      if (asset.type() != LongTermAssetType.REAL_ESTATE
          && asset.type() != LongTermAssetType.CASH_RESERVE
          && !safe(asset.valuationPeriods()).isEmpty())
        throw new IllegalArgumentException(
            "Valuation periods require real estate: " + asset.externalKey());
      if (asset.type() != LongTermAssetType.BOND && !safe(asset.bondRatePeriods()).isEmpty())
        throw new IllegalArgumentException(
            "Bond-rate periods require a bond: " + asset.externalKey());
      validatePeriods(
          asset.valuationPeriods(), "valuation periods for " + asset.externalKey(), false);
      validatePeriods(
          asset.bondRatePeriods(), "bond-rate periods for " + asset.externalKey(), true);
      if (asset.type() == LongTermAssetType.BOND) validateBond(asset);
      if (asset.type() == LongTermAssetType.DEPOSIT) validateDeposit(asset);
    }
  }

  private void validateBond(LongTermAssetBootstrapDocument.Asset asset) {
    var bond = asset.bond();
    if (bond == null)
      throw new IllegalArgumentException("Bond details are required for " + asset.externalKey());
    validateMaturity(asset, bond.maturityDate(), "bond");
    if (bond.interestTreatment() == null)
      throw new IllegalArgumentException(
          "Bond interest treatment is required for " + asset.externalKey());
    requireRate(
        bond.taxRate(),
        "bond tax rate for " + asset.externalKey(),
        BigDecimal.ZERO,
        BigDecimal.ONE);
    requireNonNegative(bond.redemptionValue(), "bond redemption value for " + asset.externalKey());
  }

  private void validateDeposit(LongTermAssetBootstrapDocument.Asset asset) {
    var deposit = asset.deposit();
    if (deposit == null)
      throw new IllegalArgumentException("Deposit details are required for " + asset.externalKey());
    if (deposit.maturityDate() != null) validateMaturity(asset, deposit.maturityDate(), "deposit");
    if (deposit.interestTreatment() == null)
      throw new IllegalArgumentException(
          "Deposit interest treatment is required for " + asset.externalKey());
    requireNonNegative(
        deposit.annualInterestRate(), "deposit interest rate for " + asset.externalKey());
    requireRate(
        deposit.taxRate(),
        "deposit tax rate for " + asset.externalKey(),
        BigDecimal.ZERO,
        BigDecimal.ONE);
  }

  private static void validateMaturity(
      LongTermAssetBootstrapDocument.Asset asset, LocalDate maturity, String type) {
    if (maturity == null
        || (asset.acquisitionDate() != null && maturity.isBefore(asset.acquisitionDate())))
      throw new IllegalArgumentException(
          type + " maturity must be on or after acquisition for " + asset.externalKey());
  }

  private void validatePeriods(
      List<LongTermAssetBootstrapDocument.Period> periods, String label, boolean nonNegativeRate) {
    for (var period : safe(periods)) {
      if (period == null) throw new IllegalArgumentException(label + " cannot contain null");
      validatePeriod(period.validFrom(), period.validTo(), label);
      if (nonNegativeRate) requireNonNegative(period.annualRate(), "rate in " + label);
    }
    validateNoOverlap(
        safe(periods).stream().map(p -> new DatePeriod(p.validFrom(), p.validTo())).toList(),
        label);
  }

  private static <T> void validateNoOverlapByKey(
      List<T> values,
      String label,
      java.util.function.Function<T, String> key,
      java.util.function.Function<T, DatePeriod> period) {
    var grouped = new HashMap<String, List<DatePeriod>>();
    for (var value : safe(values))
      grouped
          .computeIfAbsent(key.apply(value), ignored -> new ArrayList<>())
          .add(period.apply(value));
    grouped.values().forEach(periods -> validateNoOverlap(periods, label));
  }

  private static void validateNoOverlap(List<DatePeriod> periods, String label) {
    for (int i = 0; i < periods.size(); i++)
      for (int j = i + 1; j < periods.size(); j++)
        if (periods.get(i).overlaps(periods.get(j)))
          throw new IllegalArgumentException("Overlapping " + label);
  }

  private static void validatePeriod(LocalDate from, LocalDate to, String label) {
    if (from == null || (to != null && to.isBefore(from)))
      throw new IllegalArgumentException("Invalid period: " + label);
  }

  private static void requireNonNegative(BigDecimal value, String label) {
    if (value != null && value.signum() < 0)
      throw new IllegalArgumentException(label + " must be non-negative");
  }

  private static void requireRate(BigDecimal value, String label, BigDecimal min, BigDecimal max) {
    if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0)
      throw new IllegalArgumentException(label + " must be between " + min + " and " + max);
  }

  private void upsertTaxPolicy(
      Long portfolioId, LongTermAssetBootstrapDocument.TaxPolicy input, RentalTaxPolicy existing) {
    var policy = existing == null ? new RentalTaxPolicy() : existing;
    policy.setPortfolioId(portfolioId);
    policy.setValidFrom(input.validFrom());
    policy.setValidTo(input.validTo());
    policy.setRate(input.rate());
    taxPolicies.save(policy);
  }

  private void upsertAsset(
      Long portfolioId, LongTermAssetBootstrapDocument.Asset input, LongTermAsset existing) {
    var asset = existing == null ? new LongTermAsset() : existing;
    asset.setPortfolioId(portfolioId);
    asset.setExternalKey(input.externalKey());
    asset.setType(input.type());
    asset.setName(input.name());
    asset.setCurrency(input.currency());
    asset.setAcquisitionDate(input.acquisitionDate());
    asset.setAcquisitionValue(input.acquisitionValue());
    asset.setCurrentValue(input.currentValue());
    asset.setTaxBase(input.taxBase());
    asset.setRentalTaxPaidByTenant(Boolean.TRUE.equals(input.rentalTaxPaidByTenant()));
    asset.setNotes(input.notes());
    asset.setActive(true);
    asset = assets.save(asset);
    for (var flow : safe(input.cashFlows())) upsertCashFlow(asset.getId(), flow);
    for (var period : safe(input.valuationPeriods())) upsertValuation(asset.getId(), period);
    for (var period : safe(input.bondRatePeriods())) upsertBondRate(asset.getId(), period);
    if (input.bond() != null) upsertBondDetails(asset.getId(), input.bond());
    if (input.deposit() != null) upsertDepositDetails(asset.getId(), input.deposit());
  }

  private void upsertCashFlow(Long assetId, LongTermAssetBootstrapDocument.CashFlow input) {
    var existing =
        cashFlows.findAllByAssetIdOrderByValidFrom(assetId).stream()
            .filter(f -> f.getType() == input.type() && f.getValidFrom().equals(input.validFrom()))
            .findFirst()
            .orElseGet(LongTermAssetCashFlow::new);
    existing.setAssetId(assetId);
    existing.setType(input.type());
    existing.setAmount(input.amount());
    existing.setFrequency(input.frequency());
    existing.setValidFrom(input.validFrom());
    existing.setValidTo(input.validTo());
    existing.setPaidByTenant(
        input.paidByTenant() != null
            ? input.paidByTenant()
            : input.type() == CashFlowType.ADMIN_FEE || input.type() == CashFlowType.UTILITIES);
    cashFlows.save(existing);
  }

  private void upsertValuation(Long assetId, LongTermAssetBootstrapDocument.Period input) {
    var existing =
        valuations.findAllByAssetIdOrderByValidFrom(assetId).stream()
            .filter(p -> p.getValidFrom().equals(input.validFrom()))
            .findFirst()
            .orElseGet(LongTermAssetValuationPeriod::new);
    existing.setAssetId(assetId);
    existing.setValidFrom(input.validFrom());
    existing.setValidTo(input.validTo());
    existing.setExpectedAnnualGrowthRate(input.annualRate());
    valuations.save(existing);
  }

  private void upsertBondRate(Long assetId, LongTermAssetBootstrapDocument.Period input) {
    var existing =
        bondRates.findAllByAssetIdOrderByValidFrom(assetId).stream()
            .filter(p -> p.getValidFrom().equals(input.validFrom()))
            .findFirst()
            .orElseGet(LongTermAssetBondRatePeriod::new);
    existing.setAssetId(assetId);
    existing.setValidFrom(input.validFrom());
    existing.setValidTo(input.validTo());
    existing.setAnnualInterestRate(input.annualRate());
    bondRates.save(existing);
  }

  private void upsertBondDetails(Long assetId, LongTermAssetBootstrapDocument.Bond input) {
    var details = bonds.findById(assetId).orElseGet(LongTermAssetBondDetails::new);
    details.setAssetId(assetId);
    details.setMaturityDate(input.maturityDate());
    details.setInterestTreatment(input.interestTreatment());
    details.setTaxRate(input.taxRate());
    details.setRedemptionValue(input.redemptionValue());
    bonds.save(details);
  }

  private void upsertDepositDetails(Long assetId, LongTermAssetBootstrapDocument.Deposit input) {
    var details = deposits.findById(assetId).orElseGet(LongTermAssetDepositDetails::new);
    details.setAssetId(assetId);
    details.setMaturityDate(input.maturityDate());
    details.setInterestTreatment(input.interestTreatment());
    details.setAnnualInterestRate(input.annualInterestRate());
    details.setTaxRate(input.taxRate());
    deposits.save(details);
  }

  private Totals calculateRealEstateTotals(LongTermAssetBootstrapDocument document) {
    BigDecimal value = BigDecimal.ZERO,
        gross = BigDecimal.ZERO,
        expenses = BigDecimal.ZERO,
        tax = BigDecimal.ZERO;
    for (var asset : safe(document.assets()))
      if (asset.type() == LongTermAssetType.REAL_ESTATE) {
        value = value.add(asset.currentValue());
        for (var flow : safe(asset.cashFlows())) {
          var annual =
              flow.frequency() == Frequency.MONTHLY
                  ? flow.amount().multiply(TWELVE)
                  : flow.amount();
          if (flow.type() == CashFlowType.RENT
              || flow.type() == CashFlowType.PARKING_RENT
              || flow.type() == CashFlowType.OTHER_INCOME) gross = gross.add(annual);
          else if (!tenantPaid(flow)) expenses = expenses.add(annual);
        }
        BigDecimal taxBase = asset.taxBase() == null ? BigDecimal.ZERO : asset.taxBase();
        BigDecimal rate = effectiveTaxRate(document, asset.effectiveFrom());
        BigDecimal assetTax =
            Boolean.TRUE.equals(asset.rentalTaxPaidByTenant())
                ? BigDecimal.ZERO
                : taxBase.multiply(rate);
        tax = tax.add(assetTax);
      }
    return new Totals(value, gross, expenses, tax, gross.subtract(expenses).subtract(tax));
  }

  private BigDecimal effectiveTaxRate(LongTermAssetBootstrapDocument document, LocalDate date) {
    return safe(document.rentalTaxPolicies()).stream()
        .filter(p -> !p.validFrom().isAfter(date))
        .filter(p -> p.validTo() == null || !p.validTo().isBefore(date))
        .max(Comparator.comparing(LongTermAssetBootstrapDocument.TaxPolicy::validFrom))
        .map(LongTermAssetBootstrapDocument.TaxPolicy::rate)
        .orElse(new BigDecimal("0.085"));
  }

  private static boolean tenantPaid(LongTermAssetBootstrapDocument.CashFlow flow) {
    return flow.paidByTenant() != null
        ? flow.paidByTenant()
        : flow.type() == CashFlowType.ADMIN_FEE || flow.type() == CashFlowType.UTILITIES;
  }

  private record Totals(
      BigDecimal value,
      BigDecimal grossIncome,
      BigDecimal expenses,
      BigDecimal tax,
      BigDecimal netIncome) {}

  private record DatePeriod(LocalDate from, LocalDate to) {
    boolean overlaps(DatePeriod other) {
      return !from.isAfter(other.to == null ? LocalDate.MAX : other.to)
          && !other.from.isAfter(to == null ? LocalDate.MAX : to);
    }
  }

  private static <T> List<T> safe(List<T> values) {
    return values == null ? List.of() : values;
  }
}
