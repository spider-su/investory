package com.example.demo.services;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionType;
import com.example.demo.infrastructure.repository.account.Account;
import com.example.demo.infrastructure.repository.account.AccountRepository;
import com.example.demo.infrastructure.repository.account.AccountDaily;
import com.example.demo.infrastructure.repository.account.AccountDailyRepository;
import com.example.demo.infrastructure.repository.account.AccountStatistics;
import com.example.demo.infrastructure.repository.account.AccountStatisticsRepository;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetPriceHistoryRepository;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.services.currency.CurrencyRateService;
import java.time.LocalDate;
import java.time.ZoneId;
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
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Warsaw");
  private static final Pattern XTB_CURRENCY_CONVERSION_PATTERN =
      Pattern.compile(
          "currency conversion,\\s*([A-Z]{3})\\s+to\\s+([A-Z]{3}).*?exchange rate:\\s*([0-9]+(?:[\\.,][0-9]+)?)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern IBKR_FOREX_BASE_AMOUNT_PATTERN =
      Pattern.compile(
          "net amount in base from forex trade:\\s*([-+]?[0-9]+(?:[\\.,][0-9]+)?)\\s+[A-Z]{3}\\.[A-Z]{3}",
          Pattern.CASE_INSENSITIVE);

  private final OpenedPositionRepository openedPositionRepository;
  private final ClosedPositionRepository closedPositionRepository;
  private final CashOperationRepository cashOperationRepository;
  private final AssetRepository assetRepository;
  private final AccountRepository accountRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;
  private final AccountDailyRepository accountDailyRepository;
  private final AccountStatisticsRepository accountStatisticsRepository;
  private final CurrencyRateService currencyRateService;

  @Transactional
  public void recalculateAll() {
    ZonedDateTime now = ZonedDateTime.now(DEFAULT_ZONE);

    List<OpenedPosition> opened = openedPositionRepository.findAll();
    List<ClosedPosition> closed = closedPositionRepository.findAll();
    List<CashOperation> cash = cashOperationRepository.findAll();

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
    processCash(cash, positions, tickerDaily, accountDaily, accountCurrencies, positionValuedTickers);

    HistoricalPriceBook historicalPrices = loadHistoricalPrices(tickerDaily.keySet(), now.toLocalDate());

    List<AccountDaily> accountRows =
        buildAccountDailyRows(
            accountDaily, tickerDaily, assets, historicalPrices, accountCurrencies, cashSettledTickers, now);
    List<PositionProjection> positionRows = buildPositionRows(positions, assets);
    List<AccountStatistics> statisticsRows = buildAccountStatisticsRows(accountRows, now);

    accountDailyRepository.deleteAllRows();
    accountStatisticsRepository.deleteAllRows();

    accountDailyRepository.saveAll(accountRows);
    accountStatisticsRepository.saveAll(statisticsRows);

    log.info(
        "Portfolio projections recalculated: {} position rows, {} account_daily, {} account_statistics",
        positionRows.size(),
        accountRows.size(),
        statisticsRows.size());
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
      double feesBase = convert(nz(position.getCommission()) + nz(position.getSwap()), currency, date);
      dayAcc.fees += feesBase;
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
      double realizedBase = convert(netRealized, currency, closeDate);
      double feesBase = convert(nz(position.getCommission()) + nz(position.getSwap()), currency, closeDate);
      closeAcc.realizedProfit += realizedBase;
      closeAcc.fees += feesBase;
    }
  }

  private void processCash(
      List<CashOperation> cash,
      Map<PositionKey, PositionAccumulator> positions,
      Map<DayTickerKey, TickerMonthAccumulator> tickerDaily,
      Map<DayAccountKey, AccountMonthAccumulator> accountDaily,
      Map<Long, CurrencyType> accountCurrencies,
      Set<AccountTickerKey> positionValuedTickers) {
    for (CashOperation operation : cash) {
      if (operation.getAccount() == null || operation.getType() == null) {
        continue;
      }

      CurrencyType currency = operation.getCurrency() != null ? operation.getCurrency() : BASE_CURRENCY;
      CurrencyType accountCurrency = accountCurrencies.getOrDefault(operation.getAccount(), currency);
      LocalDate date = toDate(operation.getDate());
      double amount = nz(operation.getAmount());
      double amountBase = cashFlowAmountBase(operation, amount, currency, date);
      double amountAccountCurrency = convertBetween(amount, accountCurrency, currency, date);

      DayAccountKey accountKey = DayAccountKey.of(operation.getAccount(), date);
      AccountMonthAccumulator accountAcc =
          accountDaily.computeIfAbsent(accountKey, ignored -> new AccountMonthAccumulator());
      boolean hasPositionValuation =
          StringUtils.hasText(operation.getSymbol())
              && positionValuedTickers.contains(new AccountTickerKey(operation.getAccount(), operation.getSymbol()));
      accountAcc.apply(operation, amountBase, amountAccountCurrency, hasPositionValuation);

      if (!StringUtils.hasText(operation.getSymbol())) {
        continue;
      }
      DayTickerKey tickerKey = DayTickerKey.of(operation.getAccount(), operation.getSymbol(), date);
      TickerMonthAccumulator tickerAcc =
          tickerDaily.computeIfAbsent(tickerKey, ignored -> new TickerMonthAccumulator());

      PositionKey positionKey = new PositionKey(operation.getAccount(), operation.getSymbol(), currency);
      PositionAccumulator positionAcc =
          positions.computeIfAbsent(positionKey, ignored -> new PositionAccumulator());
      positionAcc.touch(date);

      if (operation.getType() == CashOperationType.DIVIDEND) {
        tickerAcc.dividends += amountBase;
        positionAcc.totalDividends += amount;
      }
      if (isTaxType(operation.getType())) {
        positionAcc.totalTax += amount;
      }
      if (isFeeType(operation.getType())) {
        tickerAcc.fees += amountBase;
        positionAcc.totalCommission += amount;
      }
    }
  }

  private List<PositionProjection> buildPositionRows(
      Map<PositionKey, PositionAccumulator> positions, Map<String, Asset> assets) {
    List<PositionProjection> rows = new ArrayList<>();
    for (Map.Entry<PositionKey, PositionAccumulator> entry : positions.entrySet()) {
      PositionKey key = entry.getKey();
      PositionAccumulator acc = entry.getValue();
      double avgBuyPrice = acc.totalBuyQty > EPSILON ? acc.totalBuyValue / acc.totalBuyQty : 0.0;
      double costBasis = Math.abs(acc.sharesOwned) * avgBuyPrice;
      double costBasisBase = convert(costBasis, key.currency(), LocalDate.now());
      double unrealizedBase = convert(acc.unrealizedProfit, key.currency(), LocalDate.now());

      Asset asset = assets.get(key.ticker());
      if (asset != null && asset.getMarketPriceUsd() != null && Math.abs(acc.sharesOwned) > EPSILON) {
        double marketValueBase = acc.sharesOwned * asset.getMarketPriceUsd();
        unrealizedBase = marketValueBase - costBasisBase;
      }

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
        if (!acc.hasTradeCashMovements && !cashSettledTickers.contains(entry.getKey())) {
          acc.buys += tickerAcc.buyValue;
          acc.sells += tickerAcc.sellValue;
        }
        acc.realizedProfit += tickerAcc.realizedProfit;
        acc.fees += tickerAcc.fees;
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
      double cashTradeMarketValue = 0.0;
      double cumulativeRealized = 0.0;
      double cumulativeDividends = 0.0;
      double cumulativeInterest = 0.0;
      double cumulativeFees = 0.0;
      double cumulativeTaxes = 0.0;
      double cumulativeDeposits = 0.0;
      double cumulativeWithdrawals = 0.0;
      for (DayAccountEntry entry : entries) {
        AccountMonthAccumulator acc = entry.acc();
        double cashBalanceAccountCurrency =
            previousCashAccountCurrency + acc.cashDeltaAccountCurrency;
        double cashBalance = convert(cashBalanceAccountCurrency, accountCurrency, entry.key().date());
        cashTradeMarketValue = Math.max(0.0, cashTradeMarketValue + acc.buys - acc.sells);
        double marketValue =
            acc.endingMarketValue > EPSILON ? acc.endingMarketValue : cashTradeMarketValue;
        double equity = cashBalance + marketValue;
        double previousEquity = previousCash + previousMarket;
        double costBase = acc.costBase > EPSILON ? acc.costBase : cashTradeMarketValue;
        double unrealizedProfit = marketValue - costBase;
        double dailyProfit =
            (marketValue - previousMarket - acc.buys + acc.sells)
                + acc.realizedProfit
                + acc.dividends
                + acc.interest
                + acc.fees
                + acc.taxes;
        double denominator = Math.abs(previousEquity) + Math.max(0.0, acc.deposits);
        double dailyReturn = Math.abs(denominator) > EPSILON ? dailyProfit / denominator : 0.0;

        cumulativeRealized += acc.realizedProfit;
        cumulativeDividends += acc.dividends;
        cumulativeInterest += acc.interest;
        cumulativeFees += acc.fees;
        cumulativeTaxes += acc.taxes;
        cumulativeDeposits += acc.deposits;
        cumulativeWithdrawals += acc.withdrawals;

        rows.add(
            AccountDaily.builder()
                .accountId(entry.key().accountId())
                .date(entry.key().date())
                .cashBalance(cashBalance)
                .marketValue(marketValue)
                .equity(equity)
                .unrealizedProfit(unrealizedProfit)
                .costBase(costBase)
                .realizedProfit(cumulativeRealized)
                .dividends(cumulativeDividends)
                .interest(cumulativeInterest)
                .fees(cumulativeFees)
                .taxes(cumulativeTaxes)
                .deposits(cumulativeDeposits)
                .withdrawals(cumulativeWithdrawals)
                .dailyReturn(dailyReturn)
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

  private List<AccountStatistics> buildAccountStatisticsRows(
      List<AccountDaily> accountRows, ZonedDateTime now) {
    Map<Long, List<AccountDaily>> dailyByAccount =
        accountRows.stream().collect(Collectors.groupingBy(AccountDaily::getAccountId));

    Set<Long> accounts = new HashSet<>(dailyByAccount.keySet());

    List<AccountStatistics> rows = new ArrayList<>();
    for (Long account : accounts) {
      List<AccountDaily> daily = dailyByAccount.getOrDefault(account, List.of());

      AccountDaily latestDay =
          daily.stream()
              .max(Comparator.comparing(AccountDaily::getDate))
              .orElse(null);

      double totalDeposit = latestDay != null ? nz(latestDay.getDeposits()) : 0.0;
      double totalWithdrawal = latestDay != null ? nz(latestDay.getWithdrawals()) : 0.0;
      double netDeposit = totalDeposit + totalWithdrawal;
      double realized = latestDay != null ? nz(latestDay.getRealizedProfit()) : 0.0;
      double dividends = latestDay != null ? nz(latestDay.getDividends()) : 0.0;
      double interest = latestDay != null ? nz(latestDay.getInterest()) : 0.0;
      double fees = latestDay != null ? nz(latestDay.getFees()) : 0.0;
      double taxes = latestDay != null ? nz(latestDay.getTaxes()) : 0.0;

      double cashBalance = latestDay != null ? nz(latestDay.getCashBalance()) : 0.0;
      double marketValue = latestDay != null ? nz(latestDay.getMarketValue()) : 0.0;
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
              .costBase(costBase)
              .realizedProfit(realized)
              .unrealizedProfit(unrealized)
              .dividends(dividends)
              .interest(interest)
              .fees(fees)
              .taxes(taxes)
              .updatedAt(now)
              .build());
    }

    rows.sort(Comparator.comparing(AccountStatistics::getAccountId));
    return rows;
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private static LocalDate toDate(ZonedDateTime dateTime) {
    return dateTime == null ? LocalDate.now() : dateTime.withZoneSameInstant(DEFAULT_ZONE).toLocalDate();
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
      prices
          .computeIfAbsent(row.getSymbol(), ignored -> new TreeMap<>())
          .putIfAbsent(row.getPriceDate(), new HistoricalPrice(row.getClosePrice(), currency));
    }
    return new HistoricalPriceBook(prices);
  }

  private static CurrencyType parseCurrency(String value) {
    if (!StringUtils.hasText(value)) {
      return BASE_CURRENCY;
    }
    try {
      return CurrencyType.valueOf(value);
    } catch (IllegalArgumentException ignored) {
      return BASE_CURRENCY;
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

  private double cashFlowAmountBase(
      CashOperation operation, double amount, CurrencyType currency, LocalDate date) {
    return parseXtbCurrencyConversion(operation.getComment())
        .map(conversion -> xtbCurrencyConversionAmountBase(conversion, amount, currency, date))
        .or(() -> parseIbkrForexBaseAmount(operation.getComment()))
        .orElseGet(() -> convert(amount, currency, date));
  }

  private double xtbCurrencyConversionAmountBase(
      XtbCurrencyConversion conversion, double amount, CurrencyType rowCurrency, LocalDate date) {
    if (conversion.sourceCurrency() == BASE_CURRENCY) {
      if (rowCurrency == conversion.sourceCurrency()) {
        return amount;
      }
      if (rowCurrency == conversion.targetCurrency()) {
        return amount / conversion.rate();
      }
    }
    if (conversion.targetCurrency() == BASE_CURRENCY) {
      if (rowCurrency == conversion.sourceCurrency()) {
        return amount * conversion.rate();
      }
      if (rowCurrency == conversion.targetCurrency()) {
        return amount;
      }
    }
    if (rowCurrency == conversion.sourceCurrency()) {
      return convert(amount, conversion.sourceCurrency(), date);
    }
    if (rowCurrency == conversion.targetCurrency()) {
      return convert(amount / conversion.rate(), conversion.sourceCurrency(), date);
    }
    return convert(amount, rowCurrency, date);
  }

  private Optional<XtbCurrencyConversion> parseXtbCurrencyConversion(String comment) {
    if (!StringUtils.hasText(comment)) {
      return Optional.empty();
    }
    Matcher matcher = XTB_CURRENCY_CONVERSION_PATTERN.matcher(comment);
    if (!matcher.find()) {
      return Optional.empty();
    }
    CurrencyType sourceCurrency = parseCurrency(matcher.group(1).toUpperCase(Locale.ROOT));
    CurrencyType targetCurrency = parseCurrency(matcher.group(2).toUpperCase(Locale.ROOT));
    double rate = Double.parseDouble(matcher.group(3).replace(',', '.'));
    if (rate <= EPSILON || sourceCurrency == targetCurrency) {
      return Optional.empty();
    }
    return Optional.of(new XtbCurrencyConversion(sourceCurrency, targetCurrency, rate));
  }

  private Optional<Double> parseIbkrForexBaseAmount(String comment) {
    if (!StringUtils.hasText(comment)) {
      return Optional.empty();
    }
    Matcher matcher = IBKR_FOREX_BASE_AMOUNT_PATTERN.matcher(comment);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(Double.parseDouble(matcher.group(1).replace(',', '.')));
  }

  private static boolean isTaxType(CashOperationType type) {
    return type == CashOperationType.WITHHOLDING_TAX
        || type == CashOperationType.TRANSACTION_TAX
        || type == CashOperationType.STAMP_DUTY
        || type == CashOperationType.FREE_FUNDS_INTEREST_TAX;
  }

  private static boolean isFeeType(CashOperationType type) {
    return type == CashOperationType.COMMISSION || type == CashOperationType.SWAP;
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

  private record HistoricalPrice(double closePrice, CurrencyType currency) {}

  private record XtbCurrencyConversion(
      CurrencyType sourceCurrency, CurrencyType targetCurrency, double rate) {}

  private final class HistoricalPriceBook {
    private final Map<String, NavigableMap<LocalDate, HistoricalPrice>> pricesBySymbol;

    private HistoricalPriceBook(Map<String, NavigableMap<LocalDate, HistoricalPrice>> pricesBySymbol) {
      this.pricesBySymbol = pricesBySymbol;
    }

    private double marketValue(
        String symbol, double shares, LocalDate date, Asset asset, double fallbackCostBasis) {
      NavigableMap<LocalDate, HistoricalPrice> prices = pricesBySymbol.get(symbol);
      if (prices != null) {
        Map.Entry<LocalDate, HistoricalPrice> price = prices.floorEntry(date);
        if (price != null) {
          HistoricalPrice historicalPrice = price.getValue();
          return convert(shares * historicalPrice.closePrice(), historicalPrice.currency(), date);
        }
      }
      return asset != null && asset.getMarketPriceUsd() != null
          ? shares * asset.getMarketPriceUsd()
          : fallbackCostBasis;
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
    double fees;
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
        CashOperation operation,
        double amountBase,
        double amountAccountCurrency,
        boolean hasPositionValuation) {
      switch (operation.getType()) {
        case DEPOSIT:
          cashDeltaAccountCurrency += amountAccountCurrency;
          if (isAccountFundingCashFlow(operation)) {
            applyAccountFunding(amountBase);
          } else {
            cashAdjustments += amountBase;
          }
          break;
        case WITHDRAWAL:
          cashDeltaAccountCurrency += amountAccountCurrency;
          if (isAccountFundingCashFlow(operation)) {
            applyAccountFunding(amountBase);
          } else {
            cashAdjustments += amountBase;
          }
          break;
        case DIVIDEND:
          cashDeltaAccountCurrency += amountAccountCurrency;
          dividends += amountBase;
          break;
        case FREE_FUNDS_INTEREST:
          cashDeltaAccountCurrency += amountAccountCurrency;
          interest += amountBase;
          break;
        case FREE_FUNDS_INTEREST_TAX:
          cashDeltaAccountCurrency += amountAccountCurrency;
          taxes += amountBase;
          break;
        case COMMISSION:
        case SWAP:
          cashDeltaAccountCurrency += amountAccountCurrency;
          fees += amountBase;
          break;
        case WITHHOLDING_TAX:
        case TRANSACTION_TAX:
        case STAMP_DUTY:
          cashDeltaAccountCurrency += amountAccountCurrency;
          taxes += amountBase;
          break;
        case TRANSFER:
        case SUBACCOUNT_TRANSFER:
          if (isAccountFundingTransfer(operation)) {
            cashDeltaAccountCurrency += amountAccountCurrency;
            if (amountBase >= 0.0) {
              deposits += amountBase;
            } else {
              withdrawals += amountBase;
            }
          } else if (isInvestmentTransfer(operation)) {
            hasTradeCashMovements = true;
            cashDeltaAccountCurrency += amountAccountCurrency;
            if (amountBase > 0.0) {
              sells += amountBase;
            } else if (amountBase < 0.0) {
              buys += -amountBase;
            }
          } else {
            cashDeltaAccountCurrency += amountAccountCurrency;
            cashAdjustments += amountBase;
          }
          break;
        case CLOSE_TRADE:
        case CORRECTION:
        case ROLLOVER:
          cashDeltaAccountCurrency += amountAccountCurrency;
          cashAdjustments += amountBase;
          break;
        case STOCK_PURCHASE:
          hasTradeCashMovements = true;
          hasCashSettlementRows = true;
          cashDeltaAccountCurrency += amountAccountCurrency;
          if (amountBase < 0 && !hasPositionValuation) {
            buys += -amountBase;
          } else if (amountBase >= 0) {
            cashAdjustments += amountBase;
          }
          break;
        case STOCK_SELL:
          hasTradeCashMovements = true;
          hasCashSettlementRows = true;
          cashDeltaAccountCurrency += amountAccountCurrency;
          if (amountBase > 0 && !hasPositionValuation) {
            sells += amountBase;
          } else if (amountBase <= 0) {
            cashAdjustments += amountBase;
          }
          break;
        case SEC_FEE:
          cashDeltaAccountCurrency += amountAccountCurrency;
          fees += amountBase;
          break;
        default:
          break;
      }
    }

    private void applyAccountFunding(double amountBase) {
      if (amountBase >= 0.0) {
        deposits += amountBase;
      } else {
        withdrawals += amountBase;
      }
    }

    private boolean isAccountFundingCashFlow(CashOperation operation) {
      return isExternalOrInternalAccountTransfer(operation) && !isInvestmentTransfer(operation);
    }

    private boolean isAccountFundingTransfer(CashOperation operation) {
      if (operation.getType() == CashOperationType.SUBACCOUNT_TRANSFER) {
        return true;
      }
      return isExternalOrInternalAccountTransfer(operation) && !isInvestmentTransfer(operation);
    }

    private boolean isExternalOrInternalAccountTransfer(CashOperation operation) {
      String comment = operation.getComment();
      if (!StringUtils.hasText(comment)) {
        return true;
      }
      String normalized = comment.toLowerCase(Locale.ROOT);
      return CashFlowAggregator.isExternalFunding(operation)
          || normalized.contains("currency conversion")
          || normalized.contains("transfer out operation")
          || normalized.contains("transfer in operation")
          || normalized.contains("transfer from")
          || normalized.contains("transfer to");
    }

    private boolean isInvestmentTransfer(CashOperation operation) {
      String comment = operation.getComment();
      if (!StringUtils.hasText(comment)) {
        return false;
      }
      String normalized = comment.toLowerCase(Locale.ROOT);
      return normalized.contains("corporate action")
          || normalized.contains("forex trade component")
          || normalized.contains("redemption")
          || normalized.contains("full call")
          || normalized.contains("early redemption")
          || normalized.contains("per bond");
    }
  }
}


