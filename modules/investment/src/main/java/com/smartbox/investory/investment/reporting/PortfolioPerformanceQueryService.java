package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.api.reporting.model.DailyPerformanceDetail;
import com.smartbox.investory.investment.api.reporting.model.InstrumentPerformance;
import com.smartbox.investory.investment.api.reporting.model.MonthlyAttribution;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.performance.model.Performance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read queries for portfolio performance history and attribution. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioPerformanceQueryService {
  private static final BigDecimal ACCOUNT_VISIBILITY_MIN_VALUE = BigDecimal.valueOf(50);

  private final PositionRepository closedPositionRepository;
  private final AccountDailyRepository accountDailyRepository;
  private final AccountRepository accountRepository;
  private final AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository;
  private final AccountStatisticsRepository accountStatisticsRepository;
  private final PortfolioMonthlyPerformanceRepository portfolioMonthlyPerformanceRepository;
  private final SymbolPerformanceRepository symbolPerformanceRepository;

  public Performance calculateMonthlyPerformance() {
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
      monthlyProfit.merge(bucketKey, nz(row.getProfit()).doubleValue(), Double::sum);
      monthlyCashflow.merge(bucketKey, nz(row.getNetCashflow()).doubleValue(), Double::sum);
      MonthlyAttribution old = attributions.get(bucketKey);
      BigDecimal profit = nz(row.getProfit());
      BigDecimal realized = nz(row.getRealizedProfit());
      BigDecimal dividends = nz(row.getDividends());
      BigDecimal interest = nz(row.getInterest());
      BigDecimal fees = nz(row.getFees());
      BigDecimal taxes = nz(row.getTaxes());
      BigDecimal marketFx =
          profit
              .subtract(realized)
              .subtract(dividends)
              .subtract(interest)
              .subtract(fees)
              .subtract(taxes);
      attributions.put(
          bucketKey,
          old == null
              ? new MonthlyAttribution(
                  bucketKey,
                  nz(row.getStartEquity()).doubleValue(),
                  nz(row.getEndEquity()).doubleValue(),
                  nz(row.getDepositFlow()).doubleValue(),
                  nz(row.getWithdrawalFlow()).doubleValue(),
                  nz(row.getNetCashflow()).doubleValue(),
                  profit.doubleValue(),
                  marketFx.doubleValue(),
                  realized.doubleValue(),
                  dividends.doubleValue(),
                  interest.doubleValue(),
                  fees.doubleValue(),
                  taxes.doubleValue(),
                  0.0,
                  0.0,
                  new ArrayList<>())
              : new MonthlyAttribution(
                  bucketKey,
                  old.openingEquity(),
                  nz(row.getEndEquity()).doubleValue(),
                  bd(old.deposits()).add(nz(row.getDepositFlow())).doubleValue(),
                  bd(old.withdrawals()).add(nz(row.getWithdrawalFlow())).doubleValue(),
                  bd(old.netExternalFlow()).add(nz(row.getNetCashflow())).doubleValue(),
                  bd(old.totalProfit()).add(profit).doubleValue(),
                  bd(old.marketAndFxMovement()).add(marketFx).doubleValue(),
                  bd(old.realizedTradingResult()).add(realized).doubleValue(),
                  bd(old.dividends()).add(dividends).doubleValue(),
                  bd(old.cashInterest()).add(interest).doubleValue(),
                  bd(old.fees()).add(fees).doubleValue(),
                  bd(old.taxes()).add(taxes).doubleValue(),
                  0.0,
                  0.0,
                  old.accounts()));
    }

    for (AccountMonthlyPerformanceEntity row :
        accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc()) {
      if (filterVisibleAccounts && !visibleAccounts.contains(row.getAccountId())) continue;
      String bucketKey = summaryBucketKey(row.getMonth());
      if (nz(row.getEndEquity()).abs().compareTo(ACCOUNT_VISIBILITY_MIN_VALUE) >= 0
          || nz(row.getProfit()).abs().compareTo(BigDecimal.valueOf(0.005)) >= 0
          || nz(row.getNetCashflow()).abs().compareTo(BigDecimal.valueOf(0.005)) >= 0) {
        monthlyAccounts
            .computeIfAbsent(bucketKey, ignored -> new TreeSet<>())
            .add(row.getAccountId());
      }
      MonthlyAttribution a = attributions.get(bucketKey);
      if (a != null) {
        BigDecimal contribution = nz(row.getProfit());
        BigDecimal totalProfit = bd(a.totalProfit());
        double pct =
            totalProfit.abs().compareTo(BigDecimal.valueOf(0.000001)) < 0
                ? 0.0
                : contribution
                    .divide(totalProfit, 12, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        List<MonthlyAttribution.AccountContribution> accounts = new ArrayList<>(a.accounts());
        accounts.add(
            new MonthlyAttribution.AccountContribution(
                String.valueOf(row.getAccountId()),
                nz(row.getStartEquity()).doubleValue(),
                nz(row.getEndEquity()).doubleValue(),
                nz(row.getNetCashflow()).doubleValue(),
                contribution.doubleValue(),
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
  }

  public double calculateWinRate() {
    List<PositionEntity> closedPositions = closedPositionRepository.findClosed();
    if (closedPositions.isEmpty()) return 0.0;
    long profitablePositions =
        closedPositions.stream()
            .filter(
                position ->
                    position.getProfit() != null
                        && position.getProfit().compareTo(BigDecimal.ZERO) > 0)
            .count();
    return (double) profitablePositions / closedPositions.size() * 100;
  }

  public List<InstrumentPerformance> calculatePerformancePerInstrument() {
    return symbolPerformanceRepository.findAll().stream()
        .map(this::toInstrumentPerformance)
        .sorted(Comparator.comparingDouble(InstrumentPerformance::getTotal).reversed())
        .toList();
  }

  public DailyPerformanceDetail dailyPerformanceDetail(LocalDate date, Set<Long> accountIds) {
    List<AccountDailyEntity> rows =
        accountIds == null || accountIds.isEmpty()
            ? accountDailyRepository.findByDateOrderByAccountIdAsc(date)
            : accountDailyRepository.findByDateAndAccountIdInOrderByAccountIdAsc(date, accountIds);
    BigDecimal closing = sum(rows, AccountDailyEntity::getEquity);
    BigDecimal profit = sum(rows, AccountDailyEntity::getDailyProfitAmount);
    BigDecimal deposits = sum(rows, AccountDailyEntity::getDeposits);
    BigDecimal withdrawals = sum(rows, AccountDailyEntity::getWithdrawals);
    BigDecimal dividends = sum(rows, AccountDailyEntity::getDividends);
    BigDecimal interest = sum(rows, AccountDailyEntity::getInterest);
    BigDecimal fees = sum(rows, AccountDailyEntity::getFees);
    BigDecimal taxes = sum(rows, AccountDailyEntity::getTaxes);
    List<DailyPerformanceDetail.AccountRow> accounts =
        rows.stream()
            .map(
                row ->
                    new DailyPerformanceDetail.AccountRow(
                        row.getAccountId(),
                        nz(row.getEquity())
                            .subtract(nz(row.getDailyProfitAmount()))
                            .subtract(nz(row.getDeposits()))
                            .add(nz(row.getWithdrawals()))
                            .doubleValue(),
                        nz(row.getEquity()).doubleValue(),
                        nz(row.getDailyProfitAmount()).doubleValue(),
                        nz(row.getDeposits()).doubleValue(),
                        nz(row.getWithdrawals()).doubleValue()))
            .toList();
    return new DailyPerformanceDetail(
        date,
        profit.doubleValue(),
        rows.stream().mapToDouble(row -> nz(row.getDailyReturn()).doubleValue()).sum(),
        closing.subtract(profit).subtract(deposits).add(withdrawals).doubleValue(),
        closing.doubleValue(),
        deposits.doubleValue(),
        withdrawals.doubleValue(),
        dividends.doubleValue(),
        interest.doubleValue(),
        fees.doubleValue(),
        taxes.doubleValue(),
        profit.subtract(dividends).subtract(interest).subtract(fees).subtract(taxes).doubleValue(),
        accounts,
        "Daily account_daily data cannot separate price movement from FX; residual is combined market/FX movement.");
  }

  private InstrumentPerformance toInstrumentPerformance(SymbolPerformanceEntity row) {
    return new InstrumentPerformance(
        row.getSymbol(),
        nz(row.getClosedProfit()).doubleValue(),
        nz(row.getUnrealizedProfit()).doubleValue(),
        nz(row.getTotalProfit()).doubleValue(),
        nz(row.getDividends()).doubleValue(),
        nz(row.getWithholdingTax()).doubleValue(),
        nz(row.getMarketValue()).doubleValue(),
        nz(row.getCostBasis()).doubleValue());
  }

  private Set<Long> cashOnlyAccountIds() {
    return accountRepository.findAll().stream()
        .filter(AccountEntity::isCashOnly)
        .map(AccountEntity::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private boolean hasVisibleAccountSurface(AccountStatisticsEntity stat) {
    return nz(stat.getCashBalance())
                .add(nz(stat.getMarketValue()))
                .abs()
                .compareTo(ACCOUNT_VISIBILITY_MIN_VALUE)
            > 0
        || nz(stat.getNetDeposit()).abs().compareTo(ACCOUNT_VISIBILITY_MIN_VALUE) > 0;
  }

  private String summaryBucketKey(LocalDate month) {
    return String.format("%d-%02d", month.getYear(), month.getMonthValue());
  }

  private static BigDecimal nz(Double value) {
    return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
  }

  private static BigDecimal nz(BigDecimal value) {
    return com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(value);
  }

  private static BigDecimal bd(double value) {
    return BigDecimal.valueOf(value);
  }

  private static <T> BigDecimal sum(
      List<T> values, java.util.function.Function<T, BigDecimal> getter) {
    return values.stream()
        .map(getter)
        .map(PortfolioPerformanceQueryService::nz)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
