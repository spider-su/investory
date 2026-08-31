package com.smartbox.investory.investment.performance;

import com.smartbox.investory.investment.api.reporting.model.*;
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
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.cash.CashFlowAggregator;
import com.smartbox.investory.investment.ledger.cash.CashOperationNormalizer;
import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import com.smartbox.investory.investment.ledger.cash.XtbAccountFundingCalculator;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationRepository;
import com.smartbox.investory.investment.ledger.cash.persistence.NormalizedCashOperationRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.performance.model.*;
import com.smartbox.investory.investment.reporting.PortfolioPerformanceQueryService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
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
public class PortfolioMetricsService {

  private static final BigDecimal ACCOUNT_VISIBILITY_MIN_VALUE = BigDecimal.valueOf(50);
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
  private final PositionRepository closedPositionRepository;
  private final PositionRepository openedPositionRepository;
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
  private final PortfolioRiskExposureCalculator riskExposureCalculator;

  private static double nz(BigDecimal value) {
    return value == null ? 0.0 : value.doubleValue();
  }

  private static BigDecimal bd(Double value) {
    return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
  }

  private static BigDecimal bd(BigDecimal value) {
    return com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(value);
  }

  private static double display(BigDecimal value) {
    return value.doubleValue();
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
      riskExposureCalculator.applyTo(portfolio);
    }
    if (!kpiApplied && !applyPortfolioCurrencyTotals(portfolio)) {
      boolean usedAccountStatistics = applyAccountStatisticsTotals(portfolio);
      if (!usedAccountStatistics) {
        applyOpenPositionUnrealizedTotal(portfolio);
      }
    }
    applyInvestmentProfitFromComponents(portfolio);

    // Estimated capital-gains tax ("Belka" 19%) for the CURRENT tax year only, applying
    // loss carry-forward from prior years (Polish rule: losses deductible over the next 5 years).
    TaxCalculator.TaxSummary tax =
        taxCalculator.calculate(closedPositionRepository.findClosed(), portfolio.getBaseCurrency());
    portfolio.setCapitalGainsTax(tax.capitalGainsTax().doubleValue());
    portfolio.setLossCarryForward(tax.lossCarryForward().doubleValue());

    // Exchange rates for the currencies board (units of each currency per 1 base currency)
    for (CurrencyType currency : CurrencyType.values()) {
      if (currency == portfolio.getBaseCurrency()) {
        continue;
      }
      Optional<BigDecimal> rate =
          currencyRateService.findRate(portfolio.getBaseCurrency(), currency);
      if (rate.isPresent()) {
        portfolio.getExchangeRates().put(currency, rate.get().doubleValue());
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
                  bd(number(row[1]).doubleValue())
                      .abs()
                      .add(bd(number(row[2]).doubleValue()).abs())
                      .add(bd(number(row[3]).doubleValue()).abs())
                      .add(bd(number(row[4]).doubleValue()).abs())
                      .doubleValue());
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
    BigDecimal realized = bd(kpi.getTotalRealizedProfit());
    BigDecimal unrealized = bd(kpi.getTotalUnrealizedProfit());
    BigDecimal dividends = bd(kpi.getTotalDividends());
    BigDecimal deposits = bd(kpi.getTotalDeposits());
    BigDecimal netDeposits = bd(kpi.getNetDeposits());
    BigDecimal balance = bd(kpi.getTotalEquity());
    BigDecimal cash = bd(kpi.getTotalCash());

    portfolio.setBaseCurrency(baseCurrency);
    portfolio.setRealizedProfit(display(realized));
    portfolio.setUnrealizedProfit(display(unrealized));
    portfolio.setDividends(display(dividends));
    portfolio.setDeposits(display(deposits));
    portfolio.setNetDeposits(display(netDeposits));
    portfolio.setWithdrawals(display(deposits.subtract(netDeposits)));
    portfolio.setCash(display(cash));
    portfolio.setBalance(display(balance));
    portfolio.setRoi(
        netDeposits.signum() > 0
            ? display(
                balance
                    .subtract(netDeposits)
                    .divide(netDeposits, 16, java.math.RoundingMode.HALF_UP)
                    .movePointRight(2))
            : 0.0);

    return true;
  }

