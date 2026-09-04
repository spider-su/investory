package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.api.reporting.model.DailyPerformanceDetail;
import com.smartbox.investory.investment.api.reporting.model.InstrumentPerformance;
import com.smartbox.investory.investment.api.reporting.model.MonthlyAttribution;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyAttributionRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioMonthlySummaryRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.performance.model.Performance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read queries for portfolio performance history and attribution. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioPerformanceQueryService {
  private final PositionRepository closedPositionRepository;
  private final AccountDailyRepository accountDailyRepository;
  private final AccountRepository accountRepository;
  private final PortfolioMonthlySummaryRepository portfolioMonthlySummaryRepository;
  private final AccountMonthlyAttributionRepository accountMonthlyAttributionRepository;
  private final SymbolPerformanceRepository symbolPerformanceRepository;

  public Performance calculateMonthlyPerformance(Long portfolioId) {
    requirePortfolioId(portfolioId);
    Performance performance = new Performance();
    Map<String, Double> monthlyProfit = new TreeMap<>();
    Map<String, Double> monthlyCashflow = new TreeMap<>();
    Map<String, Long> monthlyOps = new TreeMap<>();
    Map<String, MonthlyAttribution> attributions = new TreeMap<>();
    var accountAttributions =
        accountMonthlyAttributionRepository.findByPortfolioIdOrderByMonthAscAccountIdAsc(
            portfolioId);
    for (var row :
        portfolioMonthlySummaryRepository.findByPortfolioIdOrderByMonthAsc(portfolioId)) {
      String bucketKey = summaryBucketKey(row.getMonth());
      BigDecimal profit = nz(row.getTotalProfit());
      monthlyProfit.put(bucketKey, profit.doubleValue());
      monthlyCashflow.put(bucketKey, nz(row.getNetExternalFlow()).doubleValue());
      monthlyOps.put(bucketKey, row.getActiveAccountCount());
      BigDecimal realized = nz(row.getRealized());
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
          new MonthlyAttribution(
              bucketKey,
              nz(row.getOpeningEquity()).doubleValue(),
              nz(row.getClosingEquity()).doubleValue(),
              nz(row.getDeposits()).doubleValue(),
              nz(row.getWithdrawals()).doubleValue(),
              nz(row.getNetExternalFlow()).doubleValue(),
              profit.doubleValue(),
              marketFx.doubleValue(),
              realized.doubleValue(),
              dividends.doubleValue(),
              interest.doubleValue(),
              fees.doubleValue(),
              taxes.doubleValue(),
              0.0,
              0.0,
              accountAttributions.stream()
                  .filter(a -> bucketKey.equals(summaryBucketKey(a.getMonth())))
                  .map(
                      a ->
                          new MonthlyAttribution.AccountContribution(
                              String.valueOf(a.getAccountId()),
                              nz(a.getOpeningEquity()).doubleValue(),
                              nz(a.getClosingEquity()).doubleValue(),
                              nz(a.getNetCashflow()).doubleValue(),
                              nz(a.getProfit()).doubleValue(),
                              nz(a.getContributionPct()).doubleValue()))
                  .toList()));
    }

    performance.setCalculateMonthlyPerformance(monthlyProfit);
    performance.setMonthlyOperationsCount(monthlyOps);
    performance.setMonthlyCashflow(monthlyCashflow);
    performance.setMonthlyAttributions(attributions);
    return performance;
  }

  public double calculateWinRate(Long portfolioId) {
    requirePortfolioId(portfolioId);
    List<PositionEntity> closedPositions =
        closedPositionRepository.findClosedByAccountIn(
            accountRepository.findAllByPortfolioId(portfolioId).stream()
                .map(AccountEntity::getId)
                .toList());
    return winRate(closedPositions);
  }

  private double winRate(List<PositionEntity> closedPositions) {
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

  public List<InstrumentPerformance> calculatePerformancePerInstrument(Long portfolioId) {
    requirePortfolioId(portfolioId);
    return symbolPerformanceRepository.findAllByPortfolioId(portfolioId).stream()
        .map(this::toInstrumentPerformance)
        .sorted(Comparator.comparingDouble(InstrumentPerformance::getTotal).reversed())
        .toList();
  }

  public DailyPerformanceDetail dailyPerformanceDetail(
      Long portfolioId, LocalDate date, Set<Long> accountIds) {
    requirePortfolioId(portfolioId);
    Set<Long> scopedAccounts =
        portfolioId == null
            ? Set.of()
            : accountRepository.findAllByPortfolioId(portfolioId).stream()
                .map(AccountEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    Set<Long> selected =
        accountIds == null || accountIds.isEmpty()
            ? scopedAccounts
            : (portfolioId == null
                ? accountIds
                : accountIds.stream().filter(scopedAccounts::contains).collect(Collectors.toSet()));
    List<AccountDailyEntity> rows =
        portfolioId == null && (accountIds == null || accountIds.isEmpty())
            ? accountDailyRepository.findByDateOrderByAccountIdAsc(date)
            : accountDailyRepository.findByDateAndAccountIdInOrderByAccountIdAsc(date, selected);
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

  private static void requirePortfolioId(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
  }
}
