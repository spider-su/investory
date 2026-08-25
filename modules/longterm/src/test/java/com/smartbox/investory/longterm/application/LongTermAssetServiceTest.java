package com.smartbox.investory.longterm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.RealEstateEntryModel;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.application.model.RealEstatePlanningSummary;
import com.smartbox.investory.longterm.application.service.LongTermAssetAnnualSnapshotService;
import com.smartbox.investory.longterm.application.service.LongTermAssetCalculator;
import com.smartbox.investory.longterm.application.service.LongTermAssetCashFlowService;
import com.smartbox.investory.longterm.application.service.LongTermAssetCommandService;
import com.smartbox.investory.longterm.application.service.LongTermAssetLifecycleService;
import com.smartbox.investory.longterm.application.service.LongTermAssetPeriodService;
import com.smartbox.investory.longterm.application.service.LongTermAssetProjectionQueryService;
import com.smartbox.investory.longterm.application.service.LongTermAssetQueryService;
import com.smartbox.investory.longterm.application.service.LongTermAssetService;
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
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LongTermAssetServiceTest {
  @Mock LongTermAssetRepository assets;
  @Mock LongTermAssetCashFlowRepository cashFlows;
  @Mock LongTermAssetValuationPeriodRepository valuations;
  @Mock LongTermAssetBondRatePeriodRepository bondRates;
  @Mock LongTermAssetBondDetailsRepository bonds;
  @Mock LongTermAssetDepositDetailsRepository deposits;
  @Mock RentalTaxPolicyRepository taxPolicies;
  @Mock PortfolioContextReader portfolioContextReader;
  @Mock CurrencyConversion currencyRates;
  @Mock LongTermAssetLifecycleService lifecycle;
  @Mock LongTermAssetRentalContractRepository rentalContracts;
  LongTermAssetService service;

  @BeforeEach
  void setUp() {
    lenient()
        .when(lifecycle.activeOn(any(LongTermAssetEntity.class), any(LocalDate.class)))
        .thenReturn(true);
    // Test fixtures below use the old compact flow helper; convert them at the test boundary so
    // production code is exercised through the contract repository only.
    lenient()
        .when(rentalContracts.findAllByAssetIdOrderByStartDate(anyLong()))
        .thenAnswer(
            invocation ->
                cashFlows
                    .findAllByAssetIdOrderByValidFrom(invocation.getArgument(0, Long.class))
                    .stream()
                    .filter(f -> f.getType() != CashFlowType.RENT || f.getAmount() != null)
                    .collect(
                        java.util.stream.Collectors.groupingBy(
                            f -> java.util.Arrays.asList(f.getValidFrom(), f.getValidTo()),
                            java.util.LinkedHashMap::new,
                            java.util.stream.Collectors.toList()))
                    .values()
                    .stream()
                    .map(LongTermAssetServiceTest::contractFrom)
                    .toList());
    var query =
        new LongTermAssetQueryService(
            assets,
            cashFlows,
            valuations,
            bondRates,
            bonds,
            deposits,
            taxPolicies,
            portfolioContextReader,
            currencyRates,
            rentalContracts);
    var projection =
        new LongTermAssetProjectionQueryService(
            assets,
            valuations,
            bondRates,
            bonds,
            deposits,
            taxPolicies,
            rentalContracts,
            lifecycle);
    var snapshot =
        new LongTermAssetAnnualSnapshotService(
            assets, rentalContracts, currencyRates, lifecycle, query);
    var periods = new LongTermAssetPeriodService(assets, valuations, bondRates, taxPolicies);
    var command =
        new LongTermAssetCommandService(
            assets,
            cashFlows,
            valuations,
            bondRates,
            bonds,
            deposits,
            lifecycle,
            new LongTermAssetCashFlowService(assets, cashFlows),
            query,
            periods,
            java.time.Clock.fixed(
                DATE.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), java.time.ZoneOffset.UTC));
    service = new LongTermAssetService(query, projection, snapshot, command);
  }

  private static LongTermAssetRentalContractEntity contractFrom(
      List<LongTermAssetCashFlowEntity> flows) {
    LongTermAssetCashFlowEntity flow = flows.get(0);
    LongTermAssetRentalContractEntity contract = new LongTermAssetRentalContractEntity();
    contract.setAssetId(flow.getAssetId());
    contract.setStartDate(flow.getValidFrom());
    contract.setEndDate(flow.getValidTo());
    contract.setTerms(
        flows.stream()
            .map(
                f -> {
                  LongTermAssetRentalContractTermEntity term =
                      new LongTermAssetRentalContractTermEntity();
                  term.setContract(contract);
                  term.setType(f.getType());
                  term.setAmount(f.getAmount());
                  term.setFrequency(f.getFrequency());
                  term.setPaidByTenant(f.isPaidByTenant());
                  return term;
                })
            .toList());
    return contract;
  }

  @Test
  void editingArchivedCashReservePreservesAcquisitionAndLifecycleState() {
    LongTermAssetEntity reserve = asset(LongTermAssetType.CASH_RESERVE, "900");
    reserve.setActive(false);
    reserve.setAcquisitionDate(LocalDate.of(2020, 2, 3));
    reserve.setAcquisitionValue(new BigDecimal("700"));
    reserve.setArchivedAt(LocalDate.of(2026, 1, 15));
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(reserve));
    when(assets.findTypeByIdAndPortfolioId(1L, 1L))
        .thenReturn(Optional.of(LongTermAssetType.CASH_RESERVE));
    when(assets.save(any(LongTermAssetEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.saveCashReserve(
        1L, 1L, "Edited reserve", CurrencyType.PLN, new BigDecimal("950"), null, "edited", DATE);

    assertEquals(LocalDate.of(2020, 2, 3), reserve.getAcquisitionDate());
    assertEquals(new BigDecimal("700"), reserve.getAcquisitionValue());
    assertEquals(LocalDate.of(2026, 1, 15), reserve.getArchivedAt());
    assertEquals(false, reserve.isActive());
    assertEquals(new BigDecimal("950"), reserve.getCurrentValue());
    verify(lifecycle, never()).reactivate(anyLong(), anyLong());
  }

  private static LongTermAssetRentalContractEntity contract(LocalDate start, LocalDate end) {
    LongTermAssetRentalContractEntity contract = new LongTermAssetRentalContractEntity();
    contract.setAssetId(1L);
    contract.setStartDate(start);
    contract.setEndDate(end);
    LongTermAssetRentalContractTermEntity rent = new LongTermAssetRentalContractTermEntity();
    rent.setContract(contract);
    rent.setType(CashFlowType.RENT);
    rent.setAmount(new BigDecimal("1000"));
    rent.setFrequency(Frequency.MONTHLY);
    contract.getTerms().add(rent);
    return contract;
  }

  @Test
  void rentalSummaryAnnualizesIncomeAndExpensesAndAppliesTax() {
    LongTermAssetEntity a = asset(LongTermAssetType.REAL_ESTATE, "710000");
    a.setTaxBase(new BigDecimal("2900"));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L))
        .thenReturn(
            List.of(
                flow(CashFlowType.RENT, "2650", Frequency.MONTHLY),
                flow(CashFlowType.PARKING_RENT, "250", Frequency.MONTHLY),
                flow(CashFlowType.ADMIN_FEE, "687", Frequency.MONTHLY),
                flow(CashFlowType.PROPERTY_TAX, "320", Frequency.ANNUAL)));
    var s = service.summary(a, DATE);
    assertEquals(new BigDecimal("34800"), s.annualEconomics().grossAnnualIncome());
    assertEquals(new BigDecimal("320"), s.annualEconomics().annualExpenses());
    assertEquals(new BigDecimal("34480"), s.annualEconomics().netAnnualIncomeBeforeTax());
    assertEquals(new BigDecimal("2958.000"), s.annualEconomics().annualTax());
    assertEquals(new BigDecimal("31522.000"), s.annualEconomics().netAnnualIncomeAfterTax());
  }

  @Test
  void clampsLongTermAssetCalculationsToTheGlobalHistoryStart() {
    LongTermAssetEntity reserve = asset(LongTermAssetType.CASH_RESERVE, "1000");
    LongTermAssetValuationPeriodEntity valuation = new LongTermAssetValuationPeriodEntity();
    valuation.setAssetId(1L);
    valuation.setValidFrom(LocalDate.of(2025, 1, 1));
    valuation.setExpectedAnnualGrowthRate(new BigDecimal("0.05"));
    when(valuations.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(valuation));

    LongTermAssetSummary result = service.summary(reserve, LocalDate.of(2024, 12, 31));

    assertEquals(new BigDecimal("0.05"), result.currentAnnualRate());
  }

  @Test
  void realEstateMonthlyOperatingProfitUsesOnlyCurrentMonthlyOperatingRows() {
    LongTermAssetEntity a = asset(LongTermAssetType.REAL_ESTATE, "780000");
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L))
        .thenReturn(
            List.of(
                flow(CashFlowType.RENT, "2650", Frequency.MONTHLY),
                    flow(CashFlowType.PARKING_RENT, "250", Frequency.MONTHLY),
                flow(CashFlowType.ADMIN_FEE, "687", Frequency.MONTHLY),
                    flow(CashFlowType.UTILITIES, "100", Frequency.MONTHLY),
                flow(CashFlowType.PROPERTY_TAX, "320", Frequency.ANNUAL),
                    flow(CashFlowType.INSURANCE, "200", Frequency.ANNUAL)));
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(a));
    var s = service.list(1L, DATE).getFirst();
    assertEquals(new BigDecimal("2900"), s.realEstatePlanning().monthlyIncome());
    assertEquals(
        new BigDecimal("2856.666666666666666667"), s.realEstatePlanning().netMonthlyIncome());
  }

  @Test
  void explicitRealEstateTaxBaseIsIndependentFromGrossIncome() {
    LongTermAssetEntity a = asset(LongTermAssetType.REAL_ESTATE, "780000");
    a.setTaxBase(new BigDecimal("2800"));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L))
        .thenReturn(List.of(flow(CashFlowType.RENT, "36000", Frequency.ANNUAL)));
    var s = service.summary(a, DATE);
    assertEquals(new BigDecimal("2856.000"), s.estimatedAnnualTax());
    assertEquals(new BigDecimal("33144.000"), s.netAnnualIncomeAfterTax());
  }

  @Test
  void sanitizedFivePropertyPortfolioReconcilesRentalEconomics() {
    List<LongTermAssetEntity> properties =
        List.of(
            property(1L, "Property A", "710000", "2650", "250", "687", "320", "200"),
            property(2L, "Property B", "710000", "2800", "0", "650", "330", "240"),
            property(3L, "Property C", "700000", "2600", "0", "380", "330", "200"),
            property(4L, "Property D", "780000", "2900", "100", "400", "340", "250"),
            property(5L, "Property E", "750000", "2900", "150", "490", "320", "240"));
    for (LongTermAssetEntity property : properties) {
      when(cashFlows.findAllByAssetIdOrderByValidFrom(property.getId()))
          .thenReturn(propertyFlows(property));
    }

    List<LongTermAssetSummary> summaries =
        properties.stream().map(property -> service.summary(property, DATE)).toList();
    assertEquals(
        List.of("34800", "33600", "31200", "36000", "36600"),
        summaries.stream().map(s -> s.grossAnnualIncome().toPlainString()).toList());
    assertEquals(
        List.of("520", "570", "530", "590", "560"),
        summaries.stream().map(s -> s.annualExpenses().toPlainString()).toList());
    assertEquals(
        List.of("31322.000", "30174.000", "28018.000", "32350.000", "32929.000"),
        summaries.stream().map(s -> s.netAnnualIncomeAfterTax().toPlainString()).toList());
    assertEquals(
        new BigDecimal("3650000"),
        summaries.stream()
            .map(LongTermAssetSummary::currentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    assertEquals(
        new BigDecimal("172200"),
        summaries.stream()
            .map(LongTermAssetSummary::grossAnnualIncome)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    assertEquals(
        new BigDecimal("2770"),
        summaries.stream()
            .map(LongTermAssetSummary::annualExpenses)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    assertEquals(
        new BigDecimal("14637.000"),
        summaries.stream()
            .map(LongTermAssetSummary::estimatedAnnualTax)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    assertEquals(
        new BigDecimal("154793.000"),
        summaries.stream()
            .map(LongTermAssetSummary::netAnnualIncomeAfterTax)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    assertEquals(
        new BigDecimal("0.04717808"),
        LongTermAssetCalculator.ratio(new BigDecimal("172200"), new BigDecimal("3650000")));
    assertEquals(
        new BigDecimal("0.04240904"),
        LongTermAssetCalculator.ratio(new BigDecimal("154793"), new BigDecimal("3650000")));
  }

  @Test
  void bondSummaryUsesSelectedRateAndTax() {
    LongTermAssetEntity a = asset(LongTermAssetType.BOND, "100000");
    LongTermAssetBondDetailsEntity d = new LongTermAssetBondDetailsEntity();
    d.setTaxRate(new BigDecimal("0.19"));
    d.setMaturityDate(DATE.plusYears(2));
    d.setInterestTreatment(InterestTreatment.PAY_OUT);
    LongTermAssetBondRatePeriodEntity r = new LongTermAssetBondRatePeriodEntity();
    r.setValidFrom(DATE.minusDays(1));
    r.setAnnualInterestRate(new BigDecimal("0.06"));
    when(bonds.findById(1L)).thenReturn(Optional.of(d));
    when(bondRates.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(r));
    var s = service.summary(a, DATE);
    assertEquals(new BigDecimal("6000.00"), s.annualEconomics().grossAnnualIncome());
    assertEquals(new BigDecimal("1140.0000"), s.annualEconomics().annualTax());
    assertEquals(new BigDecimal("4860.0000"), s.bondPlanning().netInterest());
    assertEquals(DATE.plusYears(2), s.bondPlanning().maturityDate());
  }

  @Test
  void projectionInputsCarryBondRatePeriodsIntoFuturePlanningState() {
    LongTermAssetEntity bond = asset(LongTermAssetType.BOND, "900000");
    LongTermAssetBondDetailsEntity details = new LongTermAssetBondDetailsEntity();
    details.setTaxRate(new BigDecimal("0.19"));
    details.setMaturityDate(LocalDate.of(2028, 12, 31));
    details.setInterestTreatment(InterestTreatment.PAY_OUT);
    LongTermAssetBondRatePeriodEntity rate = new LongTermAssetBondRatePeriodEntity();
    rate.setValidFrom(LocalDate.of(2025, 1, 1));
    rate.setValidTo(LocalDate.of(2028, 12, 31));
    rate.setAnnualInterestRate(new BigDecimal("0.053333333333333333"));
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(bond));
    when(valuations.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of());
    when(bondRates.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(rate));
    when(bonds.findById(1L)).thenReturn(Optional.of(details));

    var input = service.projectionInputs(1L, DATE).getFirst();

    assertEquals(LongTermAssetType.BOND, input.type());
    assertEquals(new BigDecimal("900000"), input.currentValue());
    assertEquals(1, input.periods().size());
    assertEquals(
        new BigDecimal("0.053333333333333333"), input.periods().getFirst().annualReturnRate());
    assertEquals(LocalDate.of(2028, 12, 31), input.periods().getFirst().validTo());
    assertEquals(InterestTreatment.PAY_OUT, input.interestTreatment());
  }

  @Test
  void archivedAssetsAreExcludedFromList() {
    LongTermAssetEntity a = asset(LongTermAssetType.OTHER, "10");
    a.setActive(false);
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(a));
    assertEquals(0, service.list(1L, DATE).size());
  }

  @Test
  void bondRatePeriodSelectionAndOverlapArePortfolioScoped() {
    LongTermAssetEntity a = asset(LongTermAssetType.BOND, "100");
    LongTermAssetBondRatePeriodEntity existing = new LongTermAssetBondRatePeriodEntity();
    existing.setValidFrom(DATE);
    existing.setAnnualInterestRate(new BigDecimal("0.06"));
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(a));
    when(bondRates.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(existing));
    LongTermAssetBondRatePeriodEntity overlapping = new LongTermAssetBondRatePeriodEntity();
    overlapping.setValidFrom(DATE.plusDays(1));
    overlapping.setAnnualInterestRate(new BigDecimal("0.05"));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> service.addBondRatePeriod(1L, 1L, overlapping));
  }

  @Test
  void valuationPeriodCanBeCorrectedAndDeletedWithoutOverlappingItself() {
    LongTermAssetEntity asset = asset(LongTermAssetType.REAL_ESTATE, "100");
    LongTermAssetValuationPeriodEntity existing = new LongTermAssetValuationPeriodEntity();
    existing.setId(7L);
    existing.setAssetId(1L);
    existing.setValidFrom(DATE);
    existing.setExpectedAnnualGrowthRate(new BigDecimal("0.03"));
    LongTermAssetValuationPeriodEntity replacement = new LongTermAssetValuationPeriodEntity();
    replacement.setValidFrom(DATE.plusDays(1));
    replacement.setExpectedAnnualGrowthRate(new BigDecimal("0.04"));
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(asset));
    when(valuations.findById(7L)).thenReturn(Optional.of(existing));
    when(valuations.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(existing));

    service.updateValuationPeriod(1L, 1L, 7L, replacement);
    service.deleteValuationPeriod(1L, 1L, 7L);

    assertEquals(DATE.plusDays(1), existing.getValidFrom());
    assertEquals(new BigDecimal("0.04"), existing.getExpectedAnnualGrowthRate());
    verify(valuations).save(existing);
    verify(valuations).delete(existing);
  }

  @Test
  void bondRatePeriodCanBeCorrectedAndDeletedWithoutOverlappingItself() {
    LongTermAssetEntity asset = asset(LongTermAssetType.BOND, "100");
    LongTermAssetBondRatePeriodEntity existing = new LongTermAssetBondRatePeriodEntity();
    existing.setId(8L);
    existing.setAssetId(1L);
    existing.setValidFrom(DATE);
    existing.setAnnualInterestRate(new BigDecimal("0.05"));
    LongTermAssetBondRatePeriodEntity replacement = new LongTermAssetBondRatePeriodEntity();
    replacement.setValidFrom(DATE.plusDays(1));
    replacement.setAnnualInterestRate(new BigDecimal("0.06"));
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(asset));
    when(bondRates.findById(8L)).thenReturn(Optional.of(existing));
    when(bondRates.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(existing));

    service.updateBondRatePeriod(1L, 1L, 8L, replacement);
    service.deleteBondRatePeriod(1L, 1L, 8L);

    assertEquals(DATE.plusDays(1), existing.getValidFrom());
    assertEquals(new BigDecimal("0.06"), existing.getAnnualInterestRate());
    verify(bondRates).save(existing);
    verify(bondRates).delete(existing);
  }

  @Test
  void rentalTaxPolicyCorrectionAndDeleteRequirePortfolioOwnership() {
    RentalTaxPolicyEntity policy = new RentalTaxPolicyEntity();
    policy.setId(9L);
    policy.setPortfolioId(1L);
    policy.setValidFrom(DATE);
    policy.setRate(new BigDecimal("0.085"));
    when(taxPolicies.findById(9L)).thenReturn(Optional.of(policy));
    when(taxPolicies.findAllByPortfolioIdOrderByValidFrom(1L)).thenReturn(List.of(policy));

    service.saveRentalTaxPolicy(1L, policy);
    service.deleteRentalTaxPolicy(1L, 9L);

    verify(taxPolicies).save(policy);
    verify(taxPolicies).delete(policy);
    assertThrows(NoSuchElementException.class, () -> service.deleteRentalTaxPolicy(2L, 9L));
  }

  @Test
  void bondMaturityBeforeAcquisitionIsRejected() {
    LongTermAssetEntity a = asset(LongTermAssetType.BOND, "100");
    a.setAcquisitionDate(DATE);
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(a));
    LongTermAssetBondDetailsEntity d = new LongTermAssetBondDetailsEntity();
    d.setMaturityDate(DATE.minusDays(1));
    assertThrows(IllegalArgumentException.class, () -> service.saveBondDetails(1L, 1L, d));
  }

  @Test
  void simpleBondUpdatePreservesExplicitRedemption() {
    LongTermAssetEntity bond = asset(LongTermAssetType.BOND, "100");
    bond.setAcquisitionDate(DATE);
    LongTermAssetBondDetailsEntity details = new LongTermAssetBondDetailsEntity();
    details.setAssetId(1L);
    details.setMaturityDate(DATE.plusYears(2));
    details.setInterestTreatment(InterestTreatment.PAY_OUT);
    details.setTaxRate(new BigDecimal("0.19"));
    details.setRedemptionValue(new BigDecimal("125"));
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(bond));
    when(bonds.findById(1L)).thenReturn(Optional.of(details));
    when(bondRates.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of());

    service.saveSimpleBond(
        1L, 1L, new BigDecimal("0.05"), DATE.plusYears(2), InterestTreatment.PAY_OUT);

    assertEquals(new BigDecimal("125"), details.getRedemptionValue());
  }

  @Test
  void aggregateConvertsForeignCurrencyToPortfolioBase() {
    LongTermAssetEntity pln = asset(LongTermAssetType.OTHER, "100");
    LongTermAssetEntity usd = asset(LongTermAssetType.OTHER, "50");
    usd.setId(2L);
    usd.setCurrency(CurrencyType.USD);
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(pln, usd));
    when(portfolioContextReader.findById(1L))
        .thenReturn(Optional.of(new PortfolioContext(1L, CurrencyType.PLN)));
    when(currencyRates.convertToBaseCurrency(
            any(BigDecimal.class),
            org.mockito.ArgumentMatchers.eq(CurrencyType.PLN),
            org.mockito.ArgumentMatchers.eq(CurrencyType.USD),
            org.mockito.ArgumentMatchers.eq(DATE)))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0, BigDecimal.class).multiply(new BigDecimal("4")));
    var total = service.aggregate(1L, DATE);
    assertEquals(CurrencyType.PLN, total.currency());
    assertEquals(new BigDecimal("300"), total.totalCurrentValue());
  }

  @Test
  void groupsAssetsInDisplayOrderAndUsesWeightedGrossYield() {
    List<LongTermAssetSummary> rows =
        List.of(
            row(1L, "Apartment", LongTermAssetType.REAL_ESTATE, "100", "10", null),
            row(2L, "Bond late", LongTermAssetType.BOND, "300", "30", DATE.plusYears(3)),
            row(3L, "Bond early", LongTermAssetType.BOND, "200", "10", DATE.plusYears(1)),
            row(4L, "Deposit", LongTermAssetType.DEPOSIT, "50", "5", null));
    var groups = service.groupSummaries(rows, CurrencyType.PLN, DATE);
    assertEquals(
        List.of("REAL_ESTATE", "BOND", "CASH_RESERVE", "OTHER"),
        groups.stream().map(LongTermAssetQueryService.AssetGroupSummary::key).toList());
    assertEquals(
        List.of("Bond early", "Bond late"),
        groups.get(1).assets().stream().map(LongTermAssetSummary::name).toList());
    assertEquals(new BigDecimal("500"), groups.get(1).totalValue());
    assertEquals(new BigDecimal("40"), groups.get(1).grossAnnualIncome());
    assertEquals(0, groups.get(1).grossYield().compareTo(new BigDecimal("0.08")));
    assertEquals(1, groups.get(3).assets().size());
  }

  @Test
  void realEstateGroupMonthlyProfitIsSumOfPropertyValues() {
    var first = row(1L, "A", LongTermAssetType.REAL_ESTATE, "100", "10", null);
    var second =
        new LongTermAssetSummary(
            2L,
            "B",
            LongTermAssetType.REAL_ESTATE,
            CurrencyType.PLN,
            new BigDecimal("200"),
            new BigDecimal("20"),
            BigDecimal.ZERO,
            new BigDecimal("20"),
            BigDecimal.ZERO,
            new BigDecimal("20"),
            new BigDecimal("0.1"),
            BigDecimal.ZERO,
            new BigDecimal("0.1"),
            null,
            BigDecimal.ZERO,
            new RealEstatePlanningSummary(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("2113"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO));
    var groups = service.groupSummaries(List.of(first, second), CurrencyType.PLN, DATE);
    assertEquals(new BigDecimal("2113"), groups.get(0).totalPaymentMonthly());
  }

  @Test
  void realEstateGroupMonthlyIncomeIsSumOfNetMonthlyIncome() {
    var first =
        new LongTermAssetSummary(
            1L,
            "A",
            LongTermAssetType.REAL_ESTATE,
            CurrencyType.PLN,
            new BigDecimal("100"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            BigDecimal.ZERO,
            new RealEstatePlanningSummary(
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("3000"),
                new BigDecimal("100"),
                BigDecimal.ZERO));
    var second =
        new LongTermAssetSummary(
            2L,
            "B",
            LongTermAssetType.REAL_ESTATE,
            CurrencyType.PLN,
            new BigDecimal("200"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            BigDecimal.ZERO,
            new RealEstatePlanningSummary(
                new BigDecimal("2500"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("2500"),
                new BigDecimal("200"),
                BigDecimal.ZERO));
    var group = service.groupSummaries(List.of(first, second), CurrencyType.PLN, DATE).getFirst();
    assertEquals(new BigDecimal("5200"), group.netMonthlyIncome());
    assertEquals(new BigDecimal("300"), group.realEstatePlanning().monthlyReduce());
    assertEquals(new BigDecimal("3500"), group.realEstatePlanning().taxBase());
  }

  @Test
  void groupsTreatMissingOptionalRealEstateTaxBaseAsZero() {
    var row =
        new LongTermAssetSummary(
            1L,
            "Apartment",
            LongTermAssetType.REAL_ESTATE,
            CurrencyType.PLN,
            new BigDecimal("100"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            BigDecimal.ZERO,
            new RealEstatePlanningSummary(
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO));

    var group = service.groupSummaries(List.of(row), CurrencyType.PLN, DATE).getFirst();

    assertEquals(BigDecimal.ZERO, group.realEstatePlanning().totalPaymentMonthly());
  }

  @Test
  void realEstateGroupYieldUsesPlnRowTotals() {
    var rows =
        List.of(
            new LongTermAssetSummary(
                1L,
                "A",
                LongTermAssetType.REAL_ESTATE,
                CurrencyType.PLN,
                new BigDecimal("710000"),
                new BigDecimal("34800"),
                BigDecimal.ZERO,
                new BigDecimal("34800"),
                BigDecimal.ZERO,
                new BigDecimal("34800"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                new RealEstatePlanningSummary(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO)),
            new LongTermAssetSummary(
                2L,
                "B",
                LongTermAssetType.REAL_ESTATE,
                CurrencyType.PLN,
                new BigDecimal("2940000"),
                new BigDecimal("138720"),
                BigDecimal.ZERO,
                new BigDecimal("138720"),
                BigDecimal.ZERO,
                new BigDecimal("138720"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                new RealEstatePlanningSummary(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO)));
    var group = service.groupSummaries(rows, CurrencyType.PLN, DATE).getFirst();
    assertEquals(new BigDecimal("3650000"), group.totalValue());
    assertEquals(new BigDecimal("173520"), group.grossAnnualIncome());
    assertEquals(0, group.grossYield().compareTo(new BigDecimal("0.047539726027")));
  }

  @Test
  void realEstatePlanningColumnsUseMonthlyTurnoverIncomeAndAllocatedReduce() {
    LongTermAssetEntity first = asset(LongTermAssetType.REAL_ESTATE, "780000");
    first.setTaxBase(new BigDecimal("2800"));
    LongTermAssetEntity second = asset(LongTermAssetType.REAL_ESTATE, "710000");
    second.setId(2L);
    second.setTaxBase(new BigDecimal("2500"));
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(first, second));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L))
        .thenReturn(
            List.of(
                flow(CashFlowType.RENT, "2650", Frequency.MONTHLY),
                flow(CashFlowType.PARKING_RENT, "250", Frequency.MONTHLY),
                flow(CashFlowType.ADMIN_FEE, "687", Frequency.MONTHLY),
                flow(CashFlowType.PROPERTY_TAX, "320", Frequency.ANNUAL),
                flow(CashFlowType.INSURANCE, "200", Frequency.ANNUAL)));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(2L))
        .thenReturn(
            List.of(
                flow(CashFlowType.RENT, "2800", Frequency.MONTHLY),
                flow(CashFlowType.ADMIN_FEE, "650", Frequency.MONTHLY),
                flow(CashFlowType.PROPERTY_TAX, "330", Frequency.ANNUAL),
                flow(CashFlowType.INSURANCE, "240", Frequency.ANNUAL)));
    var rows = service.list(1L, DATE);
    var firstRow = rows.stream().filter(r -> r.id().equals(1L)).findFirst().orElseThrow();
    assertEquals(new BigDecimal("3587"), firstRow.realEstatePlanning().totalPaymentMonthly());
    assertEquals(new BigDecimal("2900"), firstRow.realEstatePlanning().monthlyIncome());
    BigDecimal expectedReduce =
        new BigDecimal("520")
            .add(
                new BigDecimal("2800")
                    .multiply(new BigDecimal("12"))
                    .multiply(new BigDecimal("0.085")))
            .divide(new BigDecimal("12"), 18, java.math.RoundingMode.HALF_UP);
    assertEquals(0, firstRow.realEstatePlanning().monthlyReduce().compareTo(expectedReduce));
    assertEquals(
        0,
        firstRow
            .realEstatePlanning()
            .incomeYield()
            .compareTo(
                new BigDecimal("2900")
                    .subtract(expectedReduce)
                    .multiply(new BigDecimal("12"))
                    .divide(new BigDecimal("780000"), 12, java.math.RoundingMode.HALF_UP)));
  }

  @Test
  void totalPaymentIncludesUtilitiesButExcludesOtherCashFlows() {
    LongTermAssetEntity property = asset(LongTermAssetType.REAL_ESTATE, "780000");
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(property));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L))
        .thenReturn(
            List.of(
                flow(CashFlowType.RENT, "2650", Frequency.MONTHLY),
                    flow(CashFlowType.PARKING_RENT, "250", Frequency.MONTHLY),
                flow(CashFlowType.ADMIN_FEE, "687", Frequency.MONTHLY),
                    flow(CashFlowType.UTILITIES, "100", Frequency.MONTHLY),
                flow(CashFlowType.OTHER_INCOME, "300", Frequency.MONTHLY),
                    flow(CashFlowType.OTHER_EXPENSE, "400", Frequency.MONTHLY),
                flow(CashFlowType.PROPERTY_TAX, "320", Frequency.ANNUAL),
                    flow(CashFlowType.INSURANCE, "200", Frequency.ANNUAL)));

    var summary = service.list(1L, DATE).getFirst().realEstatePlanning();

    assertEquals(new BigDecimal("3687"), summary.totalPaymentMonthly());
  }

  @Test
  void updatingTaxBasePersistsAndRecalculatesRealEstateMetrics() {
    LongTermAssetEntity property = asset(LongTermAssetType.REAL_ESTATE, "780000");
    property.setTaxBase(new BigDecimal("2000"));
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(property));
    when(assets.save(property)).thenReturn(property);
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(property));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L))
        .thenReturn(
            List.of(
                flow(CashFlowType.RENT, "2650", Frequency.MONTHLY),
                flow(CashFlowType.PROPERTY_TAX, "320", Frequency.ANNUAL),
                flow(CashFlowType.INSURANCE, "200", Frequency.ANNUAL)));

    service.updateTaxBase(1L, 1L, new BigDecimal("36000"));
    var summary = service.list(1L, DATE).getFirst().realEstatePlanning();
    BigDecimal expectedReduce =
        new BigDecimal("520")
            .add(
                new BigDecimal("36000")
                    .multiply(new BigDecimal("12"))
                    .multiply(new BigDecimal("0.085")))
            .divide(BigDecimal.valueOf(12), 18, java.math.RoundingMode.HALF_UP);

    assertEquals(new BigDecimal("36000"), property.getTaxBase());
    assertEquals(0, summary.monthlyReduce().compareTo(expectedReduce));
    assertEquals(
        0,
        summary
            .incomeYield()
            .compareTo(
                new BigDecimal("2650")
                    .subtract(expectedReduce)
                    .multiply(BigDecimal.valueOf(12))
                    .divide(new BigDecimal("780000"), 12, java.math.RoundingMode.HALF_UP)));
    verify(assets).save(property);
  }

  @Test
  void compactRealEstateEntryDoesNotWriteLegacyCashFlows() {
    when(assets.save(any()))
        .thenAnswer(
            invocation -> {
              LongTermAssetEntity saved = invocation.getArgument(0);
              saved.setId(9L);
              return saved;
            });
    when(valuations.findAllByAssetIdOrderByValidFrom(9L)).thenReturn(List.of());
    LongTermAssetEntity saved =
        service.saveRealEstateEntry(
            1L,
            null,
            new RealEstateEntryModel(
                "Apartment",
                CurrencyType.PLN,
                DATE.minusYears(2),
                new BigDecimal("600000"),
                new BigDecimal("710000"),
                new BigDecimal("2650"),
                new BigDecimal("250"),
                new BigDecimal("687"),
                new BigDecimal("50"),
                new BigDecimal("320"),
                new BigDecimal("200"),
                DATE,
                new BigDecimal("0.02"),
                "note"));
    assertEquals(9L, saved.getId());
    verify(cashFlows, never()).save(any(LongTermAssetCashFlowEntity.class));
    verify(valuations).save(any(LongTermAssetValuationPeriodEntity.class));
  }

  @Test
  void changingCurrentCashFlowClosesHistoryAndCreatesNewEffectivePeriod() {
    LongTermAssetEntity asset = asset(LongTermAssetType.REAL_ESTATE, "710000");
    LongTermAssetCashFlowEntity old = flow(CashFlowType.RENT, "2900", Frequency.MONTHLY);
    old.setId(7L);
    old.setValidFrom(DATE.minusYears(1));
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(asset));
    when(cashFlows.findById(7L)).thenReturn(Optional.of(old));
    when(cashFlows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var replacement =
        service.changeCashFlow(1L, 1L, 7L, new BigDecimal("3100"), Frequency.MONTHLY, DATE);
    assertEquals(DATE.minusDays(1), old.getValidTo());
    assertEquals(DATE, replacement.getValidFrom());
    assertEquals(new BigDecimal("3100"), replacement.getAmount());
    verify(cashFlows, times(2)).save(any(LongTermAssetCashFlowEntity.class));
  }

  @Test
  void realEstateGroupExposesMonthlyRentTaxAcrossProperties() {
    LongTermAssetEntity first = asset(LongTermAssetType.REAL_ESTATE, "780000");
    first.setTaxBase(new BigDecimal("1800"));
    LongTermAssetEntity second = asset(LongTermAssetType.REAL_ESTATE, "710000");
    second.setId(2L);
    second.setTaxBase(new BigDecimal("1500"));
    LongTermAssetEntity third = asset(LongTermAssetType.REAL_ESTATE, "500000");
    third.setId(3L);
    third.setTaxBase(null);
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(first, second, third));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of());
    when(cashFlows.findAllByAssetIdOrderByValidFrom(2L)).thenReturn(List.of());
    when(cashFlows.findAllByAssetIdOrderByValidFrom(3L)).thenReturn(List.of());

    var group = service.grouped(1L, DATE).getFirst();

    assertEquals(
        new BigDecimal("280.500000000000000000"), group.realEstatePlanning().monthlyRentTax());
  }

  @Test
  void availableEconomicsExcludeCurrentTypesPerPropertyInBusinessOrder() {
    LongTermAssetCashFlowEntity rent = flow(CashFlowType.RENT, "2650", Frequency.MONTHLY);
    rent.setId(1L);
    LongTermAssetCashFlowEntity admin = flow(CashFlowType.ADMIN_FEE, "500", Frequency.MONTHLY);
    admin.setId(2L);
    when(assets.findByIdAndPortfolioId(1L, 1L))
        .thenReturn(Optional.of(asset(LongTermAssetType.REAL_ESTATE, "780000")));
    when(assets.findByIdAndPortfolioId(2L, 1L))
        .thenReturn(Optional.of(asset(LongTermAssetType.REAL_ESTATE, "710000")));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(rent, admin));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(2L)).thenReturn(List.of());

    assertEquals(
        List.of(
            CashFlowType.PARKING_RENT,
            CashFlowType.UTILITIES,
            CashFlowType.PROPERTY_TAX,
            CashFlowType.INSURANCE,
            CashFlowType.OTHER_INCOME,
            CashFlowType.OTHER_EXPENSE),
        service.availableCashFlowTypes(1L, 1L, DATE));
    assertEquals(
        List.of(
            CashFlowType.RENT,
            CashFlowType.PARKING_RENT,
            CashFlowType.ADMIN_FEE,
            CashFlowType.UTILITIES,
            CashFlowType.PROPERTY_TAX,
            CashFlowType.INSURANCE,
            CashFlowType.OTHER_INCOME,
            CashFlowType.OTHER_EXPENSE),
        service.availableCashFlowTypes(1L, 2L, DATE));
  }

  @Test
  void duplicateCurrentEconomicsSubmissionIsRejected() {
    LongTermAssetCashFlowEntity existing = flow(CashFlowType.RENT, "2650", Frequency.MONTHLY);
    existing.setId(1L);
    when(assets.findByIdAndPortfolioId(1L, 1L))
        .thenReturn(Optional.of(asset(LongTermAssetType.REAL_ESTATE, "780000")));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(existing));

    LongTermAssetCashFlowEntity duplicate = flow(CashFlowType.RENT, "2800", Frequency.MONTHLY);
    assertThrows(
        IllegalArgumentException.class, () -> service.addCashFlow(1L, 1L, duplicate, DATE));
    verify(cashFlows, never()).save(any());
  }

  @Test
  void savingRentalPeriodPropagatesOnlyToCurrentEconomics() {
    LongTermAssetCashFlowEntity rent = flow(CashFlowType.RENT, "2650", Frequency.MONTHLY);
    rent.setId(1L);
    LongTermAssetCashFlowEntity parking = flow(CashFlowType.PARKING_RENT, "250", Frequency.MONTHLY);
    parking.setId(2L);
    when(assets.findByIdAndPortfolioId(1L, 1L))
        .thenReturn(Optional.of(asset(LongTermAssetType.REAL_ESTATE, "780000")));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(rent, parking));
    when(cashFlows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.saveRentalPeriod(1L, 1L, DATE, DATE.plusMonths(6), DATE);

    assertEquals(DATE, rent.getValidFrom());
    assertEquals(DATE.plusMonths(6), rent.getValidTo());
    assertEquals(DATE, parking.getValidFrom());
    assertEquals(DATE.plusMonths(6), parking.getValidTo());
  }

  @Test
  void savingUnchangedRentalPeriodUpdatesExistingRowsWithoutDuplicates() {
    LongTermAssetCashFlowEntity rent = flow(CashFlowType.RENT, "2900", Frequency.MONTHLY);
    rent.setId(14L);
    rent.setValidFrom(LocalDate.of(2025, 1, 1));
    rent.setValidTo(LocalDate.of(2026, 12, 30));
    when(assets.findByIdAndPortfolioId(1L, 1L))
        .thenReturn(Optional.of(asset(LongTermAssetType.REAL_ESTATE, "780000")));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(rent));
    when(cashFlows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.saveRentalPeriod(1L, 1L, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 30), DATE);

    assertEquals(14L, rent.getId());
    verify(cashFlows).save(rent);
    verify(cashFlows, times(1)).save(any());
  }

  @Test
  void changingCurrentRentalPeriodRetainsExistingIdAndValues() {
    LongTermAssetCashFlowEntity rent = flow(CashFlowType.RENT, "2900", Frequency.MONTHLY);
    rent.setId(14L);
    rent.setValidFrom(LocalDate.of(2025, 1, 1));
    rent.setValidTo(LocalDate.of(2026, 12, 30));
    when(assets.findByIdAndPortfolioId(1L, 1L))
        .thenReturn(Optional.of(asset(LongTermAssetType.REAL_ESTATE, "780000")));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(rent));
    when(cashFlows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.saveRentalPeriod(1L, 1L, LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 31), DATE);

    assertEquals(14L, rent.getId());
    assertEquals(new BigDecimal("2900"), rent.getAmount());
    assertEquals(Frequency.MONTHLY, rent.getFrequency());
    assertEquals(LocalDate.of(2027, 1, 31), rent.getValidTo());
    verify(cashFlows).save(rent);
  }

  @Test
  void changingCurrentRentalPeriodLeavesHistoricalRowUntouched() {
    LongTermAssetCashFlowEntity historical = flow(CashFlowType.RENT, "2600", Frequency.MONTHLY);
    historical.setId(11L);
    historical.setValidFrom(LocalDate.of(2025, 1, 1));
    historical.setValidTo(LocalDate.of(2026, 8, 11));
    LongTermAssetCashFlowEntity current = flow(CashFlowType.RENT, "2600", Frequency.MONTHLY);
    current.setId(25L);
    current.setValidFrom(LocalDate.of(2026, 8, 12));
    current.setValidTo(null);
    when(assets.findByIdAndPortfolioId(1L, 1L))
        .thenReturn(Optional.of(asset(LongTermAssetType.REAL_ESTATE, "780000")));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(historical, current));
    when(cashFlows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.saveRentalPeriod(1L, 1L, LocalDate.of(2026, 8, 12), null, LocalDate.of(2026, 8, 19));

    assertEquals(LocalDate.of(2025, 1, 1), historical.getValidFrom());
    assertEquals(LocalDate.of(2026, 8, 11), historical.getValidTo());
    assertEquals(25L, current.getId());
    verify(cashFlows).save(current);
    verify(cashFlows, never()).save(historical);
  }

  @Test
  void changingOpenCurrentRentalPeriodToAnEndDateSucceeds() {
    LongTermAssetCashFlowEntity current = flow(CashFlowType.RENT, "2600", Frequency.MONTHLY);
    current.setId(25L);
    current.setValidFrom(LocalDate.of(2026, 1, 1));
    current.setValidTo(null);
    when(assets.findByIdAndPortfolioId(1L, 1L))
        .thenReturn(Optional.of(asset(LongTermAssetType.REAL_ESTATE, "780000")));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(current));
    when(cashFlows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.saveRentalPeriod(1L, 1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), DATE);

    assertEquals(LocalDate.of(2026, 12, 31), current.getValidTo());
    verify(cashFlows).save(current);
  }

  @Test
  void currentRentalPeriodMayEndTheDayBeforeAnExplicitFutureReplacement() {
    LongTermAssetCashFlowEntity current = flow(CashFlowType.RENT, "2600", Frequency.MONTHLY);
    current.setId(25L);
    current.setValidFrom(LocalDate.of(2026, 7, 1));
    current.setValidTo(null);
    LongTermAssetCashFlowEntity future = flow(CashFlowType.RENT, "2800", Frequency.MONTHLY);
    future.setId(31L);
    future.setValidFrom(LocalDate.of(2027, 1, 1));
    future.setValidTo(null);
    when(assets.findByIdAndPortfolioId(1L, 1L))
        .thenReturn(Optional.of(asset(LongTermAssetType.REAL_ESTATE, "780000")));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(current, future));
    when(cashFlows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.saveRentalPeriod(
        1L, 1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 8, 1));

    assertEquals(LocalDate.of(2026, 12, 31), current.getValidTo());
    assertEquals(LocalDate.of(2027, 1, 1), future.getValidFrom());
    verify(cashFlows).save(current);
    verify(cashFlows, never()).save(future);
  }

  @Test
  void currentRentalPeriodCannotOverlapAnExplicitFutureReplacement() {
    LongTermAssetCashFlowEntity current = flow(CashFlowType.RENT, "2600", Frequency.MONTHLY);
    current.setId(25L);
    current.setValidFrom(LocalDate.of(2026, 7, 1));
    current.setValidTo(null);
    LongTermAssetCashFlowEntity future = flow(CashFlowType.RENT, "2800", Frequency.MONTHLY);
    future.setId(31L);
    future.setValidFrom(LocalDate.of(2027, 1, 1));
    future.setValidTo(null);
    when(assets.findByIdAndPortfolioId(1L, 1L))
        .thenReturn(Optional.of(asset(LongTermAssetType.REAL_ESTATE, "780000")));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(current, future));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.saveRentalPeriod(
                1L,
                1L,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2026, 8, 1)));

    assertNull(current.getValidTo());
    verify(cashFlows, never()).save(any());
  }

  @Test
  void changingCurrentRentalPeriodToHistoricalOverlapFailsBeforeMutation() {
    LongTermAssetCashFlowEntity historical = flow(CashFlowType.RENT, "2600", Frequency.MONTHLY);
    historical.setId(11L);
    historical.setValidFrom(LocalDate.of(2025, 1, 1));
    historical.setValidTo(LocalDate.of(2026, 8, 11));
    LongTermAssetCashFlowEntity current = flow(CashFlowType.RENT, "2600", Frequency.MONTHLY);
    current.setId(25L);
    current.setValidFrom(LocalDate.of(2026, 8, 12));
    when(assets.findByIdAndPortfolioId(1L, 1L))
        .thenReturn(Optional.of(asset(LongTermAssetType.REAL_ESTATE, "780000")));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(historical, current));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.saveRentalPeriod(
                1L, 1L, LocalDate.of(2026, 8, 10), null, LocalDate.of(2026, 8, 19)));

    assertEquals(LocalDate.of(2026, 8, 12), current.getValidFrom());
    verify(cashFlows, never()).save(any());
  }

  @Test
  void overviewExposesEndDateOnlyForCurrentRentEconomics() {
    LongTermAssetEntity property = asset(LongTermAssetType.REAL_ESTATE, "780000");
    LongTermAssetCashFlowEntity rent = flow(CashFlowType.RENT, "2650", Frequency.MONTHLY);
    rent.setValidTo(LocalDate.of(2026, 12, 31));
    LongTermAssetCashFlowEntity parking = flow(CashFlowType.PARKING_RENT, "250", Frequency.MONTHLY);
    parking.setValidTo(LocalDate.of(2027, 12, 31));
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(property));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(parking, rent));

    assertEquals(LocalDate.of(2026, 12, 31), service.list(1L, DATE).getFirst().rentEnd());
  }

  @Test
  void overviewLeavesRentEndEmptyWhenRentHasNoEndOrOnlyUnrelatedEconomicsExist() {
    LongTermAssetEntity property = asset(LongTermAssetType.REAL_ESTATE, "780000");
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(property));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L))
        .thenReturn(List.of(flow(CashFlowType.PARKING_RENT, "250", Frequency.MONTHLY)));

    assertNull(service.list(1L, DATE).getFirst().rentEnd());

    LongTermAssetCashFlowEntity rent = flow(CashFlowType.RENT, "2650", Frequency.MONTHLY);
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(rent));
    assertNull(service.list(1L, DATE).getFirst().rentEnd());
  }

  @Test
  void overviewUsesEffectiveTerminationOnlyWhileRentContractIsActive() {
    LongTermAssetEntity property = asset(LongTermAssetType.REAL_ESTATE, "780000");
    LongTermAssetRentalContractEntity contract =
        contract(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31));
    contract.setTerminatedDate(LocalDate.of(2026, 5, 15));
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(property));
    when(rentalContracts.findAllByAssetIdOrderByStartDate(1L)).thenReturn(List.of(contract));

    assertEquals(
        LocalDate.of(2026, 5, 15), service.list(1L, LocalDate.of(2026, 5, 1)).getFirst().rentEnd());
    assertNull(service.list(1L, DATE).getFirst().rentEnd());
  }

  @Test
  void changingCurrentCashFlowPersistsAndCanClearEndDate() {
    LongTermAssetEntity asset = asset(LongTermAssetType.REAL_ESTATE, "710000");
    LongTermAssetCashFlowEntity old = flow(CashFlowType.RENT, "2900", Frequency.MONTHLY);
    old.setId(7L);
    old.setValidFrom(DATE);
    old.setValidTo(DATE.plusMonths(1));
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(asset));
    when(cashFlows.findById(7L)).thenReturn(Optional.of(old));
    when(cashFlows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.changeCashFlow(1L, 1L, 7L, new BigDecimal("3100"), Frequency.MONTHLY, DATE, null);

    assertEquals(new BigDecimal("3100"), old.getAmount());
    assertNull(old.getValidTo());
    verify(cashFlows).save(old);
  }

  @Test
  void cashFlowsUseDeterministicRentalBusinessOrder() {
    LongTermAssetEntity asset = asset(LongTermAssetType.REAL_ESTATE, "710000");
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(asset));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L))
        .thenReturn(
            List.of(
                flow(CashFlowType.OTHER_EXPENSE, "1", Frequency.ANNUAL),
                flow(CashFlowType.PROPERTY_TAX, "1", Frequency.ANNUAL),
                flow(CashFlowType.UTILITIES, "1", Frequency.MONTHLY),
                flow(CashFlowType.ADMIN_FEE, "1", Frequency.MONTHLY),
                flow(CashFlowType.PARKING_RENT, "1", Frequency.MONTHLY),
                flow(CashFlowType.RENT, "1", Frequency.MONTHLY)));

    assertEquals(
        List.of(
            CashFlowType.RENT,
            CashFlowType.PARKING_RENT,
            CashFlowType.ADMIN_FEE,
            CashFlowType.UTILITIES,
            CashFlowType.PROPERTY_TAX,
            CashFlowType.OTHER_EXPENSE),
        service.cashFlows(1L, 1L).stream().map(LongTermAssetCashFlowEntity::getType).toList());
  }

  @Test
  void propertyGrowthUpdatePreservesExistingHistoricalPeriod() {
    LongTermAssetEntity asset = asset(LongTermAssetType.REAL_ESTATE, "710000");
    LongTermAssetValuationPeriodEntity old = new LongTermAssetValuationPeriodEntity();
    old.setAssetId(1L);
    old.setValidFrom(DATE.minusYears(1));
    old.setExpectedAnnualGrowthRate(new BigDecimal("0.02"));
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(asset));
    when(valuations.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(old));
    when(valuations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    service.saveExpectedPropertyGrowth(1L, 1L, new BigDecimal("0.03"), DATE);
    assertEquals(DATE.minusDays(1), old.getValidTo());
    verify(valuations, times(2)).save(any(LongTermAssetValuationPeriodEntity.class));
  }

  @Test
  void propertyGrowthUpdateAtExistingStartDateUpdatesTheOpenPeriodInPlace() {
    LongTermAssetEntity asset = asset(LongTermAssetType.REAL_ESTATE, "710000");
    LongTermAssetValuationPeriodEntity current = new LongTermAssetValuationPeriodEntity();
    current.setId(8L);
    current.setAssetId(1L);
    current.setValidFrom(DATE);
    current.setExpectedAnnualGrowthRate(new BigDecimal("0.02"));
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(asset));
    when(valuations.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(current));
    when(valuations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.saveExpectedPropertyGrowth(1L, 1L, new BigDecimal("0.03"), DATE);

    assertEquals(new BigDecimal("0.03"), current.getExpectedAnnualGrowthRate());
    assertNull(current.getValidTo());
    verify(valuations).save(current);
    verify(valuations, never()).delete(any());
  }

  @Test
  void propertyGrowthOutsideTechnicalRateRangeIsRejected() {
    LongTermAssetEntity asset = asset(LongTermAssetType.REAL_ESTATE, "710000");
    when(assets.findByIdAndPortfolioId(1L, 1L)).thenReturn(Optional.of(asset));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.saveExpectedPropertyGrowth(1L, 1L, new BigDecimal("1.01"), DATE));
    verifyNoInteractions(valuations);
  }

  @Test
  void historicalAnnualSnapshotUsesTheSameRentalEconomicsAsAssetSummary() {
    LongTermAssetEntity apartment = asset(LongTermAssetType.REAL_ESTATE, "710000");
    apartment.setTaxBase(new BigDecimal("34800"));
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(apartment));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L))
        .thenReturn(
            List.of(
                flow(CashFlowType.RENT, "2650", Frequency.MONTHLY),
                flow(CashFlowType.PARKING_RENT, "250", Frequency.MONTHLY),
                flow(CashFlowType.ADMIN_FEE, "687", Frequency.MONTHLY),
                flow(CashFlowType.PROPERTY_TAX, "320", Frequency.ANNUAL)));
    when(currencyRates.convertToBaseCurrency(
            any(BigDecimal.class),
            eq(CurrencyType.USD),
            eq(CurrencyType.PLN),
            any(LocalDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));

    BigDecimal overviewAnnualIncome =
        service
            .summary(apartment, LocalDate.of(2025, 12, 31))
            .annualEconomics()
            .netAnnualIncomeAfterTax();
    BigDecimal historicalAnnualIncome = service.historicalAnnualSnapshot(1L, 2025).rentalIncome();

    assertEquals(0, overviewAnnualIncome.compareTo(historicalAnnualIncome));
  }

  @Test
  void historicalAnnualSnapshotKeepsFutureOnlyRentalDataUnavailable() {
    LongTermAssetEntity apartment = asset(LongTermAssetType.REAL_ESTATE, "710000");
    LongTermAssetRentalContractEntity future = contract(LocalDate.of(2026, 1, 1), null);
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(apartment));
    when(rentalContracts.findAllByAssetIdOrderByStartDate(1L)).thenReturn(List.of(future));
    when(currencyRates.convertToBaseCurrency(
            any(BigDecimal.class),
            eq(CurrencyType.USD),
            eq(CurrencyType.PLN),
            any(LocalDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));

    assertNull(service.historicalAnnualSnapshot(1L, 2025).rentalIncome());
  }

  @Test
  void historicalAnnualSnapshotExposesBondPayoutIncome() {
    LongTermAssetEntity bond = asset(LongTermAssetType.BOND, "100000");
    LongTermAssetBondDetailsEntity details = new LongTermAssetBondDetailsEntity();
    details.setTaxRate(new BigDecimal("0.19"));
    details.setMaturityDate(DATE.plusYears(2));
    details.setInterestTreatment(InterestTreatment.PAY_OUT);
    LongTermAssetBondRatePeriodEntity rate = new LongTermAssetBondRatePeriodEntity();
    rate.setValidFrom(LocalDate.of(2025, 1, 1));
    rate.setAnnualInterestRate(new BigDecimal("0.06"));
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(bond));
    when(bonds.findById(1L)).thenReturn(Optional.of(details));
    when(bondRates.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(rate));
    when(currencyRates.convertToBaseCurrency(
            any(BigDecimal.class),
            eq(CurrencyType.USD),
            eq(CurrencyType.PLN),
            any(LocalDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));

    BigDecimal income = service.historicalAnnualSnapshot(1L, 2025).bondIncome();

    assertEquals(0, new BigDecimal("4860.0000").compareTo(income));
  }

  @Test
  void annualSnapshotsNormalizeNativePlnMoneyToCanonicalUsd() {
    LongTermAssetEntity apartment = asset(LongTermAssetType.REAL_ESTATE, "4000000");
    apartment.setTaxBase(BigDecimal.ZERO);
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(apartment));
    when(cashFlows.findAllByAssetIdOrderByValidFrom(1L))
        .thenReturn(List.of(flow(CashFlowType.RENT, "400000", Frequency.ANNUAL)));
    when(currencyRates.convertToBaseCurrency(
            any(BigDecimal.class),
            eq(CurrencyType.USD),
            eq(CurrencyType.PLN),
            any(LocalDate.class)))
        .thenAnswer(
            invocation -> invocation.getArgument(0, BigDecimal.class).divide(new BigDecimal("4")));

    LongTermAssetAnnualSnapshotModel current = service.currentAnnualSnapshot(1L, DATE);
    LongTermAssetAnnualSnapshotModel historical = service.historicalAnnualSnapshot(1L, 2025);

    assertEquals(0, new BigDecimal("1000000").compareTo(current.realEstateValue()));
    assertEquals(0, new BigDecimal("100000").compareTo(current.rentalIncome()));
    assertEquals(0, new BigDecimal("100000").compareTo(historical.rentalIncome()));
  }

  @Test
  void annualSnapshotsExcludeCapitalizedBondInterestFromSpendableIncome() {
    LongTermAssetEntity bond = asset(LongTermAssetType.BOND, "100000");
    LongTermAssetBondDetailsEntity details = new LongTermAssetBondDetailsEntity();
    details.setTaxRate(new BigDecimal("0.19"));
    details.setMaturityDate(DATE.plusYears(2));
    details.setInterestTreatment(InterestTreatment.CAPITALIZE);
    LongTermAssetBondRatePeriodEntity rate = new LongTermAssetBondRatePeriodEntity();
    rate.setValidFrom(LocalDate.of(2025, 1, 1));
    rate.setAnnualInterestRate(new BigDecimal("0.06"));
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(bond));
    when(bonds.findById(1L)).thenReturn(Optional.of(details));
    when(bondRates.findAllByAssetIdOrderByValidFrom(1L)).thenReturn(List.of(rate));
    when(currencyRates.convertToBaseCurrency(
            any(BigDecimal.class),
            eq(CurrencyType.USD),
            eq(CurrencyType.PLN),
            any(LocalDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));

    assertEquals(BigDecimal.ZERO, service.currentAnnualSnapshot(1L, DATE).bondIncome());
    assertEquals(BigDecimal.ZERO, service.historicalAnnualSnapshot(1L, 2025).bondIncome());
  }

  private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

  private static LongTermAssetEntity asset(LongTermAssetType type, String value) {
    LongTermAssetEntity a = new LongTermAssetEntity();
    a.setId(1L);
    a.setPortfolioId(1L);
    a.setName("Test");
    a.setType(type);
    a.setCurrency(CurrencyType.PLN);
    a.setCurrentValue(new BigDecimal(value));
    a.setActive(true);
    return a;
  }

  private static LongTermAssetSummary row(
      Long id,
      String name,
      LongTermAssetType type,
      String value,
      String income,
      LocalDate maturity) {
    BigDecimal v = new BigDecimal(value);
    BigDecimal i = new BigDecimal(income);
    return new LongTermAssetSummary(
        id,
        name,
        type,
        CurrencyType.PLN,
        v,
        i,
        BigDecimal.ZERO,
        i,
        BigDecimal.ZERO,
        i,
        i.divide(v, 12, java.math.RoundingMode.HALF_UP),
        BigDecimal.ZERO,
        i.divide(v, 12, java.math.RoundingMode.HALF_UP),
        maturity,
        BigDecimal.ZERO,
        new RealEstatePlanningSummary(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
  }

  private static LongTermAssetCashFlowEntity flow(
      CashFlowType type, String amount, Frequency frequency) {
    LongTermAssetCashFlowEntity f = new LongTermAssetCashFlowEntity();
    f.setAssetId(1L);
    f.setType(type);
    f.setAmount(new BigDecimal(amount));
    f.setFrequency(frequency);
    f.setValidFrom(DATE.minusYears(1));
    return f;
  }

  private static LongTermAssetEntity property(
      Long id,
      String name,
      String value,
      String rent,
      String parking,
      String administration,
      String propertyTax,
      String insurance) {
    LongTermAssetEntity a = asset(LongTermAssetType.REAL_ESTATE, value);
    a.setId(id);
    a.setName(name);
    a.setTaxBase(new BigDecimal(rent).add(new BigDecimal(parking)));
    return a;
  }

  private static List<LongTermAssetCashFlowEntity> propertyFlows(LongTermAssetEntity property) {
    return switch (property.getId().intValue()) {
      case 1 -> flows(property, "2650", "250", "687", "320", "200");
      case 2 -> flows(property, "2800", "0", "650", "330", "240");
      case 3 -> flows(property, "2600", "0", "380", "330", "200");
      case 4 -> flows(property, "2900", "100", "400", "340", "250");
      case 5 -> flows(property, "2900", "150", "490", "320", "240");
      default -> List.of();
    };
  }

  private static List<LongTermAssetCashFlowEntity> flows(
      LongTermAssetEntity property,
      String rent,
      String parking,
      String administration,
      String tax,
      String insurance) {
    return List.of(
        cashFlow(property, CashFlowType.RENT, rent, Frequency.MONTHLY),
        cashFlow(property, CashFlowType.PARKING_RENT, parking, Frequency.MONTHLY),
        cashFlow(property, CashFlowType.ADMIN_FEE, administration, Frequency.MONTHLY),
        cashFlow(property, CashFlowType.PROPERTY_TAX, tax, Frequency.ANNUAL),
        cashFlow(property, CashFlowType.INSURANCE, insurance, Frequency.ANNUAL));
  }

  private static LongTermAssetCashFlowEntity cashFlow(
      LongTermAssetEntity property, CashFlowType type, String amount, Frequency frequency) {
    LongTermAssetCashFlowEntity flow = new LongTermAssetCashFlowEntity();
    flow.setAssetId(property.getId());
    flow.setType(type);
    flow.setAmount(new BigDecimal(amount));
    flow.setFrequency(frequency);
    flow.setValidFrom(DATE.minusYears(1));
    return flow;
  }

  private static RentalTaxPolicyEntity taxPolicy(String rate) {
    RentalTaxPolicyEntity policy = new RentalTaxPolicyEntity();
    policy.setRate(new BigDecimal(rate));
    return policy;
  }
}
