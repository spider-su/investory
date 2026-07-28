package com.example.demo.services;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionType;
import com.example.demo.infrastructure.repository.account.Account;
import com.example.demo.infrastructure.repository.account.AccountRepository;
import com.example.demo.infrastructure.repository.account.AccountDaily;
import com.example.demo.infrastructure.repository.account.AccountDailyRepository;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformance;
import com.example.demo.infrastructure.repository.account.AccountMonthlyPerformanceRepository;
import com.example.demo.infrastructure.repository.account.AccountStatistics;
import com.example.demo.infrastructure.repository.account.AccountStatisticsRepository;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetPriceHistoryRepository;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.NormalizedCashOperationRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioAssetAllocation;
import com.example.demo.infrastructure.repository.portfolio.PortfolioAssetAllocationRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioCurrencyBreakdown;
import com.example.demo.infrastructure.repository.portfolio.PortfolioCurrencyBreakdownRepository;
import com.example.demo.infrastructure.repository.portfolio.PortfolioKpiSummary;
import com.example.demo.infrastructure.repository.portfolio.PortfolioKpiSummaryRepository;
import com.example.demo.infrastructure.repository.portfolio.SymbolPerformance;
import com.example.demo.infrastructure.repository.portfolio.SymbolPerformanceRepository;
import com.example.demo.services.CashOperationNormalizer.NormalizedCategory;
import com.example.demo.services.currency.CurrencyRateService;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioProjectionService {

  private static final CurrencyType BASE_CURRENCY = CurrencyType.USD;
  private static final double EPSILON = 1e-9;
  private static final double NEAR_EMPTY_ACCOUNT_VALUE_THRESHOLD = 50.0;
  private static final long MAX_HISTORICAL_PRICE_STALENESS_DAYS = 10;
  private static final String QUALITY_EXACT_LISTING_MARKET_CLOSE = "EXACT_LISTING_MARKET_CLOSE";
  private static final String QUALITY_EXACT_LISTING_SCALED = "EXACT_LISTING_SCALED";
  private static final String QUALITY_STALE_CARRY_FORWARD = "STALE_CARRY_FORWARD";

  private final OpenedPositionRepository openedPositionRepository;
  private final ClosedPositionRepository closedPositionRepository;
  private final CashOperationRepository cashOperationRepository;
  private final AssetRepository assetRepository;
  private final AccountRepository accountRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;
  private final AccountDailyRepository accountDailyRepository;
  private final AccountMonthlyPerformanceRepository accountMonthlyPerformanceRepository;
  private final AccountStatisticsRepository accountStatisticsRepository;
  private final PortfolioAssetAllocationRepository portfolioAssetAllocationRepository;
  private final PortfolioCurrencyBreakdownRepository portfolioCurrencyBreakdownRepository;
  private final PortfolioKpiSummaryRepository portfolioKpiSummaryRepository;
  private final SymbolPerformanceRepository symbolPerformanceRepository;
  private final CurrencyRateService currencyRateService;
  private final NormalizedCashOperationRepository normalizedCashOperationRepository;
  private final AssetPriceHistoryGapFillService assetPriceHistoryGapFillService;

  @Transactional
  public void recalculateAll() {
    assetPriceHistoryGapFillService.fillMissingBusinessDayGaps(ReportingDateHelper.today());
    Set<Long> accountIds = allKnownAccountIds();
    recalculateAccounts(accountIds);
  }

  @Transactional
  public void recalculateAccounts(Set<Long> accountIds) {
    ZonedDateTime now = ReportingDateHelper.now();
    if (accountIds == null || accountIds.isEmpty()) {
      rebuildDerivedSummaries(now);
      return;
    }

    Map<Long, LocalDate> dirtyFromByAccount = earliestActivityDateByAccount(accountIds);
    Set<Long> affectedAccounts = dirtyFromByAccount.keySet();
    if (affectedAccounts.isEmpty()) {
      rebuildDerivedSummaries(now);
      return;
    }

    Set<Long> cashOnlyAccounts = accountRepository.findAllById(affectedAccounts).stream()
        .filter(Account::isCashOnly)
        .map(Account::getId)
        .collect(Collectors.toSet());
    List<OpenedPosition> opened = openedPositionRepository.findAllByAccountIn(affectedAccounts).stream()
        .filter(position -> !cashOnlyAccounts.contains(position.getAccount()))
        .toList();
    List<ClosedPosition> closed = closedPositionRepository.findAllByAccountIn(affectedAccounts).stream()
        .filter(position -> !cashOnlyAccounts.contains(position.getAccount()))
        .toList();
    List<CashOperation> cash = cashOperationRepository.findAllByAccountIn(affectedAccounts);

    Map<String, Asset> assets =
        assetRepository.findAll().stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .collect(
                Collectors.toMap(
                    Asset::getSymbol,
                    asset -> asset,
                    (first, ignored) -> first,
                    LinkedHashMap::new));
    Map<Long, CurrencyType> accountCurrencies =
        accountRepository.findAll().stream()
            .filter(account -> account.getId() != null)
            .collect(Collectors.toMap(Account::getId, Account::getCurrency, (first, ignored) -> first));

    Map<PositionKey, PositionAccumulator> positions = new HashMap<>();
    Map<DayTickerKey, TickerMonthAccumulator> tickerDaily = new HashMap<>();
    Map<DayAccountKey, AccountMonthAccumulator> accountDaily = new HashMap<>();

    processOpened(opened, positions, tickerDaily);
    processClosed(closed, positions, tickerDaily);
    Set<AccountTickerKey> positionValuedTickers =
        tickerDaily.keySet().stream()
            .map(key -> new AccountTickerKey(key.accountId(), key.ticker()))
            .collect(Collectors.toSet());
    Set<AccountTickerKey> cashSettledTickers = cashSettledTickers(cash);
    List<CanonicalNormalizedCashOperation> normalizedCash =
        loadNormalizedCashOperations(affectedAccounts, accountCurrencies);
    processCash(
        normalizedCash, positions, tickerDaily, accountDaily, accountCurrencies, positionValuedTickers);

    HistoricalPriceBook historicalPrices = loadHistoricalPrices(tickerDaily.keySet(), now.toLocalDate());

    List<AccountDaily> accountRows =
        buildAccountDailyRows(
            accountDaily, tickerDaily, assets, historicalPrices, accountCurrencies, cashSettledTickers, now);
    replaceAccountDerivedRows(accountRows, dirtyFromByAccount);
    rebuildDerivedSummaries(now);

    log.info(
        "Portfolio projections recalculated: {} accounts, {} account_daily, dependent materialized views refreshed",
        affectedAccounts.size(),
        accountRows.size());
  }

  private void replaceAccountDerivedRows(
      List<AccountDaily> accountRows,
      Map<Long, LocalDate> dirtyFromByAccount) {
    for (Map.Entry<Long, LocalDate> entry : dirtyFromByAccount.entrySet()) {
      Long accountId = entry.getKey();
      LocalDate dirtyFrom = entry.getValue();
      accountDailyRepository.deleteByAccountIdAndDateGreaterThanEqual(accountId, dirtyFrom);
    }

    accountDailyRepository.saveAll(
        accountRows.stream()
            .filter(
                row ->
                    !row.getDate()
                        .isBefore(dirtyFromByAccount.getOrDefault(row.getAccountId(), row.getDate())))
            .toList());
  }

  private Set<Long> allKnownAccountIds() {
    Set<Long> accountIds = new HashSet<>();
    openedPositionRepository.findAll().forEach(position -> addAccountId(accountIds, position.getAccount()));
    closedPositionRepository.findAll().forEach(position -> addAccountId(accountIds, position.getAccount()));
    cashOperationRepository.findAll().forEach(operation -> addAccountId(accountIds, operation.getAccount()));
    return accountIds;
  }

  private static void addAccountId(Set<Long> accountIds, Long accountId) {
    if (accountId != null) {
      accountIds.add(accountId);
    }
  }

  private Map<Long, LocalDate> earliestActivityDateByAccount(Set<Long> accountIds) {
    Map<Long, LocalDate> earliest = new HashMap<>();
    openedPositionRepository.findAllByAccountIn(accountIds)
        .forEach(
            position ->
                includeEarlier(
                    earliest, position.getAccount(), toDate(position.getOpenTime())));
    closedPositionRepository.findAllByAccountIn(accountIds)
        .forEach(
            position -> {
              includeEarlier(earliest, position.getAccount(), toDate(position.getOpenTime()));
              includeEarlier(earliest, position.getAccount(), toDate(position.getCloseTime()));
            });
    cashOperationRepository.findAllByAccountIn(accountIds)
        .forEach(operation -> includeEarlier(earliest, operation.getAccount(), toDate(operation.getDate())));
    return earliest;
  }

  private static void includeEarlier(Map<Long, LocalDate> earliest, Long accountId, LocalDate candidate) {
    if (accountId == null || candidate == null) {
      return;
    }
    earliest.merge(accountId, candidate, (left, right) -> left.isBefore(right) ? left : right);
  }

  private void processOpened(
      List<OpenedPosition> opened,
      Map<PositionKey, PositionAccumulator> positions,
      Map<DayTickerKey, TickerMonthAccumulator> tickerDaily) {
    for (OpenedPosition position : opened) {
      if (position.getAccount() == null || !StringUtils.hasText(position.getSymbol())) {
        continue;
      }

      CurrencyType currency = position.getCurrency() != null ? position.getCurrency() : BASE_CURRENCY;
      LocalDate date = toDate(position.getOpenTime());
      double volume = Math.abs(nz(position.getVolume()));
      if (volume <= EPSILON) {
        continue;
      }

      double openValue = nz(position.getPurchaseValue());
      if (openValue <= EPSILON) {
        openValue = volume * nz(position.getOpenPrice());
      }

      PositionKey key = new PositionKey(position.getAccount(), position.getSymbol(), currency);
      PositionAccumulator acc = positions.computeIfAbsent(key, ignored -> new PositionAccumulator());
      acc.applyOpen(position.getType(), volume, openValue, date);
      acc.unrealizedProfit += nz(position.getProfit()) + nz(position.getCommission()) + nz(position.getSwap());
      acc.totalCommission += nz(position.getCommission()) + nz(position.getSwap());

      DayTickerKey dayKey = DayTickerKey.of(position.getAccount(), position.getSymbol(), date);
      TickerMonthAccumulator dayAcc = tickerDaily.computeIfAbsent(dayKey, ignored -> new TickerMonthAccumulator());
      if (position.getType() == PositionType.SELL) {
        double valueBase = convert(openValue, currency, date);
        dayAcc.sellQty += volume;
        dayAcc.sellValue += valueBase;
      } else {
        double valueBase = convert(openValue, currency, date);
        dayAcc.buyQty += volume;
        dayAcc.buyValue += valueBase;
      }
    }
  }

  private void processClosed(
      List<ClosedPosition> closed,
      Map<PositionKey, PositionAccumulator> positions,
      Map<DayTickerKey, TickerMonthAccumulator> tickerDaily) {
    for (ClosedPosition position : closed) {
      if (position.getAccount() == null
          || !StringUtils.hasText(position.getSymbol())
          || position.getCloseTime() == null) {
        continue;
      }

      CurrencyType currency = position.getCurrency() != null ? position.getCurrency() : BASE_CURRENCY;
      LocalDate openDate = toDate(position.getOpenTime());
      LocalDate closeDate = toDate(position.getCloseTime());
      double volume = Math.abs(nz(position.getVolume()));
      if (volume <= EPSILON) {
        continue;
      }

      double openValue = nz(position.getPurchaseValue());
      if (openValue <= EPSILON) {
        openValue = volume * nz(position.getOpenPrice());
      }
      double closeValue = nz(position.getSaleValue());
      if (closeValue <= EPSILON) {
        closeValue = volume * nz(position.getClosePrice());
      }
      double netRealized = nz(position.getProfit()) + nz(position.getCommission()) + nz(position.getSwap());

      PositionKey key = new PositionKey(position.getAccount(), position.getSymbol(), currency);
      PositionAccumulator acc = positions.computeIfAbsent(key, ignored -> new PositionAccumulator());
      if (position.getType() == PositionType.SELL) {
        acc.applyOpen(PositionType.SELL, volume, openValue, openDate);
        acc.applyClose(PositionType.SELL, volume, closeValue, closeDate);
      } else {
        acc.applyOpen(PositionType.BUY, volume, openValue, openDate);
        acc.applyClose(PositionType.BUY, volume, closeValue, closeDate);
      }
      acc.realizedProfit += netRealized;
      acc.totalCommission += nz(position.getCommission()) + nz(position.getSwap());

      DayTickerKey openDay = DayTickerKey.of(position.getAccount(), position.getSymbol(), openDate);
      DayTickerKey closeDay = DayTickerKey.of(position.getAccount(), position.getSymbol(), closeDate);
      TickerMonthAccumulator openAcc = tickerDaily.computeIfAbsent(openDay, ignored -> new TickerMonthAccumulator());
      TickerMonthAccumulator closeAcc = tickerDaily.computeIfAbsent(closeDay, ignored -> new TickerMonthAccumulator());
      double openValueBase = convert(openValue, currency, openDate);
      double closeValueBase = convert(closeValue, currency, closeDate);
      if (position.getType() == PositionType.SELL) {
        openAcc.sellQty += volume;
        openAcc.sellValue += openValueBase;
        closeAcc.buyQty += volume;
        closeAcc.buyValue += closeValueBase;
      } else {
        openAcc.buyQty += volume;
        openAcc.buyValue += openValueBase;
        closeAcc.sellQty += volume;
        closeAcc.sellValue += closeValueBase;
      }
      closeAcc.realizedProfit += convert(netRealized, currency, closeDate);
    }
  }

  private void processCash(
      List<CanonicalNormalizedCashOperation> cash,
      Map<PositionKey, PositionAccumulator> positions,
      Map<DayTickerKey, TickerMonthAccumulator> tickerDaily,
      Map<DayAccountKey, AccountMonthAccumulator> accountDaily,
      Map<Long, CurrencyType> accountCurrencies,
      Set<AccountTickerKey> positionValuedTickers) {
    for (CanonicalNormalizedCashOperation normalized : cash) {
      if (normalized.accountId() == null || normalized.normalizedCategory() == null) {
        continue;
      }

      CurrencyType currency = normalized.currency() != null ? normalized.currency() : BASE_CURRENCY;
      CurrencyType accountCurrency = accountCurrencies.getOrDefault(normalized.accountId(), currency);
      LocalDate date = normalized.date();
      double amount = nz(normalized.amount());
      double amountBase = nz(normalized.amountInBaseCurrency());
      double amountAccountCurrency = convertBetween(amount, accountCurrency, currency, date);

      /*
       * Daily cash-flow fields in account_daily come from one canonical source only:
       * normalized cash operations.
       *
       * Position pipelines feed valuation and realized trade P/L, but never deposits,
       * withdrawals, dividends, interest, fees, or taxes. That avoids double counting
       * commissions / SEC fees already present in the canonical cash ledger.
       */
      DayAccountKey accountKey = DayAccountKey.of(normalized.accountId(), date);
      AccountMonthAccumulator accountAcc =
          accountDaily.computeIfAbsent(accountKey, ignored -> new AccountMonthAccumulator());
      boolean hasPositionValuation =
          StringUtils.hasText(normalized.symbol())
              && positionValuedTickers.contains(
                  new AccountTickerKey(normalized.accountId(), normalized.symbol()));
      accountAcc.apply(normalized, amountBase, amountAccountCurrency, hasPositionValuation);

      if (!StringUtils.hasText(normalized.symbol())) {
        continue;
      }
      DayTickerKey tickerKey = DayTickerKey.of(normalized.accountId(), normalized.symbol(), date);
      TickerMonthAccumulator tickerAcc =
          tickerDaily.computeIfAbsent(tickerKey, ignored -> new TickerMonthAccumulator());

      PositionKey positionKey = new PositionKey(normalized.accountId(), normalized.symbol(), currency);
      PositionAccumulator positionAcc =
          positions.computeIfAbsent(positionKey, ignored -> new PositionAccumulator());
      positionAcc.touch(date);

      if (normalized.normalizedCategory() == NormalizedCategory.DIVIDEND
          || normalized.normalizedCategory() == NormalizedCategory.DIVIDEND_REVERSAL) {
        tickerAcc.dividends += amountBase;
        positionAcc.totalDividends += amount;
      }
      if (!hasPositionValuation && normalized.normalizedCategory() == NormalizedCategory.TRADE_PURCHASE) {
        tickerAcc.buyValue += Math.abs(amountBase);
      }
      if (!hasPositionValuation && normalized.normalizedCategory() == NormalizedCategory.TRADE_SALE) {
        tickerAcc.sellValue += Math.abs(amountBase);
      }
      if (isTaxCategory(normalized.normalizedCategory())) {
        positionAcc.totalTax += -amount;
      }
      if (isFeeCategory(normalized.normalizedCategory())) {
        positionAcc.totalCommission += -amount;
      }
    }
  }

  private List<CanonicalNormalizedCashOperation> loadNormalizedCashOperations(
      Set<Long> affectedAccounts, Map<Long, CurrencyType> accountCurrencies) {
    return normalizedCashOperationRepository.findAllByAccountIdIn(affectedAccounts).stream()
        .map(
            row ->
                new CanonicalNormalizedCashOperation(
                    row.getOperationId(),
                    row.getAccountId(),
                    row.getSymbol(),
                    parseCurrency(row.getCurrency(), accountCurrencies.get(row.getAccountId())),
                    row.getRawOperation(),
                    parseCategory(row.getNormalizedCategory()),
                    nz(row.getAmount()),
                    nz(row.getAmountInBaseCurrency()),
                    row.getDate(),
                    row.getComment()))
        .toList();
  }

  private List<PositionProjection> buildPositionRows(
      Map<PositionKey, PositionAccumulator> positions, Map<String, Asset> assets) {
    List<PositionProjection> rows = new ArrayList<>();
    for (Map.Entry<PositionKey, PositionAccumulator> entry : positions.entrySet()) {
      PositionKey key = entry.getKey();
      PositionAccumulator acc = entry.getValue();
      double avgBuyPrice = acc.totalBuyQty > EPSILON ? acc.totalBuyValue / acc.totalBuyQty : 0.0;
      double costBasis = Math.abs(acc.sharesOwned) * avgBuyPrice;
      double costBasisBase = convert(costBasis, key.currency(), ReportingDateHelper.today());
      double unrealizedBase = convert(acc.unrealizedProfit, key.currency(), ReportingDateHelper.today());

      rows.add(
          new PositionProjection(key.accountId(), key.ticker(), costBasisBase, unrealizedBase));
    }
    rows.sort(
        Comparator.comparing(PositionProjection::accountId).thenComparing(PositionProjection::ticker));
    return rows;
  }

  private List<AccountDaily> buildAccountDailyRows(
      Map<DayAccountKey, AccountMonthAccumulator> accountDaily,
      Map<DayTickerKey, TickerMonthAccumulator> tickerDaily,
      Map<String, Asset> assets,
      HistoricalPriceBook historicalPrices,
      Map<Long, CurrencyType> accountCurrencies,
      Set<AccountTickerKey> cashSettledTickers,
      ZonedDateTime now) {
    Map<DayAccountKey, AccountMonthAccumulator> aggregated = new HashMap<>(accountDaily);

    Map<Long, DayRange> accountRanges = new HashMap<>();
    for (DayAccountKey key : accountDaily.keySet()) {
      accountRanges.computeIfAbsent(key.accountId(), ignored -> new DayRange()).include(key.date());
    }

    Map<AccountTickerKey, Map<LocalDate, TickerMonthAccumulator>> byTicker = new HashMap<>();
    for (Map.Entry<DayTickerKey, TickerMonthAccumulator> entry : tickerDaily.entrySet()) {
      DayTickerKey key = entry.getKey();
      byTicker
          .computeIfAbsent(new AccountTickerKey(key.accountId(), key.ticker()), ignored -> new HashMap<>())
          .put(key.date(), entry.getValue());
      accountRanges.computeIfAbsent(key.accountId(), ignored -> new DayRange()).include(key.date());
    }
    LocalDate currentDate = now.toLocalDate();
    for (Long accountId : accountsWithOpenShares(byTicker)) {
      accountRanges.computeIfAbsent(accountId, ignored -> new DayRange()).include(currentDate);
    }

    Map<Long, List<LocalDate>> accountDays = new HashMap<>();
    for (Map.Entry<Long, DayRange> entry : accountRanges.entrySet()) {
      List<LocalDate> days = entry.getValue().days();
      accountDays.put(entry.getKey(), days);
      for (LocalDate day : days) {
        aggregated.computeIfAbsent(new DayAccountKey(entry.getKey(), day), ignored -> new AccountMonthAccumulator());
      }
    }

    for (Map.Entry<AccountTickerKey, Map<LocalDate, TickerMonthAccumulator>> entry : byTicker.entrySet()) {
      double shares = 0.0;
      double runningCostBasis = 0.0;
      for (LocalDate day : accountDays.getOrDefault(entry.getKey().accountId(), List.of())) {
        TickerMonthAccumulator tickerAcc =
            entry.getValue().getOrDefault(day, new TickerMonthAccumulator());
        if (tickerAcc.buyQty > EPSILON) {
          shares += tickerAcc.buyQty;
          runningCostBasis += tickerAcc.buyValue;
        }
        if (tickerAcc.sellQty > EPSILON) {
          double avg = shares > EPSILON ? runningCostBasis / shares : 0.0;
          double sold = Math.min(shares, tickerAcc.sellQty);
          runningCostBasis = Math.max(0.0, runningCostBasis - sold * avg);
          shares = Math.max(0.0, shares - tickerAcc.sellQty);
        }

        Asset asset = assets.get(entry.getKey().ticker());
        double marketValue =
            historicalPrices.marketValue(entry.getKey().ticker(), shares, day, asset, runningCostBasis);

        AccountMonthAccumulator acc =
            aggregated.computeIfAbsent(
                new DayAccountKey(entry.getKey().accountId(), day),
                ignored -> new AccountMonthAccumulator());
        acc.buys += tickerAcc.buyValue;
        acc.sells += tickerAcc.sellValue;
        acc.realizedProfit += tickerAcc.realizedProfit;
        acc.endingMarketValue += marketValue;
        acc.costBase += runningCostBasis;
      }
    }

    Map<Long, List<DayAccountEntry>> byAccount = new HashMap<>();
    for (Map.Entry<DayAccountKey, AccountMonthAccumulator> entry : aggregated.entrySet()) {
      byAccount
          .computeIfAbsent(entry.getKey().accountId(), ignored -> new ArrayList<>())
          .add(new DayAccountEntry(entry.getKey(), entry.getValue()));
    }

    List<AccountDaily> rows = new ArrayList<>();
    for (Map.Entry<Long, List<DayAccountEntry>> account : byAccount.entrySet()) {
      List<DayAccountEntry> entries = account.getValue();
      entries.sort(Comparator.comparing(DayAccountEntry::date));

      CurrencyType accountCurrency = accountCurrencies.getOrDefault(account.getKey(), BASE_CURRENCY);
      double previousCash = 0.0;
      double previousCashAccountCurrency = 0.0;
      double previousMarket = 0.0;
      for (DayAccountEntry entry : entries) {
        AccountMonthAccumulator acc = entry.acc();
        /*
         * Cash is reconstructed in native account currency first, then converted to base
         * currency for storage.
         *
         * USD account:
         *   today_cash_usd - yesterday_cash_usd == today's USD cash ledger sum
         *
         * non-USD account:
         *   native cash delta comes from today's native cash ledger rows
         *   stored USD cash is today's native closing cash converted at today's FX
         *   so day-over-day USD cash includes both today's converted cash movements
         *   and cash FX revaluation on the opening native balance.
         */
        double cashBalanceAccountCurrency =
            previousCashAccountCurrency + acc.cashDeltaAccountCurrency;
        double cashBalance = convert(cashBalanceAccountCurrency, accountCurrency, entry.key().date());
        double marketValue = acc.endingMarketValue > EPSILON ? acc.endingMarketValue : 0.0;
        double equity = cashBalance + marketValue;
        double previousEquity = previousCash + previousMarket;
        double costBase = acc.costBase > EPSILON ? acc.costBase : 0.0;
        double unrealizedProfit = marketValue - costBase;
        /*
         * Canonical daily profit must reconcile to the same boundary formula used by
         * account_monthly_mv:
         *
         *   daily profit = closing equity - opening equity - external deposits + external withdrawals
         *
         * Internal transfers, FX conversion, trade settlement, dividends, interest, fees,
         * taxes, and realized trade rows already affect equity through the canonical cash
         * ledger and/or market valuation, so they must not be added again here.
         */
        double dailyProfit = equity - previousEquity - acc.deposits + acc.withdrawals;
        double denominator = Math.abs(previousEquity) + Math.max(0.0, acc.deposits);
        double dailyReturn = Math.abs(denominator) > EPSILON ? dailyProfit / denominator : 0.0;

        rows.add(
            AccountDaily.builder()
                .accountId(entry.key().accountId())
                .date(entry.key().date())
                .valuationCurrency(BASE_CURRENCY.name())
                .cashBalance(cashBalance)
                .marketValue(marketValue)
                .equity(equity)
                .unrealizedProfit(unrealizedProfit)
                .costBase(costBase)
                .realizedProfit(acc.realizedProfit)
                .dividends(acc.dividends)
                .interest(acc.interest)
                .fees(acc.fees)
                .taxes(acc.taxes)
                .deposits(acc.deposits)
                .withdrawals(acc.withdrawals)
                .dailyProfitAmount(dailyProfit)
                .dailyReturn(dailyReturn)
                .createdAt(now)
                .updatedAt(now)
                .build());

        previousCash = cashBalance;
        previousCashAccountCurrency = cashBalanceAccountCurrency;
        previousMarket = marketValue;
      }
    }

    rows.sort(Comparator.comparing(AccountDaily::getAccountId).thenComparing(AccountDaily::getDate));
    return rows;
  }

  private Set<AccountTickerKey> cashSettledTickers(List<CashOperation> cash) {
    return cash.stream()
        .filter(operation -> operation.getAccount() != null)
        .filter(operation -> StringUtils.hasText(operation.getSymbol()))
        .filter(
            operation ->
                operation.getType() == CashOperationType.STOCK_PURCHASE
                    || operation.getType() == CashOperationType.STOCK_SELL)
        .map(operation -> new AccountTickerKey(operation.getAccount(), operation.getSymbol()))
        .collect(Collectors.toSet());
  }

  private List<AccountMonthlyPerformance> buildAccountMonthlyRows(
      List<AccountDaily> accountRows, ZonedDateTime now) {
    Map<Long, List<AccountDaily>> dailyByAccount =
        accountRows.stream().collect(Collectors.groupingBy(AccountDaily::getAccountId));
    List<AccountMonthlyPerformance> rows = new ArrayList<>();

    for (Map.Entry<Long, List<AccountDaily>> entry : dailyByAccount.entrySet()) {
      Long accountId = entry.getKey();
      Map<LocalDate, List<AccountDaily>> byMonth =
          entry.getValue().stream()
              .collect(Collectors.groupingBy(row -> row.getDate().withDayOfMonth(1)));
      List<LocalDate> months = new ArrayList<>(byMonth.keySet());
      months.sort(Comparator.naturalOrder());
      double previousEndEquity = 0.0;
      double previousDeposits = 0.0;
      double previousWithdrawals = 0.0;

      for (LocalDate month : months) {
        List<AccountDaily> monthRows = byMonth.get(month);
        AccountDaily latestDay =
            monthRows.stream().max(Comparator.comparing(AccountDaily::getDate)).orElse(null);
        if (latestDay == null) {
          continue;
        }
        double startEquity = previousEndEquity;
        double endEquity = nz(latestDay.getEquity());
        double cumulativeDeposits = nz(latestDay.getDeposits());
        double cumulativeWithdrawals = nz(latestDay.getWithdrawals());
        double depositFlow = cumulativeDeposits - previousDeposits;
        double withdrawalFlow = cumulativeWithdrawals - previousWithdrawals;
        double netCashflow = depositFlow - withdrawalFlow;
        double profit =
            monthRows.stream().mapToDouble(row -> nz(row.getDailyProfitAmount())).sum();
        double returnPct =
            Math.abs(startEquity + Math.max(depositFlow, 0.0)) > 0.000001
                ? profit / (startEquity + Math.max(depositFlow, 0.0))
                : 0.0;

        rows.add(
            new AccountMonthlyPerformance(
                accountId + ":" + month,
                accountId,
                month,
                latestDay.getDate(),
                startEquity,
                endEquity,
                depositFlow,
                withdrawalFlow,
                netCashflow,
                profit,
                returnPct,
                now));
        previousEndEquity = endEquity;
        previousDeposits = cumulativeDeposits;
        previousWithdrawals = cumulativeWithdrawals;
      }
    }

    rows.sort(Comparator.comparing(AccountMonthlyPerformance::getMonth).thenComparing(AccountMonthlyPerformance::getAccountId));
    return rows;
  }

  private List<AccountStatistics> buildAccountStatisticsRows(
      List<AccountDaily> accountRows,
      List<CashOperation> cashOperations,
      List<OpenedPosition> openedPositions,
      List<ClosedPosition> closedPositions,
      ZonedDateTime now) {
    Map<Long, List<AccountDaily>> dailyByAccount =
        accountRows.stream().collect(Collectors.groupingBy(AccountDaily::getAccountId));

    Set<Long> accounts = new HashSet<>(dailyByAccount.keySet());
    Map<Long, Integer> activityCounts = new HashMap<>();
    Map<Long, ZonedDateTime> firstActivityAt = new HashMap<>();
    Map<Long, ZonedDateTime> lastActivityAt = new HashMap<>();
    cashOperations.forEach(
        operation -> recordActivity(activityCounts, firstActivityAt, lastActivityAt, operation.getAccount(), operation.getDate()));
    openedPositions.forEach(
        position -> recordActivity(activityCounts, firstActivityAt, lastActivityAt, position.getAccount(), position.getOpenTime()));
    closedPositions.forEach(
        position -> recordActivity(activityCounts, firstActivityAt, lastActivityAt, position.getAccount(), position.getCloseTime()));

    List<AccountStatistics> rows = new ArrayList<>();
    for (Long account : accounts) {
      List<AccountDaily> daily = dailyByAccount.getOrDefault(account, List.of());

      AccountDaily latestDay =
          daily.stream()
              .max(Comparator.comparing(AccountDaily::getDate))
              .orElse(null);

      double totalDeposit = latestDay != null ? nz(latestDay.getDeposits()) : 0.0;
      double totalWithdrawal = latestDay != null ? nz(latestDay.getWithdrawals()) : 0.0;
      double netDeposit = totalDeposit - totalWithdrawal;
      double realized = latestDay != null ? nz(latestDay.getRealizedProfit()) : 0.0;
      double dividends = latestDay != null ? nz(latestDay.getDividends()) : 0.0;
      double interest = latestDay != null ? nz(latestDay.getInterest()) : 0.0;
      double fees = latestDay != null ? nz(latestDay.getFees()) : 0.0;
      double taxes = latestDay != null ? nz(latestDay.getTaxes()) : 0.0;

      double cashBalance = latestDay != null ? nz(latestDay.getCashBalance()) : 0.0;
      double marketValue = latestDay != null ? nz(latestDay.getMarketValue()) : 0.0;
      double equity = latestDay != null ? nz(latestDay.getEquity()) : cashBalance + marketValue;
      double unrealized = latestDay != null ? nz(latestDay.getUnrealizedProfit()) : 0.0;
      if (Math.abs(cashBalance + marketValue) < NEAR_EMPTY_ACCOUNT_VALUE_THRESHOLD) {
        totalDeposit = 0.0;
        totalWithdrawal = 0.0;
        netDeposit = 0.0;
      }

      double costBase = latestDay != null ? nz(latestDay.getCostBase()) : 0.0;

      rows.add(
          AccountStatistics.builder()
              .accountId(account)
              .totalDeposit(totalDeposit)
              .totalWithdrawal(totalWithdrawal)
              .netDeposit(netDeposit)
              .cashBalance(cashBalance)
              .marketValue(marketValue)
              .equity(equity)
              .costBase(costBase)
              .realizedProfit(realized)
              .unrealizedProfit(unrealized)
              .dividends(dividends)
              .interest(interest)
              .fees(fees)
              .taxes(taxes)
              .activityCount(activityCounts.getOrDefault(account, 0))
              .firstActivityAt(firstActivityAt.get(account))
              .lastActivityAt(lastActivityAt.get(account))
              .updatedAt(now)
              .build());
    }

    rows.sort(Comparator.comparing(AccountStatistics::getAccountId));
    return rows;
  }

  private void recordActivity(
      Map<Long, Integer> activityCounts,
      Map<Long, ZonedDateTime> firstActivityAt,
      Map<Long, ZonedDateTime> lastActivityAt,
      Long accountId,
      ZonedDateTime timestamp) {
    if (accountId == null || timestamp == null) {
      return;
    }
    activityCounts.merge(accountId, 1, Integer::sum);
    firstActivityAt.merge(accountId, timestamp, (left, right) -> left.isBefore(right) ? left : right);
    lastActivityAt.merge(accountId, timestamp, (left, right) -> left.isAfter(right) ? left : right);
  }

  private void rebuildDerivedSummaries(ZonedDateTime now) {
    accountDailyRepository.refreshReportingViews();
  }

  private void rebuildPortfolioKpiSummaries(ZonedDateTime now) {
    portfolioKpiSummaryRepository.deleteAllRows();
    Map<Long, AccountStatistics> statsByAccount =
        accountStatisticsRepository.findAll().stream()
            .collect(Collectors.toMap(AccountStatistics::getAccountId, row -> row));
    List<PortfolioKpiSummary> rows = new ArrayList<>();
    accountRepository.findAll().stream()
        .collect(Collectors.groupingBy(Account::getPortfolioId))
        .forEach(
            (portfolioId, accounts) -> {
              if (portfolioId == null || accounts.isEmpty()) {
                return;
              }
              double deposits = 0.0;
              double netDeposits = 0.0;
              double cash = 0.0;
              double marketValue = 0.0;
              double realized = 0.0;
              double unrealized = 0.0;
              double dividends = 0.0;
              double interest = 0.0;
              for (Account account : accounts) {
                AccountStatistics stat = statsByAccount.get(account.getId());
                if (stat == null) {
                  continue;
                }
                deposits += nz(stat.getTotalDeposit());
                netDeposits += nz(stat.getNetDeposit());
                cash += nz(stat.getCashBalance());
                marketValue += nz(stat.getMarketValue());
                realized += nz(stat.getRealizedProfit());
                unrealized += nz(stat.getUnrealizedProfit());
                dividends += nz(stat.getDividends());
                interest += nz(stat.getInterest());
              }
              rows.add(
                  new PortfolioKpiSummary(
                      portfolioId,
                      "Portfolio " + portfolioId,
                      BASE_CURRENCY,
                      deposits,
                      netDeposits,
                      cash,
                      marketValue,
                      cash + marketValue,
                      realized,
                      unrealized,
                      dividends,
                      interest,
                      now));
            });
    portfolioKpiSummaryRepository.saveAll(rows);
  }

  private void rebuildPortfolioCurrencyBreakdowns(ZonedDateTime now) {
    portfolioCurrencyBreakdownRepository.deleteAllRows();
    List<PortfolioCurrencyBreakdown> rows = new ArrayList<>();
    Map<CurrencyType, Double> realizedLocal = new HashMap<>();
    Map<CurrencyType, Double> unrealizedLocal = new HashMap<>();
    Map<CurrencyType, Double> dividendsLocal = new HashMap<>();

    closedPositionRepository.findAll()
        .forEach(
            position -> {
              CurrencyType currency = position.getCurrency() != null ? position.getCurrency() : BASE_CURRENCY;
              realizedLocal.merge(
                  currency,
                  nz(position.getProfit()) + nz(position.getCommission()) + nz(position.getSwap()),
                  Double::sum);
            });
    openedPositionRepository.findAll()
        .forEach(
            position -> {
              CurrencyType currency = position.getCurrency() != null ? position.getCurrency() : BASE_CURRENCY;
              unrealizedLocal.merge(
                  currency,
                  nz(position.getProfit()) + nz(position.getCommission()) + nz(position.getSwap()),
                  Double::sum);
            });
    cashOperationRepository.findAll()
        .forEach(
            operation -> {
              if (operation.getType() != CashOperationType.DIVIDEND) {
                return;
              }
              CurrencyType currency = operation.getCurrency() != null ? operation.getCurrency() : BASE_CURRENCY;
              dividendsLocal.merge(currency, nz(operation.getAmount()), Double::sum);
            });

    appendCurrencyRows(rows, "REALIZED", realizedLocal, now);
    appendCurrencyRows(rows, "UNREALIZED", unrealizedLocal, now);
    appendCurrencyRows(rows, "DIVIDENDS", dividendsLocal, now);
    portfolioCurrencyBreakdownRepository.saveAll(rows);
  }

  private void appendCurrencyRows(
      List<PortfolioCurrencyBreakdown> rows,
      String metricType,
      Map<CurrencyType, Double> amounts,
      ZonedDateTime now) {
    amounts.forEach(
        (currency, localAmount) ->
            rows.add(
                new PortfolioCurrencyBreakdown(
                    1L,
                    BASE_CURRENCY,
                    metricType,
                    currency,
                    localAmount,
                    convert(localAmount, currency, ReportingDateHelper.today()),
                    now)));
  }

  private void rebuildPortfolioAssetAllocations(ZonedDateTime now) {
    portfolioAssetAllocationRepository.deleteAllRows();
    List<PortfolioAssetAllocation> rows = new ArrayList<>();
    LocalDate valuationDate = now.toLocalDate();
    Map<String, Asset> assetsBySymbol =
        assetRepository.findAll().stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .collect(Collectors.toMap(Asset::getSymbol, asset -> asset, (left, ignored) -> left));
    Map<String, AssetAllocationAccumulator> bySymbol = new HashMap<>();
    Set<DayTickerKey> valuationKeys = new HashSet<>();
    openedPositionRepository.findAll()
        .forEach(
            position -> {
              if (!StringUtils.hasText(position.getSymbol())) {
                return;
              }
              CurrencyType currency = position.getCurrency() != null ? position.getCurrency() : BASE_CURRENCY;
              AssetAllocationAccumulator acc =
                  bySymbol.computeIfAbsent(position.getSymbol(), ignored -> new AssetAllocationAccumulator());
              double volume = nz(position.getVolume());
              double costBase = nz(position.getPurchaseValue()) > EPSILON
                  ? nz(position.getPurchaseValue())
                  : volume * nz(position.getOpenPrice());
              acc.totalVolume += volume;
              acc.costBasis += convert(costBase, currency, toDate(position.getOpenTime()));
              valuationKeys.add(DayTickerKey.of(position.getAccount(), position.getSymbol(), valuationDate));
            });
    HistoricalPriceBook historicalPrices = loadHistoricalPrices(valuationKeys, valuationDate);
    bySymbol.forEach(
        (symbol, acc) -> {
          Asset asset = assetsBySymbol.get(symbol);
          acc.marketValue =
              historicalPrices.marketValue(symbol, acc.totalVolume, valuationDate, asset, acc.costBasis);
          acc.unrealized = acc.marketValue - acc.costBasis;
          rows.add(
              new PortfolioAssetAllocation(
                  1L,
                  BASE_CURRENCY,
                  symbol,
                  acc.totalVolume,
                  acc.costBasis,
                  acc.marketValue,
                  acc.unrealized,
                  now));
        });
    portfolioAssetAllocationRepository.saveAll(rows);
  }

  private void rebuildSymbolPerformance(ZonedDateTime now) {
    symbolPerformanceRepository.deleteAllRows();
    Map<String, SymbolPerformanceAccumulator> bySymbol = new HashMap<>();
    LocalDate valuationDate = now.toLocalDate();
    Map<String, Asset> assetsBySymbol =
        assetRepository.findAll().stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .collect(Collectors.toMap(Asset::getSymbol, asset -> asset, (left, ignored) -> left));
    Set<DayTickerKey> valuationKeys = new HashSet<>();

    closedPositionRepository.findAll()
        .forEach(
            position -> {
              if (!StringUtils.hasText(position.getSymbol())) {
                return;
              }
              CurrencyType currency = position.getCurrency() != null ? position.getCurrency() : BASE_CURRENCY;
              SymbolPerformanceAccumulator acc =
                  bySymbol.computeIfAbsent(position.getSymbol(), ignored -> new SymbolPerformanceAccumulator());
              acc.closedProfit += convert(nz(position.getProfit()), currency, toDate(position.getCloseTime()));
            });
    openedPositionRepository.findAll()
        .forEach(
            position -> {
              if (!StringUtils.hasText(position.getSymbol())) {
                return;
              }
              CurrencyType currency = position.getCurrency() != null ? position.getCurrency() : BASE_CURRENCY;
              SymbolPerformanceAccumulator acc =
                  bySymbol.computeIfAbsent(position.getSymbol(), ignored -> new SymbolPerformanceAccumulator());
              double volume = nz(position.getVolume());
              double costBase = nz(position.getPurchaseValue()) > EPSILON
                  ? nz(position.getPurchaseValue())
                  : volume * nz(position.getOpenPrice());
              acc.totalVolume += volume;
              acc.costBasis += convert(costBase, currency, toDate(position.getOpenTime()));
              valuationKeys.add(DayTickerKey.of(position.getAccount(), position.getSymbol(), valuationDate));
            });
    HistoricalPriceBook historicalPrices = loadHistoricalPrices(valuationKeys, valuationDate);
    bySymbol.forEach(
        (symbol, acc) -> {
          Asset asset = assetsBySymbol.get(symbol);
          acc.marketValue =
              historicalPrices.marketValue(symbol, acc.totalVolume, valuationDate, asset, acc.costBasis);
          acc.unrealizedProfit = acc.marketValue - acc.costBasis;
        });
    cashOperationRepository.findAll()
        .forEach(
            operation -> {
              if (!StringUtils.hasText(operation.getSymbol())) {
                return;
              }
              CurrencyType currency = operation.getCurrency() != null ? operation.getCurrency() : BASE_CURRENCY;
              SymbolPerformanceAccumulator acc =
                  bySymbol.computeIfAbsent(operation.getSymbol(), ignored -> new SymbolPerformanceAccumulator());
              if (operation.getType() == CashOperationType.DIVIDEND) {
                acc.dividends += convert(nz(operation.getAmount()), currency, toDate(operation.getDate()));
              } else if (operation.getType() == CashOperationType.WITHHOLDING_TAX) {
                acc.withholdingTax +=
                    convert(Math.abs(nz(operation.getAmount())), currency, toDate(operation.getDate()));
              }
            });

    List<SymbolPerformance> rows =
        bySymbol.entrySet().stream()
            .map(
                entry ->
                    new SymbolPerformance(
                        entry.getKey(),
                        entry.getValue().closedProfit,
                        entry.getValue().unrealizedProfit,
                        entry.getValue().closedProfit + entry.getValue().unrealizedProfit,
                        entry.getValue().dividends - entry.getValue().withholdingTax,
                        entry.getValue().withholdingTax,
                        entry.getValue().totalVolume,
                        entry.getValue().costBasis,
                        entry.getValue().marketValue,
                        now))
            .sorted(Comparator.comparing(SymbolPerformance::getSymbol))
            .toList();
    symbolPerformanceRepository.saveAll(rows);
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private static LocalDate toDate(ZonedDateTime dateTime) {
    return ReportingDateHelper.toReportingDate(dateTime);
  }

  private Set<Long> accountsWithOpenShares(
      Map<AccountTickerKey, Map<LocalDate, TickerMonthAccumulator>> byTicker) {
    Set<Long> accounts = new HashSet<>();
    for (Map.Entry<AccountTickerKey, Map<LocalDate, TickerMonthAccumulator>> entry : byTicker.entrySet()) {
      double shares = 0.0;
      List<LocalDate> days = new ArrayList<>(entry.getValue().keySet());
      days.sort(Comparator.naturalOrder());
      for (LocalDate day : days) {
        TickerMonthAccumulator acc = entry.getValue().get(day);
        shares += acc.buyQty;
        shares -= acc.sellQty;
      }
      if (shares > EPSILON) {
        accounts.add(entry.getKey().accountId());
      }
    }
    return accounts;
  }

  private HistoricalPriceBook loadHistoricalPrices(Set<DayTickerKey> tickerDays, LocalDate currentDate) {
    if (tickerDays.isEmpty()) {
      return emptyHistoricalPriceBook();
    }

    Set<String> symbols = tickerDays.stream().map(DayTickerKey::ticker).collect(Collectors.toSet());
    LocalDate dateTo =
        tickerDays.stream()
            .map(DayTickerKey::date)
            .max(Comparator.naturalOrder())
            .map(maxDate -> maxDate.isAfter(currentDate) ? maxDate : currentDate)
            .orElse(currentDate);

    Map<String, NavigableMap<LocalDate, HistoricalPrice>> prices = new HashMap<>();
    for (AssetPriceHistoryRepository.HistoricalAssetPriceRow row :
        assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(symbols, dateTo)) {
      if (!StringUtils.hasText(row.getSymbol()) || row.getPriceDate() == null || row.getClosePrice() == null) {
        continue;
      }
      CurrencyType currency = parseCurrency(row.getPriceCurrency());
      double scaleFactor = row.getPriceScaleFactor() == null ? 1.0 : row.getPriceScaleFactor();
      double scaledClosePrice = row.getClosePrice() * scaleFactor;
      if (scaledClosePrice <= EPSILON) {
        continue;
      }
      HistoricalPrice candidate =
          new HistoricalPrice(
              row.getPriceDate(),
              row.getSourceDate() != null ? row.getSourceDate() : row.getPriceDate(),
              scaledClosePrice,
              currency,
              row.getSource(),
              row.getSourceSymbol(),
              row.getOriginalSourceSymbol(),
              row.getPriceOrigin(),
              row.getQualityClass(),
              row.getQualityScore() == null ? 0 : row.getQualityScore(),
              resolveContractMultiplier(row.getQualityClass()));
      NavigableMap<LocalDate, HistoricalPrice> symbolPrices =
          prices.computeIfAbsent(row.getSymbol(), ignored -> new TreeMap<>());
      HistoricalPrice existing = symbolPrices.get(row.getPriceDate());
      if (existing == null || isBetterHistoricalPrice(candidate, existing)) {
        symbolPrices.put(row.getPriceDate(), candidate);
      }
    }
    return new HistoricalPriceBook(prices);
  }

  private static boolean isBetterHistoricalPrice(HistoricalPrice candidate, HistoricalPrice existing) {
    int candidateRank = valuationPriorityRank(candidate.qualityClass(), candidate.priceOrigin());
    int existingRank = valuationPriorityRank(existing.qualityClass(), existing.priceOrigin());
    if (candidateRank != existingRank) {
      return candidateRank < existingRank;
    }
    return candidate.qualityScore() > existing.qualityScore();
  }

  private static int valuationPriorityRank(String qualityClass, String priceOrigin) {
    String quality = qualityClass == null ? "" : qualityClass.trim().toUpperCase(Locale.ROOT);
    String origin = priceOrigin == null ? "" : priceOrigin.trim().toUpperCase(Locale.ROOT);
    if (quality.contains(QUALITY_EXACT_LISTING_MARKET_CLOSE)) {
      return 1;
    }
    if (quality.contains(QUALITY_EXACT_LISTING_SCALED)) {
      return 2;
    }
    if (quality.contains("ALTERNATE") || quality.contains("PROXY")) {
      return 3;
    }
    if (origin.contains("MANUAL") || quality.contains("MANUAL")) {
      return 4;
    }
    if (quality.contains("INTERPOLATED")) {
      return 5;
    }
    if (quality.contains("TRADE_OBSERVATION") || origin.contains("TRADE")) {
      return 6;
    }
    if (quality.contains(QUALITY_STALE_CARRY_FORWARD) || origin.contains("CARRY_FORWARD")) {
      return 7;
    }
    return 8;
  }

  private static boolean selectedPriceNeedsDiagnostic(HistoricalPrice price) {
    return valuationPriorityRank(price.qualityClass(), price.priceOrigin()) > 2;
  }

  private static boolean canCarryForwardValuationPrice(HistoricalPrice price) {
    if (price == null) {
      return false;
    }
    String quality = price.qualityClass() == null ? "" : price.qualityClass().trim().toUpperCase(Locale.ROOT);
    String origin = price.priceOrigin() == null ? "" : price.priceOrigin().trim().toUpperCase(Locale.ROOT);
    return quality.contains("TRADE_OBSERVATION")
        || origin.contains("TRADE")
        || quality.contains(QUALITY_STALE_CARRY_FORWARD)
        || origin.contains("CARRY_FORWARD");
  }

  private static double resolveContractMultiplier(String qualityClass) {
    String quality = qualityClass == null ? "" : qualityClass.trim().toUpperCase(Locale.ROOT);
    if (quality.contains("PERCENT_OF_PAR")) {
      return 0.01;
    }
    return 1.0;
  }

  private static CurrencyType parseCurrency(String value) {
    return parseCurrency(value, BASE_CURRENCY);
  }

  private static CurrencyType parseCurrency(String value, CurrencyType fallback) {
    if (!StringUtils.hasText(value)) {
      return fallback != null ? fallback : BASE_CURRENCY;
    }
    try {
      return CurrencyType.valueOf(value);
    } catch (IllegalArgumentException ignored) {
      return fallback != null ? fallback : BASE_CURRENCY;
    }
  }

  private static NormalizedCategory parseCategory(String value) {
    if (!StringUtils.hasText(value)) {
      return NormalizedCategory.UNCLASSIFIED;
    }
    try {
      return NormalizedCategory.valueOf(value);
    } catch (IllegalArgumentException ignored) {
      return NormalizedCategory.UNCLASSIFIED;
    }
  }

  private double convert(double amount, CurrencyType fromCurrency, LocalDate date) {
    return currencyRateService.convertToBaseCurrency(amount, BASE_CURRENCY, fromCurrency, date);
  }

  private double convertBetween(
      double amount, CurrencyType targetCurrency, CurrencyType sourceCurrency, LocalDate date) {
    if (targetCurrency == sourceCurrency) {
      return amount;
    }
    double baseAmount = convert(amount, sourceCurrency, date);
    if (targetCurrency == BASE_CURRENCY) {
      return baseAmount;
    }
    OptionalDouble rate = currencyRateService.findRate(BASE_CURRENCY, targetCurrency, date);
    return rate.isPresent() ? baseAmount * rate.getAsDouble() : baseAmount;
  }

  private static boolean isTaxCategory(NormalizedCategory category) {
    return category == NormalizedCategory.WITHHOLDING_TAX
        || category == NormalizedCategory.WITHHOLDING_TAX_REVERSAL
        || category == NormalizedCategory.OTHER_TAX;
  }

  private static boolean isFeeCategory(NormalizedCategory category) {
    return category == NormalizedCategory.FEE;
  }

  private record PositionKey(Long accountId, String ticker, CurrencyType currency) {}

  private record PositionProjection(
      Long accountId, String ticker, double costBasis, double unrealizedProfit) {}

  private record AccountTickerKey(Long accountId, String ticker) {}

  private record DayTickerKey(Long accountId, String ticker, LocalDate date) {
    static DayTickerKey of(Long accountId, String ticker, LocalDate date) {
      return new DayTickerKey(accountId, ticker, date);
    }
  }

  private record HistoricalPrice(
      LocalDate valuationPriceDate,
      LocalDate observationDate,
      double normalizedClosePrice,
      CurrencyType currency,
      String source,
      String sourceSymbol,
      String originalSourceSymbol,
      String priceOrigin,
      String qualityClass,
      int qualityScore,
      double contractMultiplier) {}

  private record CanonicalNormalizedCashOperation(
      Long operationId,
      Long accountId,
      String symbol,
      CurrencyType currency,
      String rawOperation,
      NormalizedCategory normalizedCategory,
      double amount,
      double amountInBaseCurrency,
      LocalDate date,
      String comment) {}

  private final class HistoricalPriceBook {
    private final Map<String, NavigableMap<LocalDate, HistoricalPrice>> pricesBySymbol;

    private HistoricalPriceBook(Map<String, NavigableMap<LocalDate, HistoricalPrice>> pricesBySymbol) {
      this.pricesBySymbol = pricesBySymbol;
    }

    private double marketValue(
        String symbol, double shares, LocalDate date, Asset asset, double fallbackCostBasis) {
      if (shares <= EPSILON) {
        return 0.0;
      }
      // Use the fresh asset quote for today's projection. Historical rows remain
      // authoritative for prior dates.
      if (asset != null
          && asset.getMarketPrice() != null
          && asset.getMarketPrice() > EPSILON
          && asset.getPriceUpdatedAt() != null
          && !asset.getPriceUpdatedAt().toLocalDate().isBefore(date)) {
        CurrencyType currency = asset.getCurrency() == null ? BASE_CURRENCY : asset.getCurrency();
        return convert(shares * asset.getMarketPrice(), currency, date);
      }
      NavigableMap<LocalDate, HistoricalPrice> prices = pricesBySymbol.get(symbol);
      if (prices != null) {
        Map.Entry<LocalDate, HistoricalPrice> price = prices.floorEntry(date);
        if (price != null) {
          HistoricalPrice historicalPrice = price.getValue();
          LocalDate observationDate =
              historicalPrice.observationDate() != null
                  ? historicalPrice.observationDate()
                  : price.getKey();
          if (observationDate.plusDays(MAX_HISTORICAL_PRICE_STALENESS_DAYS).isBefore(date)) {
            if (canCarryForwardValuationPrice(historicalPrice)) {
              log.warn(
                  "Valuation carries forward stale fallback price: symbol={}, valuationDate={}, selectedPriceDate={}, observationDate={}, qualityClass={}, priceOrigin={}, source={}, sourceSymbol={}",
                  symbol,
                  date,
                  price.getKey(),
                  observationDate,
                  historicalPrice.qualityClass(),
                  historicalPrice.priceOrigin(),
                  historicalPrice.source(),
                  historicalPrice.sourceSymbol());
            } else {
              log.debug(
                  "Valuation uses zero because price is stale: symbol={}, valuationDate={}, selectedPriceDate={}, observationDate={}",
                  symbol,
                  date,
                  price.getKey(),
                  observationDate);
              return 0.0;
            }
          }
          if (selectedPriceNeedsDiagnostic(historicalPrice)) {
            log.warn(
                "Valuation used non-primary price source: symbol={}, date={}, selectedPriceDate={}, observationDate={}, qualityClass={}, priceOrigin={}, source={}, sourceSymbol={}, originalSourceSymbol={}",
                symbol,
                date,
                historicalPrice.valuationPriceDate(),
                observationDate,
                historicalPrice.qualityClass(),
                historicalPrice.priceOrigin(),
                historicalPrice.source(),
                historicalPrice.sourceSymbol(),
                historicalPrice.originalSourceSymbol());
          }
          /*
           * Canonical valuation model:
           *   market value = quantity × normalized market price × contract multiplier × FX
           *
           * normalized market price = close_price × price_scale_factor from asset_price_history
           * contract multiplier defaults to 1 and is only changed by explicit metadata.
           */
          double localMarketValue =
              shares * historicalPrice.normalizedClosePrice() * historicalPrice.contractMultiplier();
          return convert(localMarketValue, historicalPrice.currency(), date);
        }
        log.debug(
            "Valuation uses zero because no historical price row exists: symbol={}, date={}",
            symbol,
            date);
        return 0.0;
      }
      log.debug(
          "Valuation uses zero because symbol has no historical price book: symbol={}, date={}",
          symbol,
          date);
      return 0.0;
    }
  }

  private HistoricalPriceBook emptyHistoricalPriceBook() {
    return new HistoricalPriceBook(Map.of());
  }

  private record DayAccountKey(Long accountId, LocalDate date) {
    static DayAccountKey of(Long accountId, LocalDate date) {
      return new DayAccountKey(accountId, date);
    }
  }

  private static final class DayRange {
    private LocalDate first;
    private LocalDate last;

    void include(LocalDate value) {
      if (first == null || value.isBefore(first)) {
        first = value;
      }
      if (last == null || value.isAfter(last)) {
        last = value;
      }
    }

    List<LocalDate> days() {
      if (first == null || last == null) {
        return List.of();
      }
      List<LocalDate> values = new ArrayList<>();
      for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
        values.add(day);
      }
      return values;
    }
  }

  private record DayAccountEntry(DayAccountKey key, AccountMonthAccumulator acc) {
    LocalDate date() {
      return key.date();
    }
  }

  private static final class PositionAccumulator {
    double sharesOwned;
    double totalBuyQty;
    double totalSellQty;
    double totalBuyValue;
    double totalSellValue;
    double realizedProfit;
    double unrealizedProfit;
    double totalDividends;
    double totalCommission;
    double totalTax;
    LocalDate firstBuyDate;
    LocalDate lastTransactionDate;

    void applyOpen(PositionType type, double volume, double value, LocalDate date) {
      if (type == PositionType.SELL) {
        sharesOwned -= volume;
        totalSellQty += volume;
        totalSellValue += value;
      } else {
        sharesOwned += volume;
        totalBuyQty += volume;
        totalBuyValue += value;
        firstBuyDate = firstBuyDate == null ? date : firstBuyDate.isAfter(date) ? date : firstBuyDate;
      }
      touch(date);
    }

    void applyClose(PositionType openType, double volume, double value, LocalDate date) {
      if (openType == PositionType.SELL) {
        totalBuyQty += volume;
        totalBuyValue += value;
      } else {
        totalSellQty += volume;
        totalSellValue += value;
      }
      touch(date);
    }

    void touch(LocalDate date) {
      if (date == null) {
        return;
      }
      if (lastTransactionDate == null || lastTransactionDate.isBefore(date)) {
        lastTransactionDate = date;
      }
    }
  }

  private static final class TickerMonthAccumulator {
    double buyQty;
    double sellQty;
    double buyValue;
    double sellValue;
    double realizedProfit;
    double dividends;
  }

  private static final class AccountMonthAccumulator {
    double deposits;
    double withdrawals;
    double buys;
    double sells;
    double dividends;
    double interest;
    double fees;
    double taxes;
    double cashAdjustments;
    double cashDeltaAccountCurrency;
    double realizedProfit;
    double endingMarketValue;
    double costBase;
    boolean hasTradeCashMovements;
    boolean hasCashSettlementRows;

    void apply(
        CanonicalNormalizedCashOperation normalized,
        double amountBase,
        double amountAccountCurrency,
        boolean hasPositionValuation) {
      cashDeltaAccountCurrency += amountAccountCurrency;
      switch (normalized.normalizedCategory()) {
        case EXTERNAL_DEPOSIT:
          deposits += Math.abs(amountBase);
          break;
        case EXTERNAL_WITHDRAWAL:
          withdrawals += Math.abs(amountBase);
          break;
        case INTERNAL_TRANSFER_IN:
        case INTERNAL_TRANSFER_OUT:
          // Internal transfers are excluded from portfolio net flows because the
          // matching in/out rows cancel across accounts. They are still account-level
          // flows, otherwise the receiving account reports the transfer as fake profit.
          if (amountBase >= 0.0) {
            deposits += Math.abs(amountBase);
          } else {
            withdrawals += Math.abs(amountBase);
          }
          break;
        case INTERNAL_BOOKKEEPING:
        case FX_CONVERSION:
          cashAdjustments += amountBase;
          break;
        case DIVIDEND:
        case DIVIDEND_REVERSAL:
          dividends += amountBase;
          break;
        case INTEREST:
        case INTEREST_REVERSAL:
          interest += amountBase;
          break;
        case WITHHOLDING_TAX:
        case WITHHOLDING_TAX_REVERSAL:
        case OTHER_TAX:
          // Expense convention in account_daily: expense > 0, reversal/refund < 0.
          taxes += -amountBase;
          break;
        case FEE:
          /*
           * account_daily.fees uses the same expense convention:
           *   fee charge   -> positive
           *   fee refund   -> negative
           *
           * Included in fees:
           * - COMMISSION
           * - SEC_FEE
           * - SWAP
           * - explicit fee corrections/refunds normalized as FEE/FEE_REVERSAL
           *
           * Excluded from fees:
           * - STAMP_DUTY
           * - TRANSACTION_TAX
           * - FREE_FUNDS_INTEREST_TAX
           * - WITHHOLDING_TAX
           * - ROLLOVER
           */
          fees += -amountBase;
          break;
        case TRADE_PURCHASE:
        case TRADE_SALE:
          hasTradeCashMovements = true;
          hasCashSettlementRows =
              CashOperationType.STOCK_PURCHASE.name().equals(normalized.rawOperation())
                  || CashOperationType.STOCK_SELL.name().equals(normalized.rawOperation());
          cashAdjustments += amountBase;
          break;
        case REALIZED_TRADE_RESULT:
          realizedProfit += amountBase;
          cashAdjustments += amountBase;
          break;
        case CORRECTION:
        case UNCLASSIFIED:
          cashAdjustments += amountBase;
          break;
      }
    }

  }

  private static final class AssetAllocationAccumulator {
    double totalVolume;
    double costBasis;
    double marketValue;
    double unrealized;
  }

  private static final class SymbolPerformanceAccumulator {
    double closedProfit;
    double unrealizedProfit;
    double dividends;
    double withholdingTax;
    double totalVolume;
    double costBasis;
    double marketValue;
  }
}


