package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.BondPlanningSummary;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LongTermAssetAnnualSnapshotServiceTest {
  private final LongTermAssetRepository assets = mock(LongTermAssetRepository.class);
  private final LongTermAssetRentalContractRepository contracts =
      mock(LongTermAssetRentalContractRepository.class);
  private final CurrencyConversion conversion = mock(CurrencyConversion.class);
  private final LongTermAssetLifecycleService lifecycle = mock(LongTermAssetLifecycleService.class);
  private final LongTermAssetQueryService queries = mock(LongTermAssetQueryService.class);
  private final LongTermAssetAnnualSnapshotService service =
      new LongTermAssetAnnualSnapshotService(assets, contracts, conversion, lifecycle, queries);

  @Test
  void currentSnapshotNormalizesCurrenciesAndExcludesCapitalizedBondIncome() {
    LocalDate date = LocalDate.of(2026, 12, 31);
    when(conversion.convertToBaseCurrency(
            new BigDecimal("500000"), CurrencyType.USD, CurrencyType.PLN, date))
        .thenReturn(new BigDecimal("125000"));
    when(conversion.convertToBaseCurrency(
            new BigDecimal("24000"), CurrencyType.USD, CurrencyType.PLN, date))
        .thenReturn(new BigDecimal("6000"));
    var rows =
        List.of(
            summary(1L, LongTermAssetType.REAL_ESTATE, CurrencyType.PLN, "500000", "24000", null),
            summary(
                2L,
                LongTermAssetType.BOND,
                CurrencyType.USD,
                "10000",
                "600",
                InterestTreatment.CAPITALIZE),
            summary(
                3L,
                LongTermAssetType.BOND,
                CurrencyType.USD,
                "20000",
                "900",
                InterestTreatment.PAY_OUT),
            summary(4L, LongTermAssetType.CASH_RESERVE, CurrencyType.USD, "7000", "0", null),
            summary(5L, LongTermAssetType.DEPOSIT, CurrencyType.USD, "3000", "0", null),
            summary(6L, LongTermAssetType.OTHER, CurrencyType.PLN, "999999", "0", null));

    var snapshot = service.currentAnnualSnapshot(rows, date);

    assertThat(snapshot.realEstateValue()).isEqualByComparingTo("125000");
    assertThat(snapshot.rentalIncome()).isEqualByComparingTo("6000");
    assertThat(snapshot.bondValue()).isEqualByComparingTo("30000");
    assertThat(snapshot.bondIncome()).isEqualByComparingTo("900");
    assertThat(snapshot.cashReserveValue()).isEqualByComparingTo("7000");
    assertThat(snapshot.otherAssetValue()).isEqualByComparingTo("3000");
  }

  @Test
  void currentSnapshotLoadsRowsThroughCanonicalQuery() {
    LocalDate date = LocalDate.of(2026, 6, 1);
    when(queries.list(8L, date)).thenReturn(List.of());

    var snapshot = service.currentAnnualSnapshot(8L, date);

    assertThat(snapshot.realEstateValue()).isZero();
    assertThat(snapshot.bondIncome()).isZero();
  }

  private static LongTermAssetSummary summary(
      Long id,
      LongTermAssetType type,
      CurrencyType currency,
      String value,
      String netIncome,
      InterestTreatment treatment) {
    BigDecimal currentValue = new BigDecimal(value);
    BigDecimal income = new BigDecimal(netIncome);
    BondPlanningSummary planning =
        treatment == null
            ? null
            : new BondPlanningSummary(
                currentValue,
                BigDecimal.ZERO,
                income,
                BigDecimal.ZERO,
                income,
                BigDecimal.ZERO,
                null,
                treatment);
    return new LongTermAssetSummary(
        id,
        type.name(),
        type,
        currency,
        currentValue,
        null,
        BigDecimal.ZERO,
        AnnualEconomics.of(currentValue, income, BigDecimal.ZERO, BigDecimal.ZERO),
        null,
        planning,
        null);
  }
}
