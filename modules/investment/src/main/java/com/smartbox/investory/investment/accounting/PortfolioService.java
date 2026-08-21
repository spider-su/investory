package com.smartbox.investory.investment.accounting;

import com.smartbox.investory.investment.accounting.model.*;
import com.smartbox.investory.investment.infrastructure.persistence.*;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioCurrencyBreakdownEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioCurrencyBreakdownRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioDataQualityRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioFallbackReconciliationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceRepository;
import com.smartbox.investory.investment.market.fx.CurrencyRateService;
import com.smartbox.investory.investment.reporting.PortfolioPerformanceQueryService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioService {

  /**
   * Symbols whose total |P/L| falls below {@code OTHER_BUCKET_RATIO * |grand total|} are collapsed
   * into a single "Other" row in the per-instrument ranking, so the chart isn't dominated by
   * long-tail noise.
   */
  private static final double OTHER_BUCKET_RATIO = 0.019;

  private static final double ACCOUNT_VISIBILITY_MIN_VALUE = 50.0;
  private static final Set<String> ACCOUNT_NET_DEPOSIT_CATEGORIES =
      Set.of(
          "EXTERNAL_DEPOSIT",
          "EXTERNAL_WITHDRAWAL",
          "INTERNAL_TRANSFER_IN",
          "INTERNAL_TRANSFER_OUT",
          "INTERNAL_BOOKKEEPING",
          "FX_CONVERSION",
          "CORRECTION");

  private final CurrencyRateService currencyRateService;
  private final ClosedPositionRepository closedPositionRepository;
  private final OpenedPositionRepository openedPositionRepository;
  private final CashOperationRepository cashOperationRepository;
  private final NormalizedCashOperationRepository normalizedCashOperationRepository;
  private final AccountRepository accountRepository;
  private final AccountStatisticsRepository accountStatisticsRepository;
  private final PortfolioAssetAllocationRepository portfolioAssetAllocationRepository;
  private final AssetRepository assetRepository;
  private final PortfolioCurrencyBreakdownRepository portfolioCurrencyBreakdownRepository;
  private final PortfolioKpiSummaryRepository portfolioKpiSummaryRepository;
  private final PortfolioFallbackReconciliationRepository fallbackReconciliationRepository;
  private final PortfolioDataQualityRepository dataQualityRepository;
  private final SymbolPerformanceRepository symbolPerformanceRepository;
  private final TaxCalculator taxCalculator;
  private final CashFlowAggregator cashFlowAggregator;
  private final CashOperationNormalizer cashOperationNormalizer;
  private final PortfolioProperties properties;
  private final PortfolioPerformanceQueryService performanceQueryService;

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  @Cacheable(cacheNames = "portfolioCalculation", key = "'all'")
  public Portfolio calculateTotalProfitLoss() {
    Portfolio portfolio = new Portfolio();
    boolean kpiApplied = applyKpiSummary(portfolio);
    if (kpiApplied) {
      applyCashFlowSupplement(portfolio);
    } else {
      applyCalculatedTotals(portfolio);
    }
    portfolio.setAccountBalances(calculateAccountBalances(portfolio.getBaseCurrency()));
    portfolio.setAccountBalancesTotal(
        accountBalancesTotal(
            portfolio.getAccountBalances(),
            portfolio.getBaseCurrency(),
            portfolio.getNetDeposits()));
    List<OpenPositionValue> openPositionValues = calculateOpenPositionValues();
    portfolio.setOpenPositionValues(openPositionValues);
    portfolio.setOpenPositionValuesTotal(
        openPositionValuesTotal(openPositionValues, portfolio.getBaseCurrency()));
    portfolio.setDividendGainers(calculateDividendGainers(portfolio.getBaseCurrency()));
    if (properties.isDashboardEnrichmentEnabled()) {
      applyFallbackReconciliationStatus(portfolio);
      applyDataQuality(portfolio);
      applyRiskExposure(portfolio);
    }
    if (!applyPortfolioCurrencyBreakdowns(portfolio, !kpiApplied)) {
      boolean usedAccountStatistics =
          applyAccountStatisticsCurrencyBreakdowns(portfolio, !kpiApplied);
      if (!usedAccountStatistics) {
        applyOpenPositionUnrealizedCurrencyBreakdown(portfolio, !kpiApplied);
      }
    }
    ensureCurrencyRows(portfolio);
    applyInvestmentProfitFromComponents(portfolio);

    // Estimated capital-gains tax ("Belka" 19%) for the CURRENT tax year only, applying
    // loss carry-forward from prior years (Polish rule: losses deductible over the next 5 years).
    TaxCalculator.TaxSummary tax =
        taxCalculator.calculate(closedPositionRepository.findAll(), portfolio.getBaseCurrency());
    portfolio.setCapitalGainsTax(tax.capitalGainsTax().doubleValue());
    portfolio.setLossCarryForward(tax.lossCarryForward().doubleValue());

    // Exchange rates for the currencies board (units of each currency per 1 base currency)
    for (CurrencyType currency : CurrencyType.values()) {
      if (currency == portfolio.getBaseCurrency()) {
        continue;
      }
      OptionalDouble rate = currencyRateService.findRate(portfolio.getBaseCurrency(), currency);
      if (rate.isPresent()) {
        portfolio.getExchangeRates().put(currency, rate.getAsDouble());
      } else {
        log.warn("No FX rate available for {} -> {}", portfolio.getBaseCurrency(), currency);
      }
    }

    portfolio.setPerformancePerSymbol(calculatePerformancePerInstrument());
    portfolio.setMonthlyPerformance(calculateMonthlyPerformance());
    return portfolio;
  }

  private void applyFallbackReconciliationStatus(Portfolio portfolio) {
    fallbackReconciliationRepository.findAllStatuses().stream()
        .findFirst()
        .ifPresent(
            row -> {
              portfolio.setReconciliationStatus(String.valueOf(row[0]));
              portfolio.setReconciliationDifference(
                  Math.abs(number(row[1]).doubleValue())
                      + Math.abs(number(row[2]).doubleValue())
                      + Math.abs(number(row[3]).doubleValue())
                      + Math.abs(number(row[4]).doubleValue()));
            });
  }

  private void applyDataQuality(Portfolio portfolio) {
    List<PortfolioDataQualityIssue> issues =
        properties.isDataQualityIssuesEnabled()
            ? dataQualityRepository.findIssues().stream().map(this::toDataQualityIssue).toList()
            : List.of();
    dataQualityRepository.findSnapshot().stream()
        .findFirst()
        .ifPresent(
            row ->
                portfolio.setDataQuality(
                    new PortfolioDataQuality(
                        String.valueOf(row[0]),
                        number(row[1]).longValue(),
                        number(row[2]).longValue(),
                        number(row[3]).longValue(),
                        number(row[4]).longValue(),
                        number(row[5]).longValue(),
                        number(row[6]).longValue(),
                        number(row[7]).longValue(),
                        number(row[8]).longValue(),
                        number(row[9]).longValue(),
                        number(row[10]).longValue(),
                        number(row[11]).longValue(),
                        toOffsetDateTime(row[12]),
                        toOffsetDateTime(row[13]),
                        toLocalDate(row[14]),
                        toLocalDate(row[15]),
                        toOffsetDateTime(row[16]),
                        issues)));
  }

  private PortfolioDataQualityIssue toDataQualityIssue(Object[] row) {
    return new PortfolioDataQualityIssue(
        String.valueOf(row[0]),
        row[1] == null ? null : String.valueOf(row[1]),
        row[2] == null ? null : String.valueOf(row[2]),
        String.valueOf(row[3]),
        row[4] instanceof Number value ? value.intValue() : null,
        toLocalDate(row[5]),
        row[6] == null ? null : String.valueOf(row[6]),
        row[7] == null ? null : String.valueOf(row[7]),
        row[8] == null ? null : String.valueOf(row[8]),
        row[9] == null ? null : String.valueOf(row[9]),
        row[10] instanceof Number value ? value.longValue() : null);
  }

  private LocalDate toLocalDate(Object value) {
    return switch (value) {
      case null -> null;
      case LocalDate localDate -> localDate;
      case java.sql.Date sqlDate -> sqlDate.toLocalDate();
      case TemporalAccessor temporal -> {
        try {
          yield LocalDate.from(temporal);
        } catch (java.time.DateTimeException ignored) {
          yield parseLocalDateText(value);
        }
      }
      default -> parseLocalDateText(value);
    };
  }

  private LocalDate parseLocalDateText(Object value) {
    String text = String.valueOf(value);
    return LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
  }

  private void applyRiskExposure(Portfolio portfolio) {
    double total =
        portfolioAssetAllocationRepository.findAll().stream()
            .mapToDouble(row -> nz(row.getTotalValueInBaseCurrency()))
            .sum();
    if (total <= 0.0) {
      portfolio.setRiskExposure(RiskExposureSummary.unavailable(portfolio.getCash()));
      return;
    }
    List<Double> weights =
        portfolioAssetAllocationRepository.findAll().stream()
            .map(row -> nz(row.getTotalValueInBaseCurrency()) / total * 100.0)
            .sorted(Comparator.reverseOrder())
            .toList();
    double largest = weights.isEmpty() ? 0.0 : weights.getFirst();
    double topFive = weights.stream().limit(5).mapToDouble(Double::doubleValue).sum();
    double baseExposure =
        portfolioCurrencyBreakdownRepository.findAll().stream()
                .filter(row -> row.getMetricType().equals("ACCOUNT_LATEST"))
                .filter(row -> row.getCurrency() == portfolio.getBaseCurrency())
                .mapToDouble(row -> nz(row.getAmountInBaseCurrency()))
                .sum()
            / portfolio.getBalance()
            * 100.0;
    double foreignExposure =
        portfolioCurrencyBreakdownRepository.findAll().stream()
                .filter(row -> row.getMetricType().equals("ACCOUNT_LATEST"))
                .filter(row -> row.getCurrency() != portfolio.getBaseCurrency())
                .mapToDouble(row -> nz(row.getAmountInBaseCurrency()))
                .sum()
            / portfolio.getBalance()
            * 100.0;
    List<String> warnings = new ArrayList<>();
    if (largest >= 20.0) warnings.add("Largest holding exceeds 20% of portfolio.");
    if (topFive >= 50.0) warnings.add("Top five holdings exceed 50% of portfolio.");
    portfolio.setRiskExposure(
        new RiskExposureSummary(
            largest,
            topFive,
            baseExposure,
            foreignExposure,
            portfolio.getCash(),
            portfolio.getDividends() + portfolio.getInterest(),
            "Current snapshot · base "
                + portfolio.getBaseCurrency()
                + " · cash excluded from asset concentration",
            warnings));
  }

  private static Number number(Object value) {
    return value instanceof Number n ? n : 0;
  }

  private static java.time.OffsetDateTime toOffsetDateTime(Object value) {
    return value instanceof java.time.OffsetDateTime odt
        ? odt
        : value instanceof java.sql.Timestamp ts
            ? ts.toInstant().atOffset(java.time.ZoneOffset.UTC)
            : null;
  }

  private boolean applyKpiSummary(Portfolio portfolio) {
    Optional<PortfolioKpiSummaryEntity> summary =
        portfolioKpiSummaryRepository.findAll().stream().findFirst();
    if (summary.isEmpty()) {
      return false;
    }

    PortfolioKpiSummaryEntity kpi = summary.get();
    CurrencyType baseCurrency = kpi.getBaseCurrency();
    double realized = nz(kpi.getTotalRealizedProfit());
    double unrealized = nz(kpi.getTotalUnrealizedProfit());
    double dividends = nz(kpi.getTotalDividends());
    double deposits = nz(kpi.getTotalDeposits());
    double netDeposits = nz(kpi.getNetDeposits());
    double balance = nz(kpi.getTotalEquity());
    double cash = nz(kpi.getTotalCash());

    portfolio.setBaseCurrency(baseCurrency);
    portfolio.setRealizedProfit(realized);
    portfolio.setUnrealizedProfit(unrealized);
    portfolio.setDividends(dividends);
    portfolio.setDeposits(deposits);
    portfolio.setNetDeposits(netDeposits);
    portfolio.setWithdrawals(deposits - netDeposits);
    portfolio.setCash(cash);
    portfolio.setBalance(balance);
    portfolio.setRoi(netDeposits > 0 ? (balance - netDeposits) / netDeposits * 100.0 : 0.0);

    portfolio.getRealizedByCurrency().put(baseCurrency, realized);
    portfolio.getUnrealizedByCurrency().put(baseCurrency, unrealized);
    portfolio.getDividendsByCurrency().put(baseCurrency, dividends);
    return true;
  }

  private void applyCashFlowSupplement(Portfolio portfolio) {
    List<AccountStatisticsEntity> stats = accountStatisticsRepository.findAll();
    Set<Long> cashOnlyAccounts = cashOnlyAccountIds();
    stats = stats.stream().filter(stat -> !cashOnlyAccounts.contains(stat.getAccountId())).toList();
    portfolio.setInterest(stats.stream().mapToDouble(stat -> nz(stat.getInterest())).sum());
    List<CashOperationEntity> cashOperations =
        cashOperationRepository.findAll().stream()
            .filter(operation -> !cashOnlyAccounts.contains(operation.getAccount()))
            .toList();
    if (!CollectionUtils.isEmpty(cashOperations)) {
      CashFlowAggregator.CashFlowSummary cashFlow =
          cashFlowAggregator.aggregate(cashOperations, portfolio.getBaseCurrency());
      portfolio.setDividendTax(cashFlow.dividendTax().doubleValue());
    }
  }

  private void applyCalculatedTotals(Portfolio portfolio) {
    List<ClosedPosition> closedPositions = closedPositionRepository.findAll();
    List<CashOperationEntity> cashOperations = cashOperationRepository.findAll();

    // ── Realized P/L from imported closed positions ──────────────────────
    for (ClosedPosition position : closedPositions) {
      LocalDate rateDate =
          position.getCloseTime() != null ? position.getCloseTime().toLocalDate() : LocalDate.now();
      CurrencyType profitCurrency = position.getProfitCurrency();
      CurrencyType commissionCurrency = position.getCommissionCurrency();
      double profitAndSwap = nz(position.getProfit()) + nz(position.getSwap());
      double commission = nz(position.getCommission());
      portfolio.getRealizedByCurrency().merge(profitCurrency, profitAndSwap, Double::sum);
      portfolio.getRealizedByCurrency().merge(commissionCurrency, commission, Double::sum);
      portfolio.setRealizedProfit(
          portfolio.getRealizedProfit()
              + currencyRateService.convertToBaseCurrency(
                  profitAndSwap, portfolio.getBaseCurrency(), profitCurrency, rateDate)
              + currencyRateService.convertToBaseCurrency(
                  commission, portfolio.getBaseCurrency(), commissionCurrency, rateDate));
    }

    // ── Unrealized P/L (live from open positions — always real-time) ─────
    for (OpenedPosition position : openedPositionRepository.findAll()) {
      CurrencyType profitCurrency = position.getProfitCurrency();
      CurrencyType commissionCurrency = position.getCommissionCurrency();
      double profitAndSwap = nz(position.getProfit()) + nz(position.getSwap());
      double commission = nz(position.getCommission());
      portfolio.getUnrealizedByCurrency().merge(profitCurrency, profitAndSwap, Double::sum);
      portfolio.getUnrealizedByCurrency().merge(commissionCurrency, commission, Double::sum);
      portfolio.setUnrealizedProfit(
          portfolio.getUnrealizedProfit()
              + currencyRateService.convertToBaseCurrency(
                  profitAndSwap, portfolio.getBaseCurrency(), profitCurrency, LocalDate.now())
              + currencyRateService.convertToBaseCurrency(
                  commission, portfolio.getBaseCurrency(), commissionCurrency, LocalDate.now()));
    }

    // ── Dividends from imported cash operations ──────────────────────────
    double dividendsTotal = 0.0;
    for (CashOperationEntity cashOperation : cashOperations) {
      if (cashOperation.getType() == CashOperationType.DIVIDEND) {
        CurrencyType currency =
            cashOperation.getCurrency() != null
                ? cashOperation.getCurrency()
                : portfolio.getBaseCurrency();
        LocalDate rateDate =
            cashOperation.getDate() != null
                ? cashOperation.getDate().toLocalDate()
                : LocalDate.now();
        double amount = nz(cashOperation.getAmount());
        double amountInBase =
            currencyRateService.convertToBaseCurrency(
                amount, portfolio.getBaseCurrency(), currency, rateDate);
        dividendsTotal += amountInBase;
        portfolio.getDividendsByCurrency().merge(currency, amount, Double::sum);
      }
    }

    // Fall back to cash_operations for deposits/withdrawals/interest/tax.
    CashFlowAggregator.CashFlowSummary cashFlow =
        cashFlowAggregator.aggregate(cashOperations, portfolio.getBaseCurrency());
    portfolio.setDeposits(cashFlow.deposits().doubleValue());
    portfolio.setWithdrawals(cashFlow.withdrawals().doubleValue());
    portfolio.setNetDeposits(cashFlow.netDeposits().doubleValue());
    portfolio.setInterest(cashFlow.interest().doubleValue());
    // Use summary-derived dividends when available, fall back to cashflow aggregator
    portfolio.setDividends(
        dividendsTotal > 0 ? dividendsTotal : cashFlow.dividends().doubleValue());
    portfolio.setDividendTax(cashFlow.dividendTax().doubleValue());
    if (dividendsTotal == 0.0) {
      cashFlow
          .dividendsByCurrency()
          .forEach(
              (currency, amount) ->
                  portfolio
                      .getDividendsByCurrency()
                      .merge(currency, amount.doubleValue(), Double::sum));
    }

    // Balance breakdown from account_statistics.
    List<AccountStatisticsEntity> stats = accountStatisticsRepository.findAll();
    Set<Long> cashOnlyAccounts = cashOnlyAccountIds();
    stats = stats.stream().filter(stat -> !cashOnlyAccounts.contains(stat.getAccountId())).toList();
    double cash;
    double balance;
    if (!stats.isEmpty()) {
      List<AccountStatisticsEntity> activeStats =
          stats.stream()
              .filter(
                  s ->
                      Math.abs(nz(s.getCashBalance()) + nz(s.getMarketValue()))
                          >= ACCOUNT_VISIBILITY_MIN_VALUE)
              .toList();
      double marketValue = activeStats.stream().mapToDouble(s -> nz(s.getMarketValue())).sum();
      cash = activeStats.stream().mapToDouble(s -> nz(s.getCashBalance())).sum();
      balance = marketValue + cash;
    } else {
      balance =
          openedPositionRepository.findAll().stream()
              .mapToDouble(
                  position ->
                      currencyRateService.convertToBaseCurrency(
                              nz(position.getPurchaseValue()), portfolio.getBaseCurrency(),
                              position.getCostCurrency(), java.time.LocalDate.now())
                          + currencyRateService.convertToBaseCurrency(
                              nz(position.getProfit()) + nz(position.getSwap()),
                              portfolio.getBaseCurrency(),
                              position.getProfitCurrency(),
                              java.time.LocalDate.now())
                          + currencyRateService.convertToBaseCurrency(
                              nz(position.getCommission()), portfolio.getBaseCurrency(),
                              position.getCommissionCurrency(), java.time.LocalDate.now()))
              .sum();
      cash = 0.0;
    }
    portfolio.setBalance(balance);
    portfolio.setCash(cash);
    double netDep = portfolio.getNetDeposits();
    portfolio.setRoi(netDep > 0 ? (balance - netDep) / netDep * 100.0 : 0.0);
  }

  private void applyInvestmentProfitFromComponents(Portfolio portfolio) {
    // Keep the headline equal to the visible breakdown: realized + unrealized +
    // dividends after withholding tax + interest.
    double totalProfit =
        portfolio.getRealizedProfit()
            + portfolio.getUnrealizedProfit()
            + portfolio.getDividends()
            + portfolio.getDividendTax()
            + portfolio.getInterest();
    portfolio.setTotalProfit(totalProfit);
    double netDeposits = portfolio.getNetDeposits();
    portfolio.setRoi(netDeposits > 0 ? totalProfit / netDeposits * 100.0 : 0.0);
  }

  private void ensureCurrencyRows(Portfolio portfolio) {
    // Ensure every supported currency (incl. PLN) is represented across all breakdowns,
    // so accounts with no positions/dividends still show a 0 row instead of disappearing.
    for (CurrencyType currency : CurrencyType.values()) {
      portfolio.getRealizedByCurrency().putIfAbsent(currency, 0.0);
      portfolio.getUnrealizedByCurrency().putIfAbsent(currency, 0.0);
      portfolio.getDividendsByCurrency().putIfAbsent(currency, 0.0);
    }
  }

  private List<AccountBalance> calculateAccountBalances(CurrencyType baseCurrency) {
    List<AccountStatisticsEntity> stats = accountStatisticsRepository.findAll();
    Set<Long> cashOnlyAccounts = cashOnlyAccountIds();
    if (!CollectionUtils.isEmpty(stats)) {
      Map<Long, AccountEntity> accountsById =
          accountsById(
              stats.stream()
                  .map(AccountStatisticsEntity::getAccountId)
                  .filter(Objects::nonNull)
                  .collect(Collectors.toSet()));
      Map<Long, AccountNetDeposit> netDepositsByAccount =
          accountNetDeposits(accountsById.keySet(), accountsById, baseCurrency);
      return stats.stream()
          .filter(stat -> !cashOnlyAccounts.contains(stat.getAccountId()))
          .filter(this::hasDashboardAccountSurface)
          .sorted(
              Comparator.comparing(
                      (AccountStatisticsEntity stat) ->
                          accountsById.get(stat.getAccountId()).getCreatedAt(),
                      Comparator.nullsLast(Comparator.naturalOrder()))
                  .thenComparing(AccountStatisticsEntity::getAccountId))
          .map(
              stat -> {
                double balance = nz(stat.getCashBalance()) + nz(stat.getMarketValue());
                AccountEntity account = accountsById.get(stat.getAccountId());
                AccountNetDeposit netDeposit =
                    netDepositsByAccount.getOrDefault(
                        stat.getAccountId(),
                        new AccountNetDeposit(
                            Math.abs(nz(stat.getAccountNetDeposit())) > 0.005
                                ? nz(stat.getAccountNetDeposit())
                                : nz(stat.getNetDeposit()),
                            nz(stat.getNetDeposit())));
                CurrencyType localCurrency = account.getCurrency();
                double localBalance =
                    nz(localBalance(balance, baseCurrency, localCurrency, LocalDate.now()));
                double localCash =
                    nz(
                        localBalance(
                            nz(stat.getCashBalance()),
                            baseCurrency,
                            localCurrency,
                            LocalDate.now()));
                double localNetDeposit = netDeposit.localAmount();
                double baseNetDeposit = netDeposit.baseAmount();
                double profit = balance - baseNetDeposit;
                double localProfit = localBalance - localNetDeposit;
                return new AccountBalance(
                    stat.getAccountId(),
                    account.getName(),
                    localNetDeposit,
                    baseNetDeposit,
                    profit,
                    localProfit,
                    profitLossPercent(balance, baseNetDeposit),
                    balance,
                    nz(stat.getCashBalance()),
                    localCurrency,
                    localBalance,
                    localCash);
              })
          .toList();
    }

    return List.of();
  }

  private AccountBalance accountBalancesTotal(
      List<AccountBalance> accounts, CurrencyType baseCurrency, double canonicalNetDeposit) {
    double balance = accounts.stream().mapToDouble(AccountBalance::getBalance).sum();
    double cash = accounts.stream().mapToDouble(AccountBalance::getCash).sum();
    double netDeposit = canonicalNetDeposit;
    double profit = balance - netDeposit;
    Double roi =
        Math.abs(netDeposit) >= ACCOUNT_VISIBILITY_MIN_VALUE
            ? (balance - netDeposit) / netDeposit * 100.0
            : null;
    return new AccountBalance(
        null,
        "Total",
        netDeposit,
        netDeposit,
        profit,
        profit,
        roi,
        balance,
        cash,
        baseCurrency,
        balance,
        cash);
  }

  private boolean hasDashboardAccountSurface(AccountStatisticsEntity stat) {
    return Math.abs(nz(stat.getCashBalance()) + nz(stat.getMarketValue()))
            >= ACCOUNT_VISIBILITY_MIN_VALUE
        || Math.abs(nz(stat.getAccountNetDeposit())) >= ACCOUNT_VISIBILITY_MIN_VALUE
        || Math.abs(nz(stat.getNetDeposit())) >= ACCOUNT_VISIBILITY_MIN_VALUE;
  }

  private Map<Long, AccountNetDeposit> accountNetDeposits(
      Collection<Long> accountIds, Map<Long, AccountEntity> accountsById, CurrencyType baseCurrency) {
    if (CollectionUtils.isEmpty(accountIds)) {
      return Map.of();
    }
    List<NormalizedCashOperationRepository.NormalizedCashOperationRow> flowRows =
        normalizedCashOperationRepository.findAllByAccountIdIn(accountIds);
    Map<Long, AccountNetDeposit> deposits =
        flowRows.stream()
            .filter(row -> row.getAccountId() != null)
            .filter(row -> ACCOUNT_NET_DEPOSIT_CATEGORIES.contains(row.getNormalizedCategory()))
            .collect(
                Collectors.groupingBy(
                    NormalizedCashOperationRepository.NormalizedCashOperationRow::getAccountId,
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        rows ->
                            new AccountNetDeposit(
                                rows.stream().mapToDouble(row -> nz(row.getAmount())).sum(),
                                rows.stream()
                                    .mapToDouble(row -> nz(row.getAmountInBaseCurrency()))
                                    .sum()))));
    boolean canonicalFlowsPresent =
        flowRows.stream()
            .anyMatch(row -> row.getAccountFlowAmountInPortfolioBaseCurrency() != null);
    if (!canonicalFlowsPresent) {
      Map<Long, Double> adjustments =
          new XtbAccountFundingCalculator(cashOperationNormalizer)
              .calculate(cashOperationRepository.findAllByAccountIn(accountIds), accountsById);
      adjustments.forEach(
          (accountId, adjustment) -> {
            AccountEntity account = accountsById.get(accountId);
            double baseAdjustment =
                currencyRateService.convertToBaseCurrency(
                    adjustment, baseCurrency, account.getCurrency(), LocalDate.now());
            AccountNetDeposit current =
                deposits.getOrDefault(accountId, new AccountNetDeposit(0, 0));
            deposits.put(
                accountId,
                new AccountNetDeposit(
                    current.localAmount() + adjustment, current.baseAmount() + baseAdjustment));
          });
    }
    return deposits;
  }

  private Double profitLossPercent(double balance, double netDeposit) {
    if (Math.abs(balance) < 0.005) {
      return 0.0;
    }
    return Math.abs(netDeposit) >= ACCOUNT_VISIBILITY_MIN_VALUE
        ? (balance - netDeposit) / netDeposit * 100.0
        : null;
  }

  private List<OpenPositionValue> calculateOpenPositionValues() {
    List<PortfolioAssetAllocationEntity> allocations = portfolioAssetAllocationRepository.findAll();
    if (CollectionUtils.isEmpty(allocations)) {
      return List.of();
    }
    Map<Long, String> priceSourceByAssetId =
        assetRepository
            .findAllById(
                allocations.stream()
                    .map(PortfolioAssetAllocationEntity::getAssetId)
                    .filter(Objects::nonNull)
                    .toList())
            .stream()
            .collect(Collectors.toMap(AssetEntity::getId, AssetEntity::getPriceSource));

    double totalValue =
        allocations.stream()
            .mapToDouble(allocation -> nz(allocation.getTotalValueInBaseCurrency()))
            .sum();
    return allocations.stream()
        .filter(allocation -> Math.abs(nz(allocation.getTotalValueInBaseCurrency())) > 0.005)
        .sorted(
            Comparator.comparing(
                    (PortfolioAssetAllocationEntity allocation) ->
                        nz(allocation.getTotalValueInBaseCurrency()))
                .reversed())
        .map(
            allocation -> {
              return new OpenPositionValue(
                  allocation.getAssetSymbol(),
                  nz(allocation.getTotalVolume()),
                  nz(allocation.getCostBasisInBaseCurrency()),
                  nz(allocation.getTotalVolume()) > 0.005
                      ? nz(allocation.getCostBasisInBaseCurrency())
                          / nz(allocation.getTotalVolume())
                      : 0.0,
                  nz(allocation.getMarketPrice()),
                  allocation.getMarketPriceCurrency(),
                  nz(allocation.getTotalValueInBaseCurrency()),
                  nz(allocation.getUnrealizedPlInBaseCurrency()),
                  positionProfitLossPercent(
                      nz(allocation.getUnrealizedPlInBaseCurrency()),
                      nz(allocation.getCostBasisInBaseCurrency())),
                  allocation.getBaseCurrency(),
                  Math.abs(totalValue) > 0.005
                      ? nz(allocation.getTotalValueInBaseCurrency()) / totalValue * 100.0
                      : 0.0,
                  priceSourceByAssetId.get(allocation.getAssetId()));
            })
        .toList();
  }

  private Double positionProfitLossPercent(double unrealized, double costBase) {
    return Math.abs(costBase) > 0.005 ? unrealized / costBase * 100.0 : null;
  }

  private List<DividendGainer> calculateDividendGainers(CurrencyType baseCurrency) {
    List<CashOperationEntity> cashOperations = cashOperationRepository.findAll();
    if (CollectionUtils.isEmpty(cashOperations)) {
      List<DividendGainer> cached =
          symbolPerformanceRepository.findAll().stream()
              .filter(row -> Math.abs(nz(row.getDividends())) > 0.005)
              .sorted(Comparator.comparing(SymbolPerformanceEntity::getDividends).reversed())
              .map(row -> new DividendGainer(row.getSymbol(), nz(row.getDividends())))
              .toList();
      return collapseDividendGainers(cached);
    }

    Map<String, Double> dividendsBySymbolInBase = new HashMap<>();
    for (CashOperationEntity operation : cashOperations) {
      if (operation.getType() != CashOperationType.DIVIDEND
          || !org.springframework.util.StringUtils.hasText(operation.getSymbol())) {
        continue;
      }
      CurrencyType currency =
          operation.getCurrency() != null ? operation.getCurrency() : baseCurrency;
      LocalDate rateDate =
          operation.getDate() != null ? operation.getDate().toLocalDate() : LocalDate.now();
      double amountInBase =
          currencyRateService.convertToBaseCurrency(
              nz(operation.getAmount()), baseCurrency, currency, rateDate);
      dividendsBySymbolInBase.merge(operation.getSymbol(), amountInBase, Double::sum);
    }

    List<DividendGainer> sortedRows =
        dividendsBySymbolInBase.entrySet().stream()
            .filter(entry -> Math.abs(entry.getValue()) > 0.005)
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(entry -> new DividendGainer(entry.getKey(), entry.getValue()))
            .toList();

    return collapseDividendGainers(sortedRows);
  }

  private List<DividendGainer> collapseDividendGainers(List<DividendGainer> rows) {
    if (rows.size() <= 10) {
      return rows;
    }
    List<DividendGainer> topRows = new ArrayList<>(rows.subList(0, 9));
    double otherDividends =
        rows.subList(9, rows.size()).stream().mapToDouble(DividendGainer::getDividends).sum();
    topRows.add(new DividendGainer("Other", otherDividends));
    return topRows;
  }

  private boolean applyAccountStatisticsCurrencyBreakdowns(
      Portfolio portfolio, boolean updateTotals) {
    List<AccountStatisticsEntity> stats = accountStatisticsRepository.findAll();
    Set<Long> cashOnlyAccounts = cashOnlyAccountIds();
    stats = stats.stream().filter(stat -> !cashOnlyAccounts.contains(stat.getAccountId())).toList();
    if (CollectionUtils.isEmpty(stats)) {
      return false;
    }

    portfolio.getRealizedByCurrency().clear();
    portfolio.getUnrealizedByCurrency().clear();
    portfolio.getDividendsByCurrency().clear();

    double realizedInBase = 0.0;
    double unrealizedInBase = 0.0;
    double dividendsInBase = 0.0;
    for (AccountStatisticsEntity stat : stats) {
      // Every monetary value in account_statistics is already expressed in
      // valuation_currency (normally the portfolio base). AccountEntity currency is only
      // display metadata and must never be used to relabel these converted values.
      CurrencyType currency = CurrencyType.valueOf(stat.getValuationCurrency());
      double realized = nz(stat.getRealizedProfit());
      double unrealized = nz(stat.getUnrealizedProfit());
      double dividends = nz(stat.getDividends());
      portfolio.getRealizedByCurrency().merge(currency, realized, Double::sum);
      portfolio.getUnrealizedByCurrency().merge(currency, unrealized, Double::sum);
      portfolio.getDividendsByCurrency().merge(currency, dividends, Double::sum);
      realizedInBase += realized;
      unrealizedInBase += unrealized;
      dividendsInBase += dividends;
    }
    if (updateTotals) {
      portfolio.setRealizedProfit(realizedInBase);
      portfolio.setUnrealizedProfit(unrealizedInBase);
      portfolio.setDividends(dividendsInBase);
      portfolio.setTotalProfit(realizedInBase + unrealizedInBase + dividendsInBase);
    }
    return true;
  }

  private boolean applyPortfolioCurrencyBreakdowns(Portfolio portfolio, boolean updateTotals) {
    List<PortfolioCurrencyBreakdownEntity> rows = portfolioCurrencyBreakdownRepository.findAll();
    if (CollectionUtils.isEmpty(rows)) {
      return false;
    }

    portfolio.getRealizedByCurrency().clear();
    portfolio.getUnrealizedByCurrency().clear();
    portfolio.getDividendsByCurrency().clear();

    double realizedInBase = 0.0;
    double unrealizedInBase = 0.0;
    double dividendsInBase = 0.0;
    boolean hasSupportedMetric = false;
    for (PortfolioCurrencyBreakdownEntity row : rows) {
      CurrencyType currency = row.getCurrency();
      double localAmount = nz(row.getAmountLocal());
      double baseAmount = nz(row.getAmountInBaseCurrency());
      switch (row.getMetricType()) {
        case "REALIZED" -> {
          hasSupportedMetric = true;
          portfolio.getRealizedByCurrency().merge(currency, localAmount, Double::sum);
          realizedInBase += baseAmount;
        }
        case "UNREALIZED" -> {
          hasSupportedMetric = true;
          portfolio.getUnrealizedByCurrency().merge(currency, localAmount, Double::sum);
          unrealizedInBase += baseAmount;
        }
        case "DIVIDENDS" -> {
          hasSupportedMetric = true;
          portfolio.getDividendsByCurrency().merge(currency, localAmount, Double::sum);
          dividendsInBase += baseAmount;
        }
        default -> {
          // Ignore unknown projection metric rows; the MV owns the supported set.
        }
      }
    }

    if (!hasSupportedMetric) {
      return false;
    }

    if (updateTotals) {
      portfolio.setRealizedProfit(realizedInBase);
      portfolio.setUnrealizedProfit(unrealizedInBase);
      portfolio.setDividends(dividendsInBase);
      portfolio.setTotalProfit(realizedInBase + unrealizedInBase + dividendsInBase);
    }
    return true;
  }

  private Map<Long, AccountEntity> accountsById(Collection<Long> accountIds) {
    return accountRepository.findMapByIdIn(accountIds);
  }

  private void applyOpenPositionUnrealizedCurrencyBreakdown(
      Portfolio portfolio, boolean updateTotals) {
    portfolio.getUnrealizedByCurrency().clear();
    double unrealizedProfit = 0.0;
    LocalDate rateDate = LocalDate.now();
    for (OpenedPosition position : openedPositionRepository.findAll()) {
      CurrencyType profitCurrency = position.getProfitCurrency();
      CurrencyType commissionCurrency = position.getCommissionCurrency();
      double profitAndSwap = nz(position.getProfit()) + nz(position.getSwap());
      double commission = nz(position.getCommission());
      portfolio.getUnrealizedByCurrency().merge(profitCurrency, profitAndSwap, Double::sum);
      portfolio.getUnrealizedByCurrency().merge(commissionCurrency, commission, Double::sum);
      unrealizedProfit +=
          currencyRateService.convertToBaseCurrency(
              profitAndSwap, portfolio.getBaseCurrency(), profitCurrency, rateDate);
      unrealizedProfit +=
          currencyRateService.convertToBaseCurrency(
              commission, portfolio.getBaseCurrency(), commissionCurrency, rateDate);
    }
    if (updateTotals) {
      portfolio.setUnrealizedProfit(unrealizedProfit);
      portfolio.setTotalProfit(
          portfolio.getRealizedProfit() + unrealizedProfit + portfolio.getDividends());
    }
  }

  private Double localBalance(
      double balance, CurrencyType baseCurrency, CurrencyType localCurrency, LocalDate rateDate) {
    if (localCurrency == baseCurrency) {
      return balance;
    }
    // AccountEntity statistics are already in the portfolio base currency. The FX board stores
    // units of local currency per one base-currency unit, so display conversion multiplies.
    return currencyRateService.findRate(baseCurrency, localCurrency, rateDate).stream()
        .map(rate -> balance * rate)
        .findFirst()
        .orElse(balance);
  }

  private OpenPositionValue openPositionValuesTotal(
      List<OpenPositionValue> positions, CurrencyType baseCurrency) {
    double value = positions.stream().mapToDouble(OpenPositionValue::getValue).sum();
    double unrealized = positions.stream().mapToDouble(OpenPositionValue::getUnrealized).sum();
    return new OpenPositionValue(
        "Total",
        0.0,
        0.0,
        0.0,
        0.0,
        baseCurrency,
        value,
        unrealized,
        positionProfitLossPercent(
            unrealized, positions.stream().mapToDouble(OpenPositionValue::getCostBase).sum()),
        baseCurrency,
        100.0,
        null);
  }

  private Set<Long> cashOnlyAccountIds() {
    return accountRepository.findAll().stream()
        .filter(AccountEntity::isCashOnly)
        .map(AccountEntity::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  // 2. Monthly Performance
  public Performance calculateMonthlyPerformance() {
    return performanceQueryService.calculateMonthlyPerformance();
    /*
    Performance performance = new Performance();
    Map<String, Double> monthlyProfit = new TreeMap<>();
    Map<String, Double> monthlyCashflow = new TreeMap<>();
    Map<String, Set<Long>> monthlyAccounts = new TreeMap<>();
    Map<String, MonthlyAttribution> attributions = new TreeMap<>();
    Set<Long> cashOnlyAccounts = cashOnlyAccountIds();
    Set<Long> visibleAccounts =
        accountStatisticsRepository.findAll().stream()
            .filter(stat -> stat.getAccountId() != null)
            .filter(stat -> !cashOnlyAccounts.contains(stat.getAccountId()))
            .filter(this::hasVisibleAccountSurface)
            .map(AccountStatisticsEntity::getAccountId)
            .collect(Collectors.toCollection(TreeSet::new));
    boolean filterVisibleAccounts = !visibleAccounts.isEmpty();

    for (PortfolioMonthlyPerformanceEntity row :
        portfolioMonthlyPerformanceRepository.findAllByOrderByMonthAscPortfolioIdAsc()) {
      String bucketKey = summaryBucketKey(row.getMonth());
      monthlyProfit.merge(bucketKey, nz(row.getProfit()), Double::sum);
      monthlyCashflow.merge(bucketKey, nz(row.getNetCashflow()), Double::sum);
      MonthlyAttribution old = attributions.get(bucketKey);
      double profit = nz(row.getProfit());
      double realized = nz(row.getRealizedProfit());
      double dividends = nz(row.getDividends());
      double interest = nz(row.getInterest());
      double fees = nz(row.getFees());
      double taxes = nz(row.getTaxes());
      double marketFx = profit - realized - dividends - interest - fees - taxes;
      attributions.put(
          bucketKey,
          old == null
              ? new MonthlyAttribution(
                  bucketKey,
                  nz(row.getStartEquity()),
                  nz(row.getEndEquity()),
                  nz(row.getDepositFlow()),
                  nz(row.getWithdrawalFlow()),
                  row.getNetCashflow(),
                  profit,
                  marketFx,
                  realized,
                  dividends,
                  interest,
                  fees,
                  taxes,
                  0.0,
                  0.0,
                  new ArrayList<>())
              : new MonthlyAttribution(
                  bucketKey,
                  old.openingEquity(),
                  nz(row.getEndEquity()),
                  old.deposits() + nz(row.getDepositFlow()),
                  old.withdrawals() + nz(row.getWithdrawalFlow()),
                  old.netExternalFlow() + row.getNetCashflow(),
                  old.totalProfit() + profit,
                  old.marketAndFxMovement() + marketFx,
                  old.realizedTradingResult() + realized,
                  old.dividends() + dividends,
                  old.cashInterest() + interest,
                  old.fees() + fees,
                  old.taxes() + taxes,
                  0.0,
                  0.0,
                  old.accounts()));
    }

    for (AccountMonthlyPerformanceEntity row :
        accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc()) {
      if (filterVisibleAccounts && !visibleAccounts.contains(row.getAccountId())) {
        continue;
      }
      String bucketKey = summaryBucketKey(row.getMonth());
      if (Math.abs(nz(row.getEndEquity())) >= 50.0
          || Math.abs(nz(row.getProfit())) >= 0.005
          || Math.abs(nz(row.getNetCashflow())) >= 0.005) {
        monthlyAccounts
            .computeIfAbsent(bucketKey, ignored -> new TreeSet<>())
            .add(row.getAccountId());
      }
      MonthlyAttribution a = attributions.get(bucketKey);
      if (a != null) {
        double contribution = nz(row.getProfit());
        double pct =
            Math.abs(a.totalProfit()) < 0.000001 ? 0.0 : contribution / a.totalProfit() * 100.0;
        List<MonthlyAttribution.AccountContribution> accounts = new ArrayList<>(a.accounts());
        accounts.add(
            new MonthlyAttribution.AccountContribution(
                String.valueOf(row.getAccountId()),
                nz(row.getStartEquity()),
                nz(row.getEndEquity()),
                row.getNetCashflow(),
                contribution,
                pct));
        attributions.put(
            bucketKey,
            new MonthlyAttribution(
                a.period(),
                a.openingEquity(),
                a.closingEquity(),
                a.deposits(),
                a.withdrawals(),
                a.netExternalFlow(),
                a.totalProfit(),
                a.marketAndFxMovement(),
                a.realizedTradingResult(),
                a.dividends(),
                a.cashInterest(),
                a.fees(),
                a.taxes(),
                a.valuationAdjustments(),
                a.unresolvedResidual(),
                accounts));
      }
    }

    Map<String, Long> monthlyOps =
        monthlyAccounts.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> (long) entry.getValue().size(),
                    (existing, ignored) -> existing,
                    TreeMap::new));

    performance.setCalculateMonthlyPerformance(monthlyProfit);
    performance.setMonthlyOperationsCount(monthlyOps);
    performance.setMonthlyCashflow(monthlyCashflow);
    performance.setMonthlyAttributions(attributions);
    return performance;
    */
  }

  /**
   * Buckets a summary row for the monthly performance chart. Keep every source month. The chart
   * performs annual and quarterly grouping in the browser.
   */
  private String summaryBucketKey(LocalDate month) {
    return String.format("%d-%02d", month.getYear(), month.getMonthValue());
  }

  private boolean hasVisibleAccountSurface(AccountStatisticsEntity stat) {
    return Math.abs(nz(stat.getCashBalance()) + nz(stat.getMarketValue()))
            > ACCOUNT_VISIBILITY_MIN_VALUE
        || Math.abs(nz(stat.getNetDeposit())) > ACCOUNT_VISIBILITY_MIN_VALUE;
  }

  // 4. Win Rate (percentage of profitable trades)
  public double calculateWinRate() {
    return performanceQueryService.calculateWinRate();
    /*
    List<ClosedPosition> closedPositions = closedPositionRepository.findAll();
    long totalPositions = closedPositions.size();
    if (totalPositions == 0) {
      return 0.0;
    }
    long profitablePositions =
        closedPositions.stream().filter(position -> position.getProfit() > 0).count();

    return (double) profitablePositions / totalPositions * 100;
    */
  }

  public List<InstrumentPerformance> calculatePerformancePerInstrument() {
    return performanceQueryService.calculatePerformancePerInstrument();
    /*
    List<InstrumentPerformance> instrumentPerformances =
        symbolPerformanceRepository.findAll().stream()
            .map(
                row ->
                    new InstrumentPerformance(
                        row.getSymbol(),
                        nz(row.getClosedProfit()),
                        nz(row.getUnrealizedProfit()),
                        nz(row.getTotalProfit()),
                        nz(row.getDividends()),
                        nz(row.getWithholdingTax()),
                        nz(row.getMarketValue()),
                        nz(row.getCostBasis())))
            .sorted(Comparator.comparing(InstrumentPerformance::getTotal))
            .toList();

    double totalSum =
        instrumentPerformances.stream()
            .filter(Objects::nonNull)
            .mapToDouble(InstrumentPerformance::getTotal)
            .sum();
    double threshold = totalSum * OTHER_BUCKET_RATIO;

    List<InstrumentPerformance> major = new ArrayList<>();
    double otherClosed = 0.0;
    double otherUnrealized = 0.0;
    double otherDividends = 0.0;
    double otherTax = 0.0;
    double otherMarketValue = 0.0;
    double otherCostBasis = 0.0;

    for (InstrumentPerformance dto : instrumentPerformances) {
      if (Math.abs(dto.getTotal()) >= Math.abs(threshold)) {
        major.add(dto);
      } else {
        otherClosed += dto.getClosedProfit();
        otherUnrealized += dto.getUnrealizedProfit();
        otherDividends += dto.getDividends();
        otherTax += dto.getWithholdingTax();
        otherMarketValue += dto.getMarketValue();
        otherCostBasis += dto.getCostBasis();
      }
    }
    major =
        major.stream()
            .sorted(Comparator.comparingDouble(InstrumentPerformance::getTotal).reversed())
            .collect(Collectors.toList());
    if (otherClosed != 0.0 || otherUnrealized != 0.0 || otherDividends != 0.0 || otherTax != 0.0) {
      major.add(
          new InstrumentPerformance(
              "Other",
              otherClosed,
              otherUnrealized,
              otherClosed + otherUnrealized + otherDividends - otherTax,
              otherDividends,
              otherTax,
              otherMarketValue,
              otherCostBasis));
    }

    return major;
    */
  }

  // 8. Dividends Received (if modeled)
  //    public double calculateDividendsReceived() {
  //        List<ClosedPosition> closedPositions = closedPositionRepository.findAll();
  //
  //        return closedPositions.stream()
  //                .filter(ClosedPosition::isDividend)
  //                .mapToDouble(ClosedPosition::getDividendAmount)
  //                .sum();
  //    }
  private record AccountNetDeposit(double localAmount, double baseAmount) {}

  public DailyPerformanceDetail dailyPerformanceDetail(LocalDate date, Set<Long> accountIds) {
    return performanceQueryService.dailyPerformanceDetail(date, accountIds);
    /*
    List<com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity> rows =
        accountDailyRepository.findAllByOrderByDateAscAccountIdAsc().stream()
            .filter(row -> date.equals(row.getDate()))
            .filter(
                row ->
                    accountIds == null
                        || accountIds.isEmpty()
                        || accountIds.contains(row.getAccountId()))
            .toList();
    double closing = rows.stream().mapToDouble(row -> nz(row.getEquity())).sum();
    double profit = rows.stream().mapToDouble(row -> nz(row.getDailyProfitAmount())).sum();
    double deposits = rows.stream().mapToDouble(row -> nz(row.getDeposits())).sum();
    double withdrawals = rows.stream().mapToDouble(row -> nz(row.getWithdrawals())).sum();
    double dividends = rows.stream().mapToDouble(row -> nz(row.getDividends())).sum();
    double interest = rows.stream().mapToDouble(row -> nz(row.getInterest())).sum();
    double fees = rows.stream().mapToDouble(row -> nz(row.getFees())).sum();
    double taxes = rows.stream().mapToDouble(row -> nz(row.getTaxes())).sum();
    List<DailyPerformanceDetail.AccountRow> accounts =
        rows.stream()
            .map(
                row ->
                    new DailyPerformanceDetail.AccountRow(
                        row.getAccountId(),
                        nz(row.getEquity())
                            - nz(row.getDailyProfitAmount())
                            - nz(row.getDeposits())
                            + nz(row.getWithdrawals()),
                        nz(row.getEquity()),
                        nz(row.getDailyProfitAmount()),
                        nz(row.getDeposits()),
                        nz(row.getWithdrawals())))
            .toList();
    double residual = profit - dividends - interest - fees - taxes;
    return new DailyPerformanceDetail(
        date,
        profit,
        rows.stream().mapToDouble(row -> nz(row.getDailyReturn())).sum(),
        closing - profit - deposits + withdrawals,
        closing,
        deposits,
        withdrawals,
        dividends,
        interest,
        fees,
        taxes,
        residual,
        accounts,
        "Daily account_daily data cannot separate price movement from FX; residual is combined market/FX movement.");
    */
  }
}
