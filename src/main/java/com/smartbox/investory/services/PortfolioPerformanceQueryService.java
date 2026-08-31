package com.smartbox.investory.services;

import com.smartbox.investory.infrastructure.repository.ClosedPosition;
import com.smartbox.investory.infrastructure.repository.ClosedPositionRepository;
import com.smartbox.investory.infrastructure.repository.account.Account;
import com.smartbox.investory.infrastructure.repository.account.AccountDaily;
import com.smartbox.investory.infrastructure.repository.account.AccountDailyRepository;
import com.smartbox.investory.infrastructure.repository.account.AccountMonthlyPerformance;
import com.smartbox.investory.infrastructure.repository.account.AccountMonthlyPerformanceRepository;
import com.smartbox.investory.infrastructure.repository.account.AccountRepository;
import com.smartbox.investory.infrastructure.repository.account.AccountStatistics;
import com.smartbox.investory.infrastructure.repository.account.AccountStatisticsRepository;
import com.smartbox.investory.infrastructure.repository.portfolio.PortfolioMonthlyPerformance;
import com.smartbox.investory.infrastructure.repository.portfolio.PortfolioMonthlyPerformanceRepository;
import com.smartbox.investory.infrastructure.repository.portfolio.SymbolPerformance;
import com.smartbox.investory.infrastructure.repository.portfolio.SymbolPerformanceRepository;
import com.smartbox.investory.services.models.DailyPerformanceDetail;
import com.smartbox.investory.services.models.InstrumentPerformance;
import com.smartbox.investory.services.models.MonthlyAttribution;
import com.smartbox.investory.services.models.Performance;
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
  private static final double OTHER_BUCKET_RATIO = 0.019;
  private static final double ACCOUNT_VISIBILITY_MIN_VALUE = 50.0;

  private final ClosedPositionRepository closedPositionRepository;
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
            .map(AccountStatistics::getAccountId)
            .collect(Collectors.toCollection(TreeSet::new));
    boolean filterVisibleAccounts = !visibleAccounts.isEmpty();

    for (PortfolioMonthlyPerformance row :
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

    for (AccountMonthlyPerformance row :
        accountMonthlyPerformanceRepository.findAllByOrderByMonthAscAccountIdAsc()) {
      if (filterVisibleAccounts && !visibleAccounts.contains(row.getAccountId())) continue;
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
  }

  public double calculateWinRate() {
    List<ClosedPosition> closedPositions = closedPositionRepository.findAll();
    if (closedPositions.isEmpty()) return 0.0;
    long profitablePositions =
        closedPositions.stream().filter(position -> position.getProfit() > 0).count();
    return (double) profitablePositions / closedPositions.size() * 100;
  }

  public List<InstrumentPerformance> calculatePerformancePerInstrument() {
    List<InstrumentPerformance> performances =
        symbolPerformanceRepository.findAll().stream()
            .map(this::toInstrumentPerformance)
            .sorted(Comparator.comparing(InstrumentPerformance::getTotal))
            .toList();
    double threshold =
        performances.stream()
                .filter(Objects::nonNull)
                .mapToDouble(InstrumentPerformance::getTotal)
                .sum()
            * OTHER_BUCKET_RATIO;
    List<InstrumentPerformance> major = new ArrayList<>();
    double otherClosed = 0.0, otherUnrealized = 0.0, otherDividends = 0.0, otherTax = 0.0;
    double otherMarketValue = 0.0, otherCostBasis = 0.0;
    for (InstrumentPerformance dto : performances) {
      if (Math.abs(dto.getTotal()) >= Math.abs(threshold)) major.add(dto);
      else {
        otherClosed += dto.getClosedProfit();
        otherUnrealized += dto.getUnrealizedProfit();
        otherDividends += dto.getDividends();
        otherTax += dto.getWithholdingTax();
        otherMarketValue += dto.getMarketValue();
        otherCostBasis += dto.getCostBasis();
      }
    }
    major.sort(Comparator.comparingDouble(InstrumentPerformance::getTotal).reversed());
    if (otherClosed != 0.0 || otherUnrealized != 0.0 || otherDividends != 0.0 || otherTax != 0.0)
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
    return major;
  }

  public DailyPerformanceDetail dailyPerformanceDetail(LocalDate date, Set<Long> accountIds) {
    List<AccountDaily> rows =
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
        profit - dividends - interest - fees - taxes,
        accounts,
        "Daily account_daily data cannot separate price movement from FX; residual is combined market/FX movement.");
  }

  private InstrumentPerformance toInstrumentPerformance(SymbolPerformance row) {
    return new InstrumentPerformance(
        row.getSymbol(),
        nz(row.getClosedProfit()),
        nz(row.getUnrealizedProfit()),
        nz(row.getTotalProfit()),
        nz(row.getDividends()),
        nz(row.getWithholdingTax()),
        nz(row.getMarketValue()),
        nz(row.getCostBasis()));
  }

  private Set<Long> cashOnlyAccountIds() {
    return accountRepository.findAll().stream()
        .filter(Account::isCashOnly)
        .map(Account::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private boolean hasVisibleAccountSurface(AccountStatistics stat) {
    return Math.abs(nz(stat.getCashBalance()) + nz(stat.getMarketValue()))
            > ACCOUNT_VISIBILITY_MIN_VALUE
        || Math.abs(nz(stat.getNetDeposit())) > ACCOUNT_VISIBILITY_MIN_VALUE;
  }

  private String summaryBucketKey(LocalDate month) {
    return String.format("%d-%02d", month.getYear(), month.getMonthValue());
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }
}