  private void applyCashFlowSupplement(Portfolio portfolio) {
    List<AccountStatisticsEntity> stats = accountStatisticsRepository.findAll();
    Set<Long> cashOnlyAccounts = cashOnlyAccountIds();
    stats = stats.stream().filter(stat -> !cashOnlyAccounts.contains(stat.getAccountId())).toList();
    portfolio.setInterest(
        display(
            stats.stream()
                .map(stat -> bd(stat.getInterest()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
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
    List<PositionEntity> closedPositions = closedPositionRepository.findClosed();
    List<CashOperationEntity> cashOperations = cashOperationRepository.findAll();

    // ── Realized P/L from imported closed positions ──────────────────────
    for (PositionEntity position : closedPositions) {
      LocalDate rateDate =
          position.getCloseTime() != null ? position.getCloseTime().toLocalDate() : LocalDate.now();
      CurrencyType profitCurrency = position.getProfitCurrency();
      CurrencyType commissionCurrency = position.getCommissionCurrency();
      BigDecimal profitAndSwap = bd(position.getProfit()).add(bd(position.getSwap()));
      BigDecimal commission = bd(position.getCommission());
      portfolio.setRealizedProfit(
          display(
              bd(portfolio.getRealizedProfit())
                  .add(
                      currencyRateService.convertToBaseCurrency(
                          profitAndSwap, portfolio.getBaseCurrency(), profitCurrency, rateDate))
                  .add(
                      currencyRateService.convertToBaseCurrency(
                          commission, portfolio.getBaseCurrency(), commissionCurrency, rateDate))));
    }

    // ── Unrealized P/L (live from open positions — always real-time) ─────
    for (PositionEntity position : openedPositionRepository.findOpen()) {
      CurrencyType profitCurrency = position.getProfitCurrency();
      CurrencyType commissionCurrency = position.getCommissionCurrency();
      BigDecimal profitAndSwap = bd(position.getProfit()).add(bd(position.getSwap()));
      BigDecimal commission = bd(position.getCommission());
      portfolio.setUnrealizedProfit(
          display(
              bd(portfolio.getUnrealizedProfit())
                  .add(
                      currencyRateService.convertToBaseCurrency(
                          profitAndSwap,
                          portfolio.getBaseCurrency(),
                          profitCurrency,
                          LocalDate.now()))
                  .add(
                      currencyRateService.convertToBaseCurrency(
                          commission,
                          portfolio.getBaseCurrency(),
                          commissionCurrency,
                          LocalDate.now()))));
    }

    // ── Dividends from imported cash operations ──────────────────────────
    BigDecimal dividendsTotal = BigDecimal.ZERO;
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
        BigDecimal amount = bd(cashOperation.getAmount());
        BigDecimal amountInBase =
            currencyRateService.convertToBaseCurrency(
                amount, portfolio.getBaseCurrency(), currency, rateDate);
        dividendsTotal = dividendsTotal.add(amountInBase);
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
        dividendsTotal.signum() > 0 ? display(dividendsTotal) : cashFlow.dividends().doubleValue());
    portfolio.setDividendTax(cashFlow.dividendTax().doubleValue());
    // Balance breakdown from account_statistics.
    List<AccountStatisticsEntity> stats = accountStatisticsRepository.findAll();
    Set<Long> cashOnlyAccounts = cashOnlyAccountIds();
    stats = stats.stream().filter(stat -> !cashOnlyAccounts.contains(stat.getAccountId())).toList();
    BigDecimal cash;
    BigDecimal balance;
    if (!stats.isEmpty()) {
      List<AccountStatisticsEntity> activeStats =
          stats.stream()
              .filter(
                  s ->
                      bd(s.getCashBalance())
                              .add(bd(s.getMarketValue()))
                              .abs()
                              .compareTo(ACCOUNT_VISIBILITY_MIN_VALUE)
                          >= 0)
              .toList();
      BigDecimal marketValue =
          activeStats.stream()
              .map(s -> bd(s.getMarketValue()))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      cash =
          activeStats.stream()
              .map(s -> bd(s.getCashBalance()))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      balance = marketValue.add(cash);
    } else {
      balance =
          openedPositionRepository.findOpen().stream()
              .map(
                  position ->
                      currencyRateService
                          .convertToBaseCurrency(
                              bd(position.getPurchaseValue()), portfolio.getBaseCurrency(),
                              position.getCostCurrency(), java.time.LocalDate.now())
                          .add(
                              currencyRateService.convertToBaseCurrency(
                                  bd(position.getProfit()).add(bd(position.getSwap())),
                                  portfolio.getBaseCurrency(),
                                  position.getProfitCurrency(),
                                  java.time.LocalDate.now()))
                          .add(
                              currencyRateService.convertToBaseCurrency(
                                  bd(position.getCommission()), portfolio.getBaseCurrency(),
                                  position.getCommissionCurrency(), java.time.LocalDate.now())))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      cash = BigDecimal.ZERO;
    }
    portfolio.setBalance(display(balance));
    portfolio.setCash(display(cash));
    BigDecimal netDep = bd(portfolio.getNetDeposits());
    portfolio.setRoi(
        netDep.signum() > 0
            ? display(
                balance
                    .subtract(netDep)
                    .divide(netDep, 16, java.math.RoundingMode.HALF_UP)
                    .movePointRight(2))
            : 0.0);
  }

  private void applyInvestmentProfitFromComponents(Portfolio portfolio) {
    // Keep the headline equal to the visible breakdown: realized + unrealized +
    // dividends after withholding tax + interest.
    BigDecimal totalProfit =
        bd(portfolio.getRealizedProfit())
            .add(bd(portfolio.getUnrealizedProfit()))
            .add(bd(portfolio.getDividends()))
            .add(bd(portfolio.getDividendTax()))
            .add(bd(portfolio.getInterest()));
    portfolio.setTotalProfit(display(totalProfit));
    BigDecimal netDeposits = bd(portfolio.getNetDeposits());
    portfolio.setRoi(
        netDeposits.signum() > 0
            ? display(
                totalProfit
                    .divide(netDeposits, 16, java.math.RoundingMode.HALF_UP)
                    .movePointRight(2))
            : 0.0);
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
                BigDecimal balance = bd(stat.getCashBalance()).add(bd(stat.getMarketValue()));
                AccountEntity account = accountsById.get(stat.getAccountId());
                AccountNetDeposit netDeposit =
                    netDepositsByAccount.getOrDefault(
                        stat.getAccountId(),
                        new AccountNetDeposit(
                            bd(stat.getAccountNetDeposit())
                                        .abs()
                                        .compareTo(BigDecimal.valueOf(0.005))
                                    > 0
                                ? bd(stat.getAccountNetDeposit())
                                : bd(stat.getNetDeposit()),
                            bd(stat.getNetDeposit())));
                CurrencyType localCurrency = account.getCurrency();
                BigDecimal localBalance =
                    localBalance(balance, baseCurrency, localCurrency, LocalDate.now());
                BigDecimal localCash =
                    localBalance(
                        bd(stat.getCashBalance()), baseCurrency, localCurrency, LocalDate.now());
                BigDecimal localNetDeposit = netDeposit.localAmount();
                BigDecimal baseNetDeposit = netDeposit.baseAmount();
                BigDecimal profit = balance.subtract(baseNetDeposit);
                BigDecimal localProfit = localBalance.subtract(localNetDeposit);
                return new AccountBalance(
                    stat.getAccountId(),
                    account.getName(),
                    display(localNetDeposit),
                    display(baseNetDeposit),
                    display(profit),
                    display(localProfit),
                    profitLossPercent(balance, baseNetDeposit),
                    display(balance),
                    nz(stat.getCashBalance()),
                    localCurrency,
                    display(localBalance),
                    display(localCash));
              })
          .toList();
    }

    return List.of();
  }

