package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.application.model.RealEstateGroupPlanningSummary;
import com.smartbox.investory.longterm.application.model.RealEstatePlanningSummary;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Assembles the UI-facing group model from already-calculated asset summaries. */
@Component
public class LongTermAssetPageAssembler {
  private final CurrencyConversion currencyRates;

  public LongTermAssetPageAssembler(CurrencyConversion currencyRates) {
    this.currencyRates = currencyRates;
  }

  public List<LongTermAssetQueryService.AssetGroupSummary> groups(
      List<LongTermAssetSummary> rows, CurrencyType base, LocalDate date) {
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
            date),
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
            date),
        group(
            "CASH_RESERVE",
            "Cash",
            rows.stream()
                .filter(r -> r.type() == LongTermAssetType.CASH_RESERVE)
                .sorted(
                    Comparator.comparing(LongTermAssetSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList(),
            base,
            date),
        group(
            "OTHER",
            "Other",
            rows.stream()
                .filter(
                    r ->
                        r.type() == LongTermAssetType.DEPOSIT
                            || r.type() == LongTermAssetType.OTHER)
                .sorted(
                    Comparator.comparing(LongTermAssetSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList(),
            base,
            date));
  }

  private LongTermAssetQueryService.AssetGroupSummary group(
      String key,
      String title,
      List<LongTermAssetSummary> rows,
      CurrencyType base,
      LocalDate date) {
    BigDecimal value = sum(rows, LongTermAssetSummary::currentValue, base, date);
    BigDecimal income = sum(rows, r -> r.annualEconomics().grossAnnualIncome(), base, date);
    BigDecimal expenses = sum(rows, r -> r.annualEconomics().annualExpenses(), base, date);
    BigDecimal tax = sum(rows, r -> r.annualEconomics().annualTax(), base, date);
    BigDecimal payment = sum(rows, r -> planning(r).totalPaymentMonthly(), base, date);
    BigDecimal monthlyIncome = sum(rows, r -> planning(r).monthlyIncome(), base, date);
    BigDecimal monthlyReduce = sum(rows, r -> planning(r).monthlyReduce(), base, date);
    BigDecimal taxBase = sum(rows, r -> planning(r).taxBase(), base, date);
    BigDecimal monthlyRentTax =
        sum(rows, r -> planning(r).annualTax(), base, date)
            .divide(BigDecimal.valueOf(12), 18, RoundingMode.HALF_UP);
    RealEstateGroupPlanningSummary planning =
        "REAL_ESTATE".equals(key)
            ? new RealEstateGroupPlanningSummary(
                payment,
                monthlyIncome.subtract(monthlyReduce),
                monthlyReduce,
                taxBase,
                monthlyRentTax,
                LongTermAssetCalculator.ratio(
                    monthlyIncome.subtract(monthlyReduce).multiply(BigDecimal.valueOf(12)), value))
            : null;
    return new LongTermAssetQueryService.AssetGroupSummary(
        key,
        title,
        base,
        rows,
        value,
        AnnualEconomics.aggregateOf(value, income, expenses, tax),
        planning);
  }

  private BigDecimal sum(
      List<LongTermAssetSummary> rows,
      Function<LongTermAssetSummary, BigDecimal> value,
      CurrencyType base,
      LocalDate date) {
    return rows.stream()
        .map(
            row -> {
              BigDecimal amount = value.apply(row);
              if (amount == null) {
                return BigDecimal.ZERO;
              }
              return row.currency() == base
                  ? amount
                  : currencyRates.convertToBaseCurrency(amount, base, row.currency(), date);
            })
        .map(amount -> amount == null ? BigDecimal.ZERO : amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static RealEstatePlanningSummary planning(LongTermAssetSummary row) {
    return row.realEstatePlanning() == null
        ? new RealEstatePlanningSummary(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO)
        : row.realEstatePlanning();
  }
}
