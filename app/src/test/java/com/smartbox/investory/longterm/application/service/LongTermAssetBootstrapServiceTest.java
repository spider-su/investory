package com.smartbox.investory.longterm.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.longterm.application.model.LongTermAssetBootstrapDocument;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LongTermAssetBootstrapServiceTest {
  @Mock LongTermAssetRepository assets;
  @Mock LongTermAssetCashFlowRepository cashFlows;
  @Mock LongTermAssetValuationPeriodRepository valuations;
  @Mock LongTermAssetBondRatePeriodRepository bondRates;
  @Mock LongTermAssetBondDetailsRepository bonds;
  @Mock LongTermAssetDepositDetailsRepository deposits;
  @Mock RentalTaxPolicyRepository taxPolicies;
  @Mock PortfolioContextReader portfolioContextReader;
  @Mock com.smartbox.investory.longterm.application.service.LongTermAssetLifecycleService lifecycle;
  @Mock LongTermAssetRentalContractRepository rentalContracts;

  private LongTermAssetBootstrapService service;

  @BeforeEach
  void setUp() {
    service =
        new LongTermAssetBootstrapService(
            assets,
            valuations,
            bondRates,
            bonds,
            deposits,
            taxPolicies,
            portfolioContextReader,
            lifecycle,
            rentalContracts);
    lenient()
        .when(portfolioContextReader.findById(1L))
        .thenReturn(Optional.of(new PortfolioContext(1L, CurrencyType.PLN)));
    lenient().when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of());
    lenient().when(taxPolicies.findAllByPortfolioIdOrderByValidFrom(1L)).thenReturn(List.of());
  }

  @Test
  void sanitizedFixtureReconcilesAndDryRunDoesNotWrite() throws Exception {
    var document =
        new ObjectMapper()
            .findAndRegisterModules()
            .readValue(
                Files.readString(
                    Path.of("src/main/resources/bootstrap/example-long-term-assets.json")),
                LongTermAssetBootstrapDocument.class);

    var result = service.importDocument(document, true);

    assertEquals(7, result.assetsToCreate());
    assertEquals(new BigDecimal("3650000"), result.propertyValue());
    assertEquals(new BigDecimal("172200"), result.grossAnnualIncome());
    assertEquals(new BigDecimal("2770"), result.operatingExpenses());
    assertEquals(new BigDecimal("14637.000"), result.rentalTax());
    assertEquals(new BigDecimal("154793.000"), result.netAnnualIncome());
    verify(assets, never()).save(any());
    verify(taxPolicies, never()).save(any());
  }

  @Test
  void existingExternalKeyIsUpdatedAndSecondDryRunDoesNotCreateDuplicate() {
    var existing = asset("property-a", "700000");
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(existing));
    var input = assetInput("property-a", LongTermAssetType.REAL_ESTATE, "710000");
    var document = new LongTermAssetBootstrapDocument(1L, List.of(), List.of(input));

    var result = service.importDocument(document, true);

    assertEquals(0, result.assetsToCreate());
    assertEquals(1, result.assetsToUpdate());
    verify(assets, never()).save(any());
  }

  @Test
  void repeatedImportUpsertsTheSameAssetWithoutDuplicatingIt() {
    var stored = new ArrayList<LongTermAssetEntity>();
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenAnswer(ignored -> List.copyOf(stored));
    when(assets.save(any()))
        .thenAnswer(
            invocation -> {
              var saved = invocation.getArgument(0, LongTermAssetEntity.class);
              if (saved.getId() == null) {
                saved.setId(10L);
                stored.add(saved);
              }
              return saved;
            });
    var document =
        new LongTermAssetBootstrapDocument(
            1L,
            List.of(),
            List.of(assetInput("property-a", LongTermAssetType.REAL_ESTATE, "710000")));

    var first = service.importDocument(document, false);
    var second = service.importDocument(document, false);

    assertEquals(1, first.assetsToCreate());
    assertEquals(1, second.assetsToUpdate());
    assertEquals(1, stored.size());
    verify(assets, times(2)).save(any());
  }

  @Test
  void bootstrapSchemaDoesNotAcceptCredentialFields() {
    var mapper = new ObjectMapper();
    assertThrows(
        com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class,
        () ->
            mapper.readValue(
                "{\"portfolioId\":1,\"password\":\"secret\"}",
                LongTermAssetBootstrapDocument.class));
  }

  @Test
  void invalidInputFailsBeforeAnyPersistence() {
    var valid = assetInput("valid", LongTermAssetType.REAL_ESTATE, "100");
    var invalid = assetInput("invalid", LongTermAssetType.REAL_ESTATE, "-1");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.importDocument(
                new LongTermAssetBootstrapDocument(1L, List.of(), List.of(valid, invalid)), false));
    verify(assets, never()).save(any());
    verify(cashFlows, never()).save(any());
    verify(valuations, never()).save(any());
  }

  @Test
  void bondFixtureValidatesMaturityRateAndTreatment() {
    var bond =
        new LongTermAssetBootstrapDocument.Bond(
            LocalDate.of(2030, 12, 31),
            InterestTreatment.CAPITALIZE,
            new BigDecimal("0.19"),
            new BigDecimal("300000"));
    var rate =
        new LongTermAssetBootstrapDocument.Period(
            LocalDate.of(2026, 1, 1), LocalDate.of(2030, 12, 31), new BigDecimal("0.055"));
    var input =
        new LongTermAssetBootstrapDocument.AssetEntity(
            "bond-2030",
            LongTermAssetType.BOND,
            "Example Bond",
            CurrencyType.PLN,
            LocalDate.of(2026, 1, 1),
            new BigDecimal("300000"),
            new BigDecimal("300000"),
            LocalDate.of(2026, 1, 1),
            null,
            List.of(),
            List.of(),
            List.of(rate),
            bond,
            null);

    var result =
        service.importDocument(
            new LongTermAssetBootstrapDocument(1L, List.of(), List.of(input)), true);

    assertEquals(1, result.assetsToCreate());
    assertEquals(0, result.rentalTax().signum());
  }

  @Test
  void dryRunTotalsUseOnlyCashFlowsEffectiveOnAssetDate() {
    var historical =
        new LongTermAssetBootstrapDocument.CashFlow(
            CashFlowType.RENT,
            new BigDecimal("1000"),
            Frequency.MONTHLY,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 6, 30));
    var current =
        new LongTermAssetBootstrapDocument.CashFlow(
            CashFlowType.RENT,
            new BigDecimal("2000"),
            Frequency.MONTHLY,
            LocalDate.of(2026, 7, 1),
            null);
    var property =
        new LongTermAssetBootstrapDocument.AssetEntity(
            "property-a",
            LongTermAssetType.REAL_ESTATE,
            "Property A",
            CurrencyType.PLN,
            LocalDate.of(2020, 1, 1),
            new BigDecimal("400000"),
            new BigDecimal("500000"),
            LocalDate.of(2026, 7, 1),
            null,
            List.of(historical, current),
            List.of(),
            List.of(),
            null,
            null,
            BigDecimal.ZERO,
            false);

    var result =
        service.importDocument(
            new LongTermAssetBootstrapDocument(1L, List.of(), List.of(property)), true);

    assertEquals(new BigDecimal("24000"), result.grossAnnualIncome());
  }

  @Test
  void existingAssetCurrencyCannotBeRelabeledByBootstrap() {
    var existing = asset("property-a", "700000");
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(existing));
    var changed = assetInput("property-a", LongTermAssetType.REAL_ESTATE, "710000");
    changed =
        new LongTermAssetBootstrapDocument.AssetEntity(
            changed.externalKey(),
            changed.type(),
            changed.name(),
            CurrencyType.USD,
            changed.acquisitionDate(),
            changed.acquisitionValue(),
            changed.currentValue(),
            changed.effectiveFrom(),
            changed.notes(),
            changed.cashFlows(),
            changed.valuationPeriods(),
            changed.bondRatePeriods(),
            changed.bond(),
            changed.deposit(),
            changed.taxBase(),
            changed.rentalTaxPaidByTenant());

    var document = new LongTermAssetBootstrapDocument(1L, List.of(), List.of(changed));

    assertThrows(IllegalArgumentException.class, () -> service.importDocument(document, false));
    verify(assets, never()).save(any());
  }

  @Test
  void bootstrapCorrectionDeletesManagedContractsBeforeWritingReplacementPeriods() {
    var existingAsset = asset("property-a", "700000");
    existingAsset.setTaxBase(new BigDecimal("2500"));
    var importedContract = new LongTermAssetRentalContractEntity();
    importedContract.setId(11L);
    importedContract.setAssetId(existingAsset.getId());
    importedContract.setStartDate(LocalDate.of(2026, 1, 1));
    importedContract.setBootstrapManaged(true);
    when(assets.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(existingAsset));
    when(assets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(rentalContracts.findAllByAssetIdOrderByStartDate(existingAsset.getId()))
        .thenReturn(List.of(importedContract));
    var correctedFlow =
        new LongTermAssetBootstrapDocument.CashFlow(
            CashFlowType.RENT,
            new BigDecimal("3000"),
            Frequency.MONTHLY,
            LocalDate.of(2026, 2, 1),
            null);
    var base = assetInput("property-a", LongTermAssetType.REAL_ESTATE, "710000");
    var corrected =
        new LongTermAssetBootstrapDocument.AssetEntity(
            base.externalKey(),
            base.type(),
            base.name(),
            base.currency(),
            base.acquisitionDate(),
            base.acquisitionValue(),
            base.currentValue(),
            base.effectiveFrom(),
            base.notes(),
            List.of(correctedFlow),
            base.valuationPeriods(),
            base.bondRatePeriods(),
            base.bond(),
            base.deposit(),
            new BigDecimal("2500"),
            false);

    service.importDocument(
        new LongTermAssetBootstrapDocument(1L, List.of(), List.of(corrected)), false);

    var saved = ArgumentCaptor.forClass(LongTermAssetRentalContractEntity.class);
    var order = inOrder(rentalContracts);
    order.verify(rentalContracts).deleteAll(List.of(importedContract));
    order.verify(rentalContracts).flush();
    order.verify(rentalContracts).save(saved.capture());
    order.verify(rentalContracts).flush();
    assertEquals(LocalDate.of(2026, 2, 1), saved.getValue().getStartDate());
    assertEquals(new BigDecimal("2500"), saved.getValue().getMonthlyTaxBase());
    assertTrue(saved.getValue().isBootstrapManaged());
  }

  @Test
  void anonymousManualContractIsNeverTreatedAsBootstrapOwned() {
    var existingAsset = asset("property-a", "700000");
    var manualContract = new LongTermAssetRentalContractEntity();
    manualContract.setId(12L);
    manualContract.setAssetId(existingAsset.getId());
    manualContract.setStartDate(LocalDate.of(2026, 1, 1));
    when(assets.findByPortfolioIdAndExternalKey(1L, "property-a"))
        .thenReturn(Optional.of(existingAsset));
    when(rentalContracts.findAllByAssetIdOrderByStartDate(existingAsset.getId()))
        .thenReturn(List.of(manualContract));
    var flow =
        new LongTermAssetBootstrapDocument.CashFlow(
            CashFlowType.RENT,
            new BigDecimal("3000"),
            Frequency.MONTHLY,
            LocalDate.of(2026, 2, 1),
            null);
    var base = assetInput("property-a", LongTermAssetType.REAL_ESTATE, "710000");
    var input =
        new LongTermAssetBootstrapDocument.AssetEntity(
            base.externalKey(),
            base.type(),
            base.name(),
            base.currency(),
            base.acquisitionDate(),
            base.acquisitionValue(),
            base.currentValue(),
            base.effectiveFrom(),
            base.notes(),
            List.of(flow),
            base.valuationPeriods(),
            base.bondRatePeriods(),
            base.bond(),
            base.deposit(),
            BigDecimal.ZERO,
            false);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.importDocument(
                new LongTermAssetBootstrapDocument(1L, List.of(), List.of(input)), false));

    verify(rentalContracts, never()).delete(any());
    verify(rentalContracts, never()).deleteAll(anyCollection());
    verify(rentalContracts, never()).save(any());
  }

  private static LongTermAssetEntity asset(String key, String value) {
    var asset = new LongTermAssetEntity();
    asset.setId(1L);
    asset.setPortfolioId(1L);
    asset.setExternalKey(key);
    asset.setName(key);
    asset.setType(LongTermAssetType.REAL_ESTATE);
    asset.setCurrency(CurrencyType.PLN);
    asset.setCurrentValue(new BigDecimal(value));
    return asset;
  }

  private static LongTermAssetBootstrapDocument.AssetEntity assetInput(
      String key, LongTermAssetType type, String value) {
    return new LongTermAssetBootstrapDocument.AssetEntity(
        key,
        type,
        key,
        CurrencyType.PLN,
        null,
        null,
        new BigDecimal(value),
        LocalDate.of(2026, 1, 1),
        null,
        List.of(),
        List.of(),
        List.of(),
        type == LongTermAssetType.BOND
            ? new LongTermAssetBootstrapDocument.Bond(
                LocalDate.of(2030, 12, 31), InterestTreatment.PAY_OUT, new BigDecimal("0.19"), null)
            : null,
        null);
  }
}