  private AccountBalance accountBalancesTotal(
      List<AccountBalance> accounts, CurrencyType baseCurrency, double canonicalNetDeposit) {
    BigDecimal balance =
        accounts.stream().map(a -> bd(a.getBalance())).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal cash =
        accounts.stream().map(a -> bd(a.getCash())).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal netDeposit = bd(canonicalNetDeposit);
    BigDecimal profit = balance.subtract(netDeposit);
    Double roi =
        netDeposit.abs().compareTo(ACCOUNT_VISIBILITY_MIN_VALUE) >= 0
            ? display(
                profit.divide(netDeposit, 16, java.math.RoundingMode.HALF_UP).movePointRight(2))
            : null;
    return new AccountBalance(
        null,
        "Total",
        display(netDeposit),
        display(netDeposit),
        display(profit),
        display(profit),
        roi,
        display(balance),
        display(cash),
        baseCurrency,
        display(balance),
        display(cash));
  }

  private boolean hasDashboardAccountSurface(AccountStatisticsEntity stat) {
    return bd(stat.getCashBalance())
                .add(bd(stat.getMarketValue()))
                .abs()
                .compareTo(ACCOUNT_VISIBILITY_MIN_VALUE)
            >= 0
        || bd(stat.getAccountNetDeposit()).abs().compareTo(ACCOUNT_VISIBILITY_MIN_VALUE) >= 0
        || bd(stat.getNetDeposit()).abs().compareTo(ACCOUNT_VISIBILITY_MIN_VALUE) >= 0;
  }

  private Map<Long, AccountNetDeposit> accountNetDeposits(
      Collection<Long> accountIds,
      Map<Long, AccountEntity> accountsById,
      CurrencyType baseCurrency) {
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
                                rows.stream()
                                    .map(row -> bd(row.getAmount()))
                                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                                rows.stream()
                                    .map(row -> bd(row.getAmountInBaseCurrency()))
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)))));
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
            BigDecimal baseAdjustment =
                currencyRateService.convertToBaseCurrency(
                    bd(adjustment), baseCurrency, account.getCurrency(), LocalDate.now());
            AccountNetDeposit current =
                deposits.getOrDefault(
                    accountId, new AccountNetDeposit(BigDecimal.ZERO, BigDecimal.ZERO));
            deposits.put(
                accountId,
                new AccountNetDeposit(
                    current.localAmount().add(bd(adjustment)),
                    current.baseAmount().add(baseAdjustment)));
          });
    }
    return deposits;
  }

  private Double profitLossPercent(BigDecimal balance, BigDecimal netDeposit) {
    if (balance.abs().compareTo(BigDecimal.valueOf(0.005)) < 0) {
      return 0.0;
    }
    return netDeposit.abs().compareTo(ACCOUNT_VISIBILITY_MIN_VALUE) >= 0
        ? display(
            balance
                .subtract(netDeposit)
                .divide(netDeposit, 16, java.math.RoundingMode.HALF_UP)
                .movePointRight(2))
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

    BigDecimal totalValue =
        allocations.stream()
            .map(a -> bd(a.getTotalValueInBaseCurrency()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return allocations.stream()
        .filter(
            allocation ->
                bd(allocation.getTotalValueInBaseCurrency())
                        .abs()
                        .compareTo(BigDecimal.valueOf(0.005))
                    > 0)
        .sorted(
            Comparator.comparing(
                    (PortfolioAssetAllocationEntity allocation) ->
                        nz(allocation.getTotalValueInBaseCurrency()))
                .reversed())
        .map(
            allocation -> {
              BigDecimal volume = bd(allocation.getTotalVolume());
              BigDecimal costBase = bd(allocation.getCostBasisInBaseCurrency());
              BigDecimal value = bd(allocation.getTotalValueInBaseCurrency());
              return new OpenPositionValue(
                  allocation.getAssetSymbol(),
                  volume,
                  costBase,
                  volume.compareTo(BigDecimal.valueOf(0.005)) > 0
                      ? costBase.divide(volume, 16, java.math.RoundingMode.HALF_UP)
                      : BigDecimal.ZERO,
                  bd(allocation.getMarketPrice()),
                  allocation.getMarketPriceCurrency(),
                  value,
                  bd(allocation.getUnrealizedPlInBaseCurrency()),
                  positionProfitLossPercent(
                      bd(allocation.getUnrealizedPlInBaseCurrency()),
                      bd(allocation.getCostBasisInBaseCurrency())),
                  allocation.getBaseCurrency(),
                  totalValue.abs().compareTo(BigDecimal.valueOf(0.005)) > 0
                      ? value
                          .divide(totalValue, 16, java.math.RoundingMode.HALF_UP)
                          .movePointRight(2)
                      : BigDecimal.ZERO,
                  priceSourceByAssetId.get(allocation.getAssetId()));
            })
        .toList();
  }

  private BigDecimal positionProfitLossPercent(BigDecimal unrealized, BigDecimal costBase) {
    return costBase.abs().compareTo(BigDecimal.valueOf(0.005)) > 0
        ? unrealized.divide(costBase, 16, java.math.RoundingMode.HALF_UP).movePointRight(2)
        : null;
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

    Map<String, BigDecimal> dividendsBySymbolInBase = new HashMap<>();
    for (CashOperationEntity operation : cashOperations) {
      if (operation.getType() != CashOperationType.DIVIDEND
          || !org.springframework.util.StringUtils.hasText(operation.getSymbol())) {
        continue;
      }
      CurrencyType currency =
          operation.getCurrency() != null ? operation.getCurrency() : baseCurrency;
      LocalDate rateDate =
          operation.getDate() != null ? operation.getDate().toLocalDate() : LocalDate.now();
      BigDecimal amountInBase =
          currencyRateService.convertToBaseCurrency(
              bd(operation.getAmount()), baseCurrency, currency, rateDate);
      dividendsBySymbolInBase.merge(operation.getSymbol(), amountInBase, BigDecimal::add);
    }

    List<DividendGainer> sortedRows =
        dividendsBySymbolInBase.entrySet().stream()
            .filter(entry -> entry.getValue().abs().compareTo(BigDecimal.valueOf(0.005)) > 0)
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .map(entry -> new DividendGainer(entry.getKey(), display(entry.getValue())))
            .toList();

    return collapseDividendGainers(sortedRows);
  }

  private List<DividendGainer> collapseDividendGainers(List<DividendGainer> rows) {
    if (rows.size() <= 10) {
      return rows;
    }
    List<DividendGainer> topRows = new ArrayList<>(rows.subList(0, 9));
    BigDecimal otherDividends =
        rows.subList(9, rows.size()).stream()
            .map(row -> bd(row.getDividends()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    topRows.add(new DividendGainer("Other", display(otherDividends)));
    return topRows;
  }

  private boolean applyAccountStatisticsTotals(Portfolio portfolio) {
    List<AccountStatisticsEntity> stats = accountStatisticsRepository.findAll();
    Set<Long> cashOnlyAccounts = cashOnlyAccountIds();
    stats = stats.stream().filter(stat -> !cashOnlyAccounts.contains(stat.getAccountId())).toList();
    if (CollectionUtils.isEmpty(stats)) {
      return false;
    }

    BigDecimal realizedInBase = BigDecimal.ZERO;
    BigDecimal unrealizedInBase = BigDecimal.ZERO;
    BigDecimal dividendsInBase = BigDecimal.ZERO;
    for (AccountStatisticsEntity stat : stats) {
      // Every monetary value in account_statistics is already expressed in
      // valuation_currency (normally the portfolio base). AccountEntity currency is only
      // display metadata and must never be used to relabel these converted values.
      BigDecimal realized = bd(stat.getRealizedProfit());
      BigDecimal unrealized = bd(stat.getUnrealizedProfit());
      BigDecimal dividends = bd(stat.getDividends());
      realizedInBase = realizedInBase.add(realized);
      unrealizedInBase = unrealizedInBase.add(unrealized);
      dividendsInBase = dividendsInBase.add(dividends);
    }
    portfolio.setRealizedProfit(display(realizedInBase));
    portfolio.setUnrealizedProfit(display(unrealizedInBase));
    portfolio.setDividends(display(dividendsInBase));
    portfolio.setTotalProfit(display(realizedInBase.add(unrealizedInBase).add(dividendsInBase)));
    return true;
  }

  private boolean applyPortfolioCurrencyTotals(Portfolio portfolio) {
    List<PortfolioCurrencyBreakdownEntity> rows = portfolioCurrencyBreakdownRepository.findAll();
    if (CollectionUtils.isEmpty(rows)) {
      return false;
    }

    BigDecimal realizedInBase = BigDecimal.ZERO;
    BigDecimal unrealizedInBase = BigDecimal.ZERO;
    BigDecimal dividendsInBase = BigDecimal.ZERO;
    boolean hasSupportedMetric = false;
    for (PortfolioCurrencyBreakdownEntity row : rows) {
      BigDecimal baseAmount = bd(row.getAmountInBaseCurrency());
      switch (row.getMetricType()) {
        case "REALIZED" -> {
          hasSupportedMetric = true;
          realizedInBase = realizedInBase.add(baseAmount);
        }
        case "UNREALIZED" -> {
          hasSupportedMetric = true;
          unrealizedInBase = unrealizedInBase.add(baseAmount);
        }
        case "DIVIDENDS" -> {
          hasSupportedMetric = true;
          dividendsInBase = dividendsInBase.add(baseAmount);
        }
        default -> {
          // Ignore unknown projection metric rows; the MV owns the supported set.
        }
      }
    }

    if (!hasSupportedMetric) {
      return false;
    }

    portfolio.setRealizedProfit(display(realizedInBase));
    portfolio.setUnrealizedProfit(display(unrealizedInBase));
    portfolio.setDividends(display(dividendsInBase));
    portfolio.setTotalProfit(display(realizedInBase.add(unrealizedInBase).add(dividendsInBase)));
    return true;
  }

  private Map<Long, AccountEntity> accountsById(Collection<Long> accountIds) {
    return accountRepository.findMapByIdIn(accountIds);
  }

  private void applyOpenPositionUnrealizedTotal(Portfolio portfolio) {
    BigDecimal unrealizedProfit = BigDecimal.ZERO;
    LocalDate rateDate = LocalDate.now();
    for (PositionEntity position : openedPositionRepository.findOpen()) {
      CurrencyType profitCurrency = position.getProfitCurrency();
      CurrencyType commissionCurrency = position.getCommissionCurrency();
      BigDecimal profitAndSwap = bd(position.getProfit()).add(bd(position.getSwap()));
      BigDecimal commission = bd(position.getCommission());
      unrealizedProfit =
          unrealizedProfit
              .add(
                  currencyRateService.convertToBaseCurrency(
                      profitAndSwap, portfolio.getBaseCurrency(), profitCurrency, rateDate))
              .add(
                  currencyRateService.convertToBaseCurrency(
                      commission, portfolio.getBaseCurrency(), commissionCurrency, rateDate));
    }
    portfolio.setUnrealizedProfit(display(unrealizedProfit));
    portfolio.setTotalProfit(
        display(
            bd(portfolio.getRealizedProfit())
                .add(unrealizedProfit)
                .add(bd(portfolio.getDividends()))));
  }

  private BigDecimal localBalance(
      BigDecimal balance,
      CurrencyType baseCurrency,
      CurrencyType localCurrency,
      LocalDate rateDate) {
    if (localCurrency == baseCurrency) {
      return balance;
    }
    // AccountEntity statistics are already in the portfolio base currency. The FX board stores
    // units of local currency per one base-currency unit, so display conversion multiplies.
    return currencyRateService.findRate(baseCurrency, localCurrency, rateDate).stream()
        .map(balance::multiply)
        .findFirst()
        .orElse(balance);
  }

  private OpenPositionValue openPositionValuesTotal(
      List<OpenPositionValue> positions, CurrencyType baseCurrency) {
    BigDecimal value =
        positions.stream()
            .map(position -> bd(position.getValue()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal unrealized =
        positions.stream()
            .map(position -> bd(position.getUnrealized()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal costBase =
        positions.stream()
            .map(position -> bd(position.getCostBase()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new OpenPositionValue(
        "Total",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        baseCurrency,
        value,
        unrealized,
        positionProfitLossPercent(unrealized, costBase),
        baseCurrency,
        BigDecimal.valueOf(100),
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
  }

  // 4. Win Rate (percentage of profitable trades)
  public double calculateWinRate() {
    return performanceQueryService.calculateWinRate();
  }

  public List<InstrumentPerformance> calculatePerformancePerInstrument() {
    return performanceQueryService.calculatePerformancePerInstrument();
  }

  private record AccountNetDeposit(BigDecimal localAmount, BigDecimal baseAmount) {}

  public DailyPerformanceDetail dailyPerformanceDetail(LocalDate date, Set<Long> accountIds) {
    return performanceQueryService.dailyPerformanceDetail(date, accountIds);
  }
}
