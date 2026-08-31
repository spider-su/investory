package com.smartbox.investory.investment.projection;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.cash.CashOperationNormalizer.NormalizedCategory;
import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationRepository;
import com.smartbox.investory.investment.ledger.cash.persistence.NormalizedCashOperationRepository;
import com.smartbox.investory.investment.ledger.position.PositionSettlementModel;
import com.smartbox.investory.investment.ledger.position.PositionType;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.performance.ModifiedDietzCalculator;
import com.smartbox.investory.investment.reporting.ReportingDateHelper;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.investment.valuation.price.AssetPriceHistoryGapFillService;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioProjectionService {

  private static final BigDecimal EPSILON = new BigDecimal("0.000000001");
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final long MAX_HISTORICAL_PRICE_STALENESS_DAYS = 10;
  private static final String QUALITY_EXACT_LISTING_MARKET_CLOSE = "EXACT_LISTING_MARKET_CLOSE";
  private static final String QUALITY_EXACT_LISTING_SCALED = "EXACT_LISTING_SCALED";
  private static final String QUALITY_STALE_CARRY_FORWARD = "STALE_CARRY_FORWARD";
  private final ReentrantLock rebuildLock = new ReentrantLock(true);

  private final PositionRepository positionRepository;
  private final CashOperationRepository cashOperationRepository;
  private final AssetRepository assetRepository;
  private final AccountRepository accountRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;
  private final AccountDailyRepository accountDailyRepository;
  private final CurrencyRateService currencyRateService;
  private final NormalizedCashOperationRepository normalizedCashOperationRepository;
  private final AssetPriceHistoryGapFillService assetPriceHistoryGapFillService;
  private final PortfolioProjectionRefreshService projectionRefreshService;

  @Transactional
  public void recalculateAll() {
    long started = System.nanoTime();
    log.info("Portfolio projection rebuild started mode=all");
    rebuildLock.lock();
    Set<Long> accountIds = Set.of();
    try {
      assetPriceHistoryGapFillService.fillMissingBusinessDayGaps(ReportingDateHelper.today());
      accountIds = allKnownAccountIds();
      recalculateAccounts(accountIds);
      log.info(
          "Portfolio projection rebuild completed mode=all accounts={} durationMs={}",
          accountIds.size(),
          (System.nanoTime() - started) / 1_000_000);
    } catch (RuntimeException | Error exception) {
      log.error(
          "Portfolio projection rebuild failed mode=all accounts={} durationMs={}",
          accountIds.size(),
          (System.nanoTime() - started) / 1_000_000,
          exception);
      throw exception;
    } finally {
      rebuildLock.unlock();
    }
  }

  /** Runs a rebuild in an independent transaction after a source-data mutation has committed. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recalculateAllInNewTransaction() {
    recalculateAll();
  }

  public void refreshReconciliationViews() {
    projectionRefreshService.refreshReconciliationViews();
  }

  @Transactional
  public void recalculateAccounts(Set<Long> accountIds) {
    long started = System.nanoTime();
    log.info(
        "Portfolio projection rebuild started mode=accounts requestedAccounts={}",
        accountIds == null ? 0 : accountIds.size());
    try {
      recalculateAccountsInternal(accountIds);
      log.info(
          "Portfolio projection rebuild completed mode=accounts requestedAccounts={} durationMs={}",
          accountIds == null ? 0 : accountIds.size(),
          (System.nanoTime() - started) / 1_000_000);
    } catch (RuntimeException | Error exception) {
      log.error(
          "Portfolio projection rebuild failed mode=accounts requestedAccounts={} durationMs={}",
          accountIds == null ? 0 : accountIds.size(),
          (System.nanoTime() - started) / 1_000_000,
          exception);
      throw exception;
    }
  }

  private void recalculateAccountsInternal(Set<Long> accountIds) {
    ZonedDateTime now = ReportingDateHelper.now();
    if (accountIds == null || accountIds.isEmpty()) {
      rebuildDerivedSummaries();
      return;
    }

    Map<Long, LocalDate> dirtyFromByAccount = earliestActivityDateByAccount(accountIds);
    Set<Long> affectedAccounts = dirtyFromByAccount.keySet();
    if (affectedAccounts.isEmpty()) {
      rebuildDerivedSummaries();
      return;
    }

    List<AccountEntity> allAccounts = accountRepository.findAllById(affectedAccounts);
    Set<Long> cashOnlyAccounts =
        allAccounts.stream()
            .filter(account -> affectedAccounts.contains(account.getId()))
            .filter(AccountEntity::isCashOnly)
            .map(AccountEntity::getId)
            .collect(Collectors.toSet());
    List<AssetEntity> allAssets = assetRepository.findAll();
    Set<Long> excludedAssetIds =
        allAssets.stream()
            .filter(asset -> Boolean.TRUE.equals(asset.getExcludeFromImport()))
            .map(AssetEntity::getId)
            .collect(Collectors.toSet());
    List<PositionEntity> opened =
        positionRepository.findOpenByAccountIn(affectedAccounts).stream()
            .filter(position -> !cashOnlyAccounts.contains(position.getAccount()))
            .filter(position -> !excludedAssetIds.contains(position.getAssetId()))
            .toList();
    List<PositionEntity> closed =
        positionRepository.findClosedByAccountIn(affectedAccounts).stream()
            .filter(position -> !cashOnlyAccounts.contains(position.getAccount()))
            .filter(position -> !excludedAssetIds.contains(position.getAssetId()))
            .toList();
    Map<String, AssetEntity> assets =
        allAssets.stream()
            .filter(asset -> StringUtils.hasText(asset.getSymbol()))
            .filter(asset -> !Boolean.TRUE.equals(asset.getExcludeFromImport()))
            .collect(
                Collectors.toMap(
                    AssetEntity::getSymbol,
                    asset -> asset,
                    (first, ignored) -> first,
                    LinkedHashMap::new));
    Map<Long, CurrencyType> accountCurrencies =
        allAccounts.stream()
            .filter(account -> account.getId() != null)
            .collect(
                Collectors.toMap(
                    AccountEntity::getId, AccountEntity::getCurrency, (first, ignored) -> first));

    Map<PositionKey, PositionAccumulator> positions = new HashMap<>();
    Map<DayTickerKey, TickerMonthAccumulator> tickerDaily = new HashMap<>();
    Map<DayAccountKey, AccountMonthAccumulator> accountDaily = new HashMap<>();

    Map<Long, CurrencyType> portfolioBaseCurrencies =
        accountRepository.findPortfolioCurrenciesByAccountIdIn(affectedAccounts).stream()
            .collect(
                Collectors.toMap(
                    AccountRepository.AccountPortfolioCurrencyRow::getAccountId,
                    row -> parseCurrency(row.getBaseCurrency())));
    processOpened(opened, positions, tickerDaily, portfolioBaseCurrencies);
    processClosed(closed, positions, tickerDaily, portfolioBaseCurrencies);
    Set<AccountTickerKey> positionValuedTickers =
        tickerDaily.keySet().stream()
            .map(key -> new AccountTickerKey(key.accountId(), key.ticker()))
            .collect(Collectors.toSet());
    Set<AccountTickerKey> resultOnlyTickers =
        opened.stream()
            .filter(
                position -> position.getSettlementModel() == PositionSettlementModel.RESULT_ONLY)
            .filter(
                position ->
                    position.getAccount() != null && StringUtils.hasText(position.getSymbol()))
            .map(position -> new AccountTickerKey(position.getAccount(), position.getSymbol()))
            .collect(Collectors.toSet());
    resultOnlyTickers.addAll(
        closed.stream()
            .filter(
                position -> position.getSettlementModel() == PositionSettlementModel.RESULT_ONLY)
            .filter(
                position ->
                    position.getAccount() != null && StringUtils.hasText(position.getSymbol()))
            .map(position -> new AccountTickerKey(position.getAccount(), position.getSymbol()))
            .collect(Collectors.toSet()));
    List<CanonicalNormalizedCashOperation> normalizedCash =
        loadNormalizedCashOperations(affectedAccounts);
    processCash(
        normalizedCash,
        positions,
        tickerDaily,
        accountDaily,
        positionValuedTickers,
        resultOnlyTickers,
        cashOnlyAccounts);

    HistoricalPriceBook historicalPrices =
        loadHistoricalPrices(tickerDaily.keySet(), now.toLocalDate());

    List<AccountDailyEntity> accountRows =
        buildAccountDailyRows(
            accountDaily,
            tickerDaily,
            assets,
            historicalPrices,
            accountCurrencies,
            portfolioBaseCurrencies,
            now,
            cashOnlyAccounts,
            opened,
            closed);
    replaceAccountDerivedRows(accountRows, dirtyFromByAccount);
    rebuildDerivedSummaries();

    log.info(
        "Portfolio projection account_daily replacement accounts={} rows={} dirtyFromByAccount={}",
        affectedAccounts.size(),
        accountRows.size(),
        dirtyFromByAccount);
  }

  private void replaceAccountDerivedRows(
      List<AccountDailyEntity> accountRows, Map<Long, LocalDate> dirtyFromByAccount) {
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
                        .isBefore(
                            dirtyFromByAccount.getOrDefault(row.getAccountId(), row.getDate())))
            .toList());
  }

  private Set<Long> allKnownAccountIds() {
    Set<Long> accountIds = new HashSet<>();
    accountIds.addAll(positionRepository.findDistinctAccountIds());
    cashOperationRepository.findAllAccountIds().forEach(accountIds::add);
    return accountIds;
  }

  private Map<Long, LocalDate> earliestActivityDateByAccount(Set<Long> accountIds) {
    Map<Long, LocalDate> earliest = new HashMap<>();
    positionRepository
        .findOpenByAccountIn(accountIds)
        .forEach(
            position ->
                includeEarlier(earliest, position.getAccount(), toDate(position.getOpenTime())));
    positionRepository
        .findClosedByAccountIn(accountIds)
        .forEach(
            position -> {
              includeEarlier(earliest, position.getAccount(), toDate(position.getOpenTime()));
              includeEarlier(earliest, position.getAccount(), toDate(position.getCloseTime()));
            });
    cashOperationRepository
        .findAllByAccountIn(accountIds)
        .forEach(
            operation ->
                includeEarlier(earliest, operation.getAccount(), toDate(operation.getDate())));
    return earliest;
  }

  private static void includeEarlier(
      Map<Long, LocalDate> earliest, Long accountId, LocalDate candidate) {
    if (accountId == null || candidate == null) {
      return;
    }
    earliest.merge(accountId, candidate, (left, right) -> left.isBefore(right) ? left : right);
  }

  private void processOpened(
      List<PositionEntity> opened,
      Map<PositionKey, PositionAccumulator> positions,
      Map<DayTickerKey, TickerMonthAccumulator> tickerDaily,
      Map<Long, CurrencyType> portfolioBaseCurrencies) {
    for (PositionEntity position : opened) {
      if (position.getAccount() == null || !StringUtils.hasText(position.getSymbol())) {
        continue;
      }
      if (position.getSettlementModel() != PositionSettlementModel.CASH_SETTLED) {
        logResultOnlyOpenPosition(position);
        continue;
      }

      CurrencyType costCurrency =
          requiredCurrency(position.getCostCurrency(), position.getId(), "cost");
      CurrencyType profitCurrency =
          requiredCurrency(position.getProfitCurrency(), position.getId(), "profit");
      CurrencyType commissionCurrency =
          requiredCurrency(position.getCommissionCurrency(), position.getId(), "commission");
      LocalDate date = toDate(position.getOpenTime());
      BigDecimal volume = nz(position.getVolume()).abs();
      if (volume.compareTo(EPSILON) <= 0) {
        continue;
      }

      BigDecimal openValue = nz(position.getPurchaseValue());
      if (openValue.compareTo(EPSILON) <= 0) {
        openValue = volume.multiply(nz(position.getOpenPrice()));
      }

      PositionKey key = new PositionKey(position.getAccount(), position.getSymbol(), costCurrency);
      PositionAccumulator acc =
          positions.computeIfAbsent(key, ignored -> new PositionAccumulator());
      acc.applyOpen(position.getType(), volume, openValue, date);
      acc.unrealizedProfit =
          acc.unrealizedProfit.add(
              convertBetween(
                      nz(position.getProfit()).add(nz(position.getSwap())),
                      costCurrency,
                      profitCurrency,
                      date)
                  .add(
                      convertBetween(
                          nz(position.getCommission()), costCurrency, commissionCurrency, date)));
      acc.totalCommission =
          acc.totalCommission.add(
              convertBetween(nz(position.getCommission()), costCurrency, commissionCurrency, date)
                  .add(convertBetween(nz(position.getSwap()), costCurrency, profitCurrency, date)));

      DayTickerKey dayKey = DayTickerKey.of(position.getAccount(), position.getSymbol(), date);
      TickerMonthAccumulator dayAcc =
          tickerDaily.computeIfAbsent(dayKey, ignored -> new TickerMonthAccumulator());
      if (position.getType() == PositionType.SELL) {
        BigDecimal valueBase =
            convert(
                openValue,
                baseCurrency(position.getAccount(), portfolioBaseCurrencies),
                costCurrency,
                date);
        dayAcc.sellQty = dayAcc.sellQty.add(volume);
        dayAcc.sellValue = dayAcc.sellValue.add(valueBase);
      } else {
        BigDecimal valueBase =
            convert(
                openValue,
                baseCurrency(position.getAccount(), portfolioBaseCurrencies),
                costCurrency,
                date);
        dayAcc.buyQty = dayAcc.buyQty.add(volume);
        dayAcc.buyValue = dayAcc.buyValue.add(valueBase);
      }
    }
  }

  private void processClosed(
      List<PositionEntity> closed,
      Map<PositionKey, PositionAccumulator> positions,
      Map<DayTickerKey, TickerMonthAccumulator> tickerDaily,
      Map<Long, CurrencyType> portfolioBaseCurrencies) {
    for (PositionEntity position : closed) {
      if (position.getAccount() == null
          || !StringUtils.hasText(position.getSymbol())
          || position.getCloseTime() == null) {
        continue;
      }
      if (position.getSettlementModel() != PositionSettlementModel.CASH_SETTLED) {
        continue;
      }

      CurrencyType costCurrency =
          requiredCurrency(position.getCostCurrency(), position.getId(), "cost");
      CurrencyType profitCurrency =
          requiredCurrency(position.getProfitCurrency(), position.getId(), "profit");
      CurrencyType commissionCurrency =
          requiredCurrency(position.getCommissionCurrency(), position.getId(), "commission");
      LocalDate openDate = toDate(position.getOpenTime());
      LocalDate closeDate = toDate(position.getCloseTime());
      BigDecimal volume = nz(position.getVolume()).abs();
      if (volume.compareTo(EPSILON) <= 0) {
        continue;
      }

      BigDecimal openValue = nz(position.getPurchaseValue());
      if (openValue.compareTo(EPSILON) <= 0) {
        openValue = volume.multiply(nz(position.getOpenPrice()));
      }
      BigDecimal closeValue = nz(position.getSaleValue());
      if (closeValue.compareTo(EPSILON) <= 0) {
        closeValue = volume.multiply(nz(position.getClosePrice()));
      }
      BigDecimal netRealized =
          convertBetween(
                  nz(position.getProfit()).add(nz(position.getSwap())),
                  costCurrency,
                  profitCurrency,
                  closeDate)
              .add(
                  convertBetween(
                      nz(position.getCommission()), costCurrency, commissionCurrency, closeDate));

      PositionKey key = new PositionKey(position.getAccount(), position.getSymbol(), costCurrency);
      PositionAccumulator acc =
          positions.computeIfAbsent(key, ignored -> new PositionAccumulator());
      if (position.getType() == PositionType.SELL) {
        acc.applyOpen(PositionType.SELL, volume, openValue, openDate);
        acc.applyClose(PositionType.SELL, volume, closeValue, closeDate);
      } else {
        acc.applyOpen(PositionType.BUY, volume, openValue, openDate);
        acc.applyClose(PositionType.BUY, volume, closeValue, closeDate);
      }
      acc.realizedProfit = acc.realizedProfit.add(netRealized);
      acc.totalCommission =
          acc.totalCommission.add(
              convertBetween(
                      nz(position.getCommission()), costCurrency, commissionCurrency, closeDate)
                  .add(
                      convertBetween(
                          nz(position.getSwap()), costCurrency, profitCurrency, closeDate)));

      DayTickerKey openDay = DayTickerKey.of(position.getAccount(), position.getSymbol(), openDate);
      DayTickerKey closeDay =
          DayTickerKey.of(position.getAccount(), position.getSymbol(), closeDate);
      TickerMonthAccumulator openAcc =
          tickerDaily.computeIfAbsent(openDay, ignored -> new TickerMonthAccumulator());
      TickerMonthAccumulator closeAcc =
          tickerDaily.computeIfAbsent(closeDay, ignored -> new TickerMonthAccumulator());
      CurrencyType portfolioBase = baseCurrency(position.getAccount(), portfolioBaseCurrencies);
      BigDecimal openValueBase = convert(openValue, portfolioBase, costCurrency, openDate);
      BigDecimal closeValueBase = convert(closeValue, portfolioBase, costCurrency, closeDate);
      if (position.getType() == PositionType.SELL) {
        openAcc.sellQty = openAcc.sellQty.add(volume);
        openAcc.sellValue = openAcc.sellValue.add(openValueBase);
        closeAcc.buyQty = closeAcc.buyQty.add(volume);
        closeAcc.buyValue = closeAcc.buyValue.add(closeValueBase);
      } else {
        openAcc.buyQty = openAcc.buyQty.add(volume);
        openAcc.buyValue = openAcc.buyValue.add(openValueBase);
        closeAcc.sellQty = closeAcc.sellQty.add(volume);
        closeAcc.sellValue = closeAcc.sellValue.add(closeValueBase);
      }
      closeAcc.realizedProfit =
          closeAcc.realizedProfit.add(convert(netRealized, portfolioBase, costCurrency, closeDate));
    }
  }

  private void processCash(
      List<CanonicalNormalizedCashOperation> cash,
      Map<PositionKey, PositionAccumulator> positions,
      Map<DayTickerKey, TickerMonthAccumulator> tickerDaily,
      Map<DayAccountKey, AccountMonthAccumulator> accountDaily,
      Set<AccountTickerKey> positionValuedTickers,
      Set<AccountTickerKey> resultOnlyTickers,
      Set<Long> cashOnlyAccounts) {
    for (CanonicalNormalizedCashOperation normalized : cash) {
      if (normalized.accountId() == null || normalized.normalizedCategory() == null) {
        continue;
      }

      CurrencyType currency = normalized.currency();
      LocalDate date = normalized.date();
      BigDecimal amount = normalized.amount();
      if (!normalized.isComplete()) {
        throw new IllegalStateException(
            "Cash operation "
                + normalized.operationId()
                + " has unavailable portfolio/account FX: portfolioStatus="
                + normalized.portfolioConversionStatus()
                + ", accountStatus="
                + normalized.accountConversionStatus());
      }
      BigDecimal amountBase = normalized.amountInPortfolioBaseCurrency();
      BigDecimal amountAccountCurrency = normalized.amountInAccountCurrency();

      /*
       * Daily cash-flow fields in account_daily come from one canonical source only:
       * normalized cash operations.
       *
       * PositionEntity pipelines feed valuation and realized trade P/L, but never deposits,
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
      accountAcc.apply(
          normalized,
          amountBase,
          amountAccountCurrency,
          normalized.accountFlowAmountInPortfolioBaseCurrency(),
          normalized.performanceFlowAmountInPortfolioBaseCurrency(),
          cashOnlyAccounts.contains(normalized.accountId()));

      if (CashOperationType.SWAP.name().equals(normalized.rawOperation())
          && resultOnlyTickers.contains(
              new AccountTickerKey(normalized.accountId(), normalized.symbol()))) {
        accountAcc.realizedProfit = accountAcc.realizedProfit.add(amountBase);
      }

      if (!StringUtils.hasText(normalized.symbol())) {
        continue;
      }
      DayTickerKey tickerKey = DayTickerKey.of(normalized.accountId(), normalized.symbol(), date);
      TickerMonthAccumulator tickerAcc =
          tickerDaily.computeIfAbsent(tickerKey, ignored -> new TickerMonthAccumulator());

      PositionKey positionKey =
          new PositionKey(normalized.accountId(), normalized.symbol(), currency);
      PositionAccumulator positionAcc =
          positions.computeIfAbsent(positionKey, ignored -> new PositionAccumulator());
      positionAcc.touch(date);

      if (normalized.normalizedCategory() == NormalizedCategory.DIVIDEND
          || normalized.normalizedCategory() == NormalizedCategory.DIVIDEND_REVERSAL) {
        tickerAcc.dividends = tickerAcc.dividends.add(amountBase);
        positionAcc.totalDividends = positionAcc.totalDividends.add(amount);
      }
      if (!hasPositionValuation
          && normalized.normalizedCategory() == NormalizedCategory.TRADE_PURCHASE) {
        tickerAcc.buyValue = tickerAcc.buyValue.add(amountBase.abs());
      }
      if (!hasPositionValuation
          && normalized.normalizedCategory() == NormalizedCategory.TRADE_SALE) {
        tickerAcc.sellValue = tickerAcc.sellValue.add(amountBase.abs());
      }
      if (isTaxCategory(normalized.normalizedCategory())) {
        positionAcc.totalTax = positionAcc.totalTax.subtract(amount);
      }
      if (isFeeCategory(normalized.normalizedCategory())) {
        positionAcc.totalCommission = positionAcc.totalCommission.subtract(amount);
      }
    }
  }

  private List<CanonicalNormalizedCashOperation> loadNormalizedCashOperations(
      Set<Long> affectedAccounts) {
    return normalizedCashOperationRepository.findAllDetailedByAccountIdIn(affectedAccounts).stream()
        .map(
            row ->
                new CanonicalNormalizedCashOperation(
                    row.getOperationId(),
                    row.getAccountId(),
                    row.getSymbol(),
                    parseCurrency(row.getCurrency()),
                    row.getRawOperation(),
                    parseCategory(row.getNormalizedCategory()),
                    nz(row.getAmount()),
                    decimal(row.getAmountInPortfolioBaseCurrency()),
                    row.getPortfolioConversionStatus(),
                    accountFlowAmount(row, parseCategory(row.getNormalizedCategory())),
                    performanceFlowAmount(row, parseCategory(row.getNormalizedCategory())),
                    decimal(row.getAmountInAccountCurrency()),
                    row.getAccountConversionStatus(),
                    row.getDate(),
                    row.getComment()))
        .toList();
  }

  private static BigDecimal accountFlowAmount(
      NormalizedCashOperationRepository.NormalizedCashOperationRow row,
      NormalizedCategory category) {
    if (row.getAccountFlowAmountInPortfolioBaseCurrency() != null) {
      return decimal(row.getAccountFlowAmountInPortfolioBaseCurrency());
    }
    return switch (category) {
      case EXTERNAL_DEPOSIT, EXTERNAL_WITHDRAWAL, INTERNAL_TRANSFER_IN, INTERNAL_TRANSFER_OUT ->
          decimal(row.getAmountInPortfolioBaseCurrency());
      default -> null;
    };
  }

  private static BigDecimal performanceFlowAmount(
      NormalizedCashOperationRepository.NormalizedCashOperationRow row,
      NormalizedCategory category) {
    if (row.getPerformanceFlowAmountInPortfolioBaseCurrency() != null) {
      return decimal(row.getPerformanceFlowAmountInPortfolioBaseCurrency());
    }
    return switch (category) {
      case EXTERNAL_DEPOSIT, EXTERNAL_WITHDRAWAL, INTERNAL_TRANSFER_IN, INTERNAL_TRANSFER_OUT ->
          decimal(row.getAmountInPortfolioBaseCurrency());
      default -> null;
    };
  }

  private List<AccountDailyEntity> buildAccountDailyRows(
      Map<DayAccountKey, AccountMonthAccumulator> accountDaily,
      Map<DayTickerKey, TickerMonthAccumulator> tickerDaily,
      Map<String, AssetEntity> assets,
      HistoricalPriceBook historicalPrices,
      Map<Long, CurrencyType> accountCurrencies,
      Map<Long, CurrencyType> portfolioBaseCurrencies,
      ZonedDateTime now,
      Set<Long> cashOnlyAccounts,
      List<PositionEntity> opened,
      List<PositionEntity> closed) {
    Map<DayAccountKey, AccountMonthAccumulator> aggregated = new HashMap<>(accountDaily);

    Map<Long, DayRange> accountRanges = new HashMap<>();
    for (DayAccountKey key : accountDaily.keySet()) {
      accountRanges.computeIfAbsent(key.accountId(), ignored -> new DayRange()).include(key.date());
    }

    Map<AccountTickerKey, Map<LocalDate, TickerMonthAccumulator>> byTicker = new HashMap<>();
    for (Map.Entry<DayTickerKey, TickerMonthAccumulator> entry : tickerDaily.entrySet()) {
      DayTickerKey key = entry.getKey();
      byTicker
          .computeIfAbsent(
              new AccountTickerKey(key.accountId(), key.ticker()), ignored -> new HashMap<>())
          .put(key.date(), entry.getValue());
      accountRanges.computeIfAbsent(key.accountId(), ignored -> new DayRange()).include(key.date());
    }
    LocalDate currentDate = now.toLocalDate();
    for (Long accountId : cashOnlyAccounts) {
      accountRanges.computeIfAbsent(accountId, ignored -> new DayRange()).include(currentDate);
    }
    for (Long accountId : accountsWithOpenShares(byTicker)) {
      accountRanges.computeIfAbsent(accountId, ignored -> new DayRange()).include(currentDate);
    }

    Map<Long, List<LocalDate>> accountDays = new HashMap<>();
    for (Map.Entry<Long, DayRange> entry : accountRanges.entrySet()) {
      List<LocalDate> days = entry.getValue().days();
      accountDays.put(entry.getKey(), days);
      for (LocalDate day : days) {
        aggregated.computeIfAbsent(
            new DayAccountKey(entry.getKey(), day), ignored -> new AccountMonthAccumulator());
      }
    }

    for (Map.Entry<AccountTickerKey, Map<LocalDate, TickerMonthAccumulator>> entry :
        byTicker.entrySet()) {
      BigDecimal shares = ZERO;
      BigDecimal runningCostBasis = ZERO;
      LocalDate holdingStartDate = null;
      for (LocalDate day : accountDays.getOrDefault(entry.getKey().accountId(), List.of())) {
        TickerMonthAccumulator tickerAcc =
            entry.getValue().getOrDefault(day, new TickerMonthAccumulator());
        if (tickerAcc.buyQty.compareTo(EPSILON) > 0) {
          if (shares.compareTo(EPSILON) <= 0) {
            holdingStartDate = day;
          }
          shares = shares.add(tickerAcc.buyQty);
          runningCostBasis = runningCostBasis.add(tickerAcc.buyValue);
        }
        if (tickerAcc.sellQty.compareTo(EPSILON) > 0) {
          BigDecimal avg =
              shares.compareTo(EPSILON) > 0
                  ? runningCostBasis.divide(shares, 16, java.math.RoundingMode.HALF_UP)
                  : ZERO;
          BigDecimal sold = shares.min(tickerAcc.sellQty);
          runningCostBasis = runningCostBasis.subtract(sold.multiply(avg)).max(ZERO);
          shares = shares.subtract(tickerAcc.sellQty).max(ZERO);
          if (shares.compareTo(EPSILON) <= 0) {
            holdingStartDate = null;
          }
        }

        AssetEntity asset = assets.get(entry.getKey().ticker());
        BigDecimal marketValue =
            historicalPrices.marketValue(
                entry.getKey().ticker(),
                shares,
                day,
                currentDate,
                holdingStartDate,
                asset,
                baseCurrency(entry.getKey().accountId(), portfolioBaseCurrencies));

        AccountMonthAccumulator acc =
            aggregated.computeIfAbsent(
                new DayAccountKey(entry.getKey().accountId(), day),
                ignored -> new AccountMonthAccumulator());
        acc.buys = acc.buys.add(tickerAcc.buyValue);
        acc.sells = acc.sells.add(tickerAcc.sellValue);
        acc.realizedProfit = acc.realizedProfit.add(tickerAcc.realizedProfit);
        acc.endingMarketValue = acc.endingMarketValue.add(marketValue);
        acc.costBase = acc.costBase.add(runningCostBasis);
      }
    }

    Map<DayAccountKey, BigDecimal> canonicalPositionCosts =
        canonicalPositionCostsByDay(opened, closed, accountDays, portfolioBaseCurrencies);
    for (Map.Entry<DayAccountKey, AccountMonthAccumulator> entry : aggregated.entrySet()) {
      BigDecimal canonicalCost = canonicalPositionCosts.get(entry.getKey());
      if (canonicalCost != null) {
        entry.getValue().costBase = canonicalCost;
      }
    }

    Map<Long, List<DayAccountEntry>> byAccount = new HashMap<>();
    for (Map.Entry<DayAccountKey, AccountMonthAccumulator> entry : aggregated.entrySet()) {
      byAccount
          .computeIfAbsent(entry.getKey().accountId(), ignored -> new ArrayList<>())
          .add(new DayAccountEntry(entry.getKey(), entry.getValue()));
    }

    List<AccountDailyEntity> rows = new ArrayList<>();
    for (Map.Entry<Long, List<DayAccountEntry>> account : byAccount.entrySet()) {
      List<DayAccountEntry> entries = account.getValue();
      entries.sort(Comparator.comparing(DayAccountEntry::date));

      CurrencyType accountCurrency = accountCurrencies.get(account.getKey());
      if (accountCurrency == null) {
        throw new IllegalStateException("Missing account currency for account " + account.getKey());
      }
      CurrencyType portfolioBase = baseCurrency(account.getKey(), portfolioBaseCurrencies);
      BigDecimal previousCash = ZERO;
      BigDecimal previousCashAccountCurrency = ZERO;
      BigDecimal previousMarket = ZERO;
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
        BigDecimal cashBalanceAccountCurrency =
            previousCashAccountCurrency.add(acc.cashDeltaAccountCurrency);
        BigDecimal cashBalance =
            convert(cashBalanceAccountCurrency, portfolioBase, accountCurrency, entry.key().date());
        BigDecimal marketValue =
            acc.endingMarketValue.compareTo(EPSILON) > 0 ? acc.endingMarketValue : ZERO;
        BigDecimal equity = cashBalance.add(marketValue);
        BigDecimal previousEquity = previousCash.add(previousMarket);
        BigDecimal costBase = acc.costBase.compareTo(EPSILON) > 0 ? acc.costBase : ZERO;
        BigDecimal unrealizedProfit = marketValue.subtract(costBase);
        /*
         * Canonical daily profit must reconcile to the same boundary formula used by
         * account_monthly_mv:
         *
         *   daily profit = closing equity - opening equity - performance flow
         *
         * Performance flow includes external funding and genuine tracked-account transfers.
         * Internal bookkeeping, FX conversion, trade settlement, dividends, interest, fees, taxes,
         * and realized trade rows already affect equity through the canonical cash ledger and/or
         * market valuation, so they must not be added again here.
         */
        BigDecimal dailyProfit =
            ModifiedDietzCalculator.profit(previousEquity, equity, acc.performanceFlow);
        BigDecimal dailyReturn =
            ModifiedDietzCalculator.returnRate(
                previousEquity, equity, List.of(acc.performanceFlow));

        rows.add(
            AccountDailyEntity.builder()
                .accountId(entry.key().accountId())
                .date(entry.key().date())
                .valuationCurrency(portfolioBase.name())
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

    rows.sort(
        Comparator.comparing(AccountDailyEntity::getAccountId)
            .thenComparing(AccountDailyEntity::getDate));
    return rows;
  }

  private Map<DayAccountKey, BigDecimal> canonicalPositionCostsByDay(
      List<PositionEntity> opened,
      List<PositionEntity> closed,
      Map<Long, List<LocalDate>> accountDays,
      Map<Long, CurrencyType> portfolioBaseCurrencies) {
    Map<DayAccountKey, BigDecimal> costs = new HashMap<>();
    opened.forEach(
        position ->
            addCanonicalPositionCost(
                costs,
                accountDays,
                portfolioBaseCurrencies,
                position.getAccount(),
                position.getCostCurrency(),
                position.getPurchaseValue(),
                position.getVolume(),
                position.getOpenPrice(),
                position.getOpenTime() == null ? null : toDate(position.getOpenTime()),
                null));
    closed.forEach(
        position ->
            addCanonicalPositionCost(
                costs,
                accountDays,
                portfolioBaseCurrencies,
                position.getAccount(),
                position.getCostCurrency(),
                position.getPurchaseValue(),
                position.getVolume(),
                position.getOpenPrice(),
                position.getOpenTime() == null ? null : toDate(position.getOpenTime()),
                position.getCloseTime() == null ? null : toDate(position.getCloseTime())));
    return costs;
  }

  private void addCanonicalPositionCost(
      Map<DayAccountKey, BigDecimal> costs,
      Map<Long, List<LocalDate>> accountDays,
      Map<Long, CurrencyType> portfolioBaseCurrencies,
      Long accountId,
      CurrencyType costCurrency,
      BigDecimal purchaseValue,
      BigDecimal volume,
      BigDecimal openPrice,
      LocalDate openDate,
      LocalDate closeDate) {
    if (accountId == null || costCurrency == null || openDate == null) {
      return;
    }
    BigDecimal nativeCost = nz(purchaseValue);
    if (nativeCost.compareTo(EPSILON) <= 0) {
      nativeCost = nz(volume).abs().multiply(nz(openPrice));
    }
    BigDecimal baseCost =
        convert(
            nativeCost, baseCurrency(accountId, portfolioBaseCurrencies), costCurrency, openDate);
    for (LocalDate day : accountDays.getOrDefault(accountId, List.of())) {
      if (day.isBefore(openDate) || (closeDate != null && !day.isBefore(closeDate))) {
        continue;
      }
      costs.merge(new DayAccountKey(accountId, day), baseCost, BigDecimal::add);
    }
  }

  private void rebuildDerivedSummaries() {
    accountDailyRepository.refreshReportingViews();
  }

  private static BigDecimal decimal(Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  private static BigDecimal nz(Double value) {
    return value == null ? ZERO : BigDecimal.valueOf(value);
  }

  private static BigDecimal nz(BigDecimal value) {
    return com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(value);
  }

  private static LocalDate toDate(ZonedDateTime dateTime) {
    return ReportingDateHelper.toReportingDate(dateTime);
  }

  private Set<Long> accountsWithOpenShares(
      Map<AccountTickerKey, Map<LocalDate, TickerMonthAccumulator>> byTicker) {
    Set<Long> accounts = new HashSet<>();
    for (Map.Entry<AccountTickerKey, Map<LocalDate, TickerMonthAccumulator>> entry :
        byTicker.entrySet()) {
      BigDecimal shares = ZERO;
      List<LocalDate> days = new ArrayList<>(entry.getValue().keySet());
      days.sort(Comparator.naturalOrder());
      for (LocalDate day : days) {
        TickerMonthAccumulator acc = entry.getValue().get(day);
        shares = shares.add(acc.buyQty).subtract(acc.sellQty);
      }
      if (shares.compareTo(EPSILON) > 0) {
        accounts.add(entry.getKey().accountId());
      }
    }
    return accounts;
  }

  private HistoricalPriceBook loadHistoricalPrices(
      Set<DayTickerKey> tickerDays, LocalDate currentDate) {
    if (tickerDays.isEmpty()) {
      return emptyHistoricalPriceBook();
    }

    Set<String> symbols = tickerDays.stream().map(DayTickerKey::ticker).collect(Collectors.toSet());
    LocalDate dateTo =
        tickerDays.stream()
            .map(DayTickerKey::date)
            .reduce(currentDate, (latest, date) -> date.isAfter(latest) ? date : latest);

    Map<String, NavigableMap<LocalDate, HistoricalPrice>> prices = new HashMap<>();
    for (AssetPriceHistoryRepository.HistoricalAssetPriceRow row :
        assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(symbols, dateTo)) {
      if (!StringUtils.hasText(row.getSymbol())
          || row.getPriceDate() == null
          || row.getClosePrice() == null) {
        continue;
      }
      CurrencyType currency = parseCurrency(row.getPriceCurrency());
      BigDecimal scaleFactor =
          row.getPriceScaleFactor() == null ? BigDecimal.ONE : row.getPriceScaleFactor();
      BigDecimal scaledClosePriceValue = row.getClosePrice().multiply(scaleFactor);
      BigDecimal scaledClosePrice = scaledClosePriceValue;
      if (scaledClosePrice.compareTo(EPSILON) <= 0) {
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
              resolveContractMultiplier(row.getQualityClass()),
              Boolean.TRUE.equals(row.getEstimated()),
              row.getInterpolationLeftDate(),
              row.getInterpolationRightDate());
      NavigableMap<LocalDate, HistoricalPrice> symbolPrices =
          prices.computeIfAbsent(row.getSymbol(), ignored -> new TreeMap<>());
      HistoricalPrice existing = symbolPrices.get(row.getPriceDate());
      if (existing == null || isBetterHistoricalPrice(candidate, existing)) {
        symbolPrices.put(row.getPriceDate(), candidate);
      }
    }
    return new HistoricalPriceBook(prices);
  }

  private static boolean isBetterHistoricalPrice(
      HistoricalPrice candidate, HistoricalPrice existing) {
    int dateComparison =
        candidate.effectiveObservationDate().compareTo(existing.effectiveObservationDate());
    if (dateComparison != 0) {
      return dateComparison > 0;
    }
    int candidateRank = valuationPriorityRank(candidate.qualityClass(), candidate.priceOrigin());
    int existingRank = valuationPriorityRank(existing.qualityClass(), existing.priceOrigin());
    if (candidateRank != existingRank) {
      return candidateRank < existingRank;
    }
    if (candidate.qualityScore() != existing.qualityScore()) {
      return candidate.qualityScore() > existing.qualityScore();
    }
    return tradeObservationTieRank(candidate.priceOrigin())
        < tradeObservationTieRank(existing.priceOrigin());
  }

  private void logResultOnlyOpenPosition(PositionEntity position) {
    if (position.getSettlementModel() == PositionSettlementModel.RESULT_ONLY) {
      log.warn(
          "Open result-only position omitted from notional valuation: positionId={}, accountId={}, symbol={}",
          position.getId(),
          position.getAccount(),
          position.getSymbol());
    } else {
      log.warn(
          "Open position with unclassified settlement omitted from valuation: positionId={}, accountId={}, symbol={}",
          position.getId(),
          position.getAccount(),
          position.getSymbol());
    }
  }

  private static int tradeObservationTieRank(String priceOrigin) {
    String origin = priceOrigin == null ? "" : priceOrigin.trim().toUpperCase(Locale.ROOT);
    if (origin.equals("XTB_TRADE_OPEN")) {
      return 0;
    }
    if (origin.equals("XTB_TRADE_CLOSE")) {
      return 2;
    }
    return 1;
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

  private static BigDecimal resolveContractMultiplier(String qualityClass) {
    String quality = qualityClass == null ? "" : qualityClass.trim().toUpperCase(Locale.ROOT);
    if (quality.contains("PERCENT_OF_PAR")) {
      return new BigDecimal("0.01");
    }
    return BigDecimal.ONE;
  }

  private static CurrencyType parseCurrency(String value) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("Missing monetary currency in projection input");
    }
    try {
      return CurrencyType.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unsupported monetary currency: " + value, exception);
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

  private BigDecimal convert(
      BigDecimal amount, CurrencyType targetCurrency, CurrencyType sourceCurrency, LocalDate date) {
    return currencyRateService.convertToBaseCurrency(amount, targetCurrency, sourceCurrency, date);
  }

  private BigDecimal convertBetween(
      BigDecimal amount, CurrencyType targetCurrency, CurrencyType sourceCurrency, LocalDate date) {
    if (targetCurrency == sourceCurrency) {
      return amount;
    }
    return currencyRateService.convertToBaseCurrency(amount, targetCurrency, sourceCurrency, date);
  }

  private static CurrencyType baseCurrency(
      Long accountId, Map<Long, CurrencyType> portfolioBaseCurrencies) {
    CurrencyType baseCurrency = portfolioBaseCurrencies.get(accountId);
    if (baseCurrency == null) {
      throw new IllegalStateException("Missing portfolio base currency for account " + accountId);
    }
    return baseCurrency;
  }

  private static CurrencyType requiredCurrency(
      CurrencyType currency, Long positionId, String role) {
    if (currency == null) {
      throw new IllegalArgumentException(
          "PositionEntity " + positionId + " has no " + role + " currency");
    }
    return currency;
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

  private record AccountTickerKey(Long accountId, String ticker) {}

  private record DayTickerKey(Long accountId, String ticker, LocalDate date) {
    static DayTickerKey of(Long accountId, String ticker, LocalDate date) {
      return new DayTickerKey(accountId, ticker, date);
    }
  }

  private record HistoricalPrice(
      LocalDate valuationPriceDate,
      LocalDate observationDate,
      BigDecimal normalizedClosePrice,
      CurrencyType currency,
      String source,
      String sourceSymbol,
      String originalSourceSymbol,
      String priceOrigin,
      String qualityClass,
      int qualityScore,
      BigDecimal contractMultiplier,
      boolean estimated,
      LocalDate interpolationLeftDate,
      LocalDate interpolationRightDate) {
    private LocalDate effectiveObservationDate() {
      if (estimated && interpolationLeftDate != null) {
        return interpolationLeftDate;
      }
      return observationDate != null ? observationDate : valuationPriceDate;
    }

    private boolean isAdmissibleOn(LocalDate valuationDate) {
      return !effectiveObservationDate().isAfter(valuationDate)
          && (!estimated
              || interpolationRightDate == null
              || !interpolationRightDate.isAfter(valuationDate));
    }
  }

  private record CanonicalNormalizedCashOperation(
      Long operationId,
      Long accountId,
      String symbol,
      CurrencyType currency,
      String rawOperation,
      NormalizedCategory normalizedCategory,
      BigDecimal amount,
      BigDecimal amountInPortfolioBaseCurrency,
      String portfolioConversionStatus,
      BigDecimal accountFlowAmountInPortfolioBaseCurrency,
      BigDecimal performanceFlowAmountInPortfolioBaseCurrency,
      BigDecimal amountInAccountCurrency,
      String accountConversionStatus,
      LocalDate date,
      String comment) {
    private boolean isComplete() {
      return amountInPortfolioBaseCurrency != null
          && amountInAccountCurrency != null
          && isUsable(portfolioConversionStatus)
          && isUsable(accountConversionStatus);
    }

    private static boolean isUsable(String status) {
      return CurrencyRateService.isUsableStatus(status);
    }
  }

  private final class HistoricalPriceBook {
    private final Map<String, NavigableMap<LocalDate, HistoricalPrice>> pricesBySymbol;

    private HistoricalPriceBook(
        Map<String, NavigableMap<LocalDate, HistoricalPrice>> pricesBySymbol) {
      this.pricesBySymbol = pricesBySymbol;
    }

    private BigDecimal marketValue(
        String symbol,
        BigDecimal shares,
        LocalDate date,
        LocalDate valuationDate,
        LocalDate holdingStartDate,
        AssetEntity asset,
        CurrencyType portfolioBaseCurrency) {
      if (shares.compareTo(EPSILON) <= 0) {
        return ZERO;
      }
      NavigableMap<LocalDate, HistoricalPrice> prices = pricesBySymbol.get(symbol);
      if (prices != null) {
        HistoricalPrice historicalPrice =
            prices.headMap(date, true).values().stream()
                .filter(price -> price.isAdmissibleOn(date))
                .max(
                    Comparator.comparing(HistoricalPrice::effectiveObservationDate)
                        .thenComparingInt(
                            price ->
                                -valuationPriorityRank(price.qualityClass(), price.priceOrigin()))
                        .thenComparingInt(HistoricalPrice::qualityScore)
                        .thenComparing(HistoricalPrice::valuationPriceDate)
                        .thenComparingInt(price -> -tradeObservationTieRank(price.priceOrigin()))
                        .thenComparing(HistoricalPrice::source)
                        .thenComparing(HistoricalPrice::sourceSymbol))
                .orElse(null);
        if (historicalPrice != null) {
          LocalDate observationDate = historicalPrice.effectiveObservationDate();
          if (observationDate.plusDays(MAX_HISTORICAL_PRICE_STALENESS_DAYS).isBefore(date)) {
            boolean priceObservedDuringHolding =
                holdingStartDate != null && !observationDate.isBefore(holdingStartDate);
            if (priceObservedDuringHolding) {
              log.warn(
                  "Valuation carries forward stale price observed during current holding: symbol={}, valuationDate={}, holdingStartDate={}, selectedPriceDate={}, observationDate={}, qualityClass={}, priceOrigin={}, source={}, sourceSymbol={}",
                  symbol,
                  date,
                  holdingStartDate,
                  historicalPrice.valuationPriceDate(),
                  observationDate,
                  historicalPrice.qualityClass(),
                  historicalPrice.priceOrigin(),
                  historicalPrice.source(),
                  historicalPrice.sourceSymbol());
            } else {
              BigDecimal fallbackValue =
                  currentMarketValueFallback(
                      shares, date, valuationDate, asset, portfolioBaseCurrency);
              if (fallbackValue != null) {
                return fallbackValue;
              }
              log.debug(
                  "Valuation uses zero because stale price predates current holding: symbol={}, valuationDate={}, holdingStartDate={}, selectedPriceDate={}, observationDate={}",
                  symbol,
                  date,
                  holdingStartDate,
                  historicalPrice.valuationPriceDate(),
                  observationDate);
              return ZERO;
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
          BigDecimal localMarketValue =
              shares
                  .multiply(historicalPrice.normalizedClosePrice())
                  .multiply(historicalPrice.contractMultiplier());
          return convert(localMarketValue, portfolioBaseCurrency, historicalPrice.currency(), date);
        }
        log.debug(
            "Valuation uses zero because no historical price row exists: symbol={}, date={}",
            symbol,
            date);
      }
      BigDecimal fallbackValue =
          currentMarketValueFallback(shares, date, valuationDate, asset, portfolioBaseCurrency);
      if (fallbackValue != null) {
        return fallbackValue;
      }
      log.debug(
          "Valuation uses zero because no current or historical price is available: symbol={}, date={}",
          symbol,
          date);
      return ZERO;
    }

    private BigDecimal currentMarketValueFallback(
        BigDecimal shares,
        LocalDate date,
        LocalDate valuationDate,
        AssetEntity asset,
        CurrencyType portfolioBaseCurrency) {
      if (!date.equals(valuationDate)
          || asset == null
          || asset.getMarketPrice() == null
          || asset.getMarketPrice().compareTo(EPSILON) <= 0) {
        return null;
      }
      CurrencyType currency = requiredCurrency(asset.getCurrency(), asset.getId(), "market price");
      return convert(
          shares.multiply(asset.getMarketPrice()), portfolioBaseCurrency, currency, date);
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
    BigDecimal sharesOwned = ZERO;
    BigDecimal totalBuyQty = ZERO;
    BigDecimal totalSellQty = ZERO;
    BigDecimal totalBuyValue = ZERO;
    BigDecimal totalSellValue = ZERO;
    BigDecimal realizedProfit = ZERO;
    BigDecimal unrealizedProfit = ZERO;
    BigDecimal totalDividends = ZERO;
    BigDecimal totalCommission = ZERO;
    BigDecimal totalTax = ZERO;
    LocalDate firstBuyDate;
    LocalDate lastTransactionDate;

    void applyOpen(PositionType type, BigDecimal volume, BigDecimal value, LocalDate date) {
      if (type == PositionType.SELL) {
        sharesOwned = sharesOwned.subtract(volume);
        totalSellQty = totalSellQty.add(volume);
        totalSellValue = totalSellValue.add(value);
      } else {
        sharesOwned = sharesOwned.add(volume);
        totalBuyQty = totalBuyQty.add(volume);
        totalBuyValue = totalBuyValue.add(value);
        firstBuyDate =
            firstBuyDate == null ? date : firstBuyDate.isAfter(date) ? date : firstBuyDate;
      }
      touch(date);
    }

    void applyClose(PositionType openType, BigDecimal volume, BigDecimal value, LocalDate date) {
      if (openType == PositionType.SELL) {
        totalBuyQty = totalBuyQty.add(volume);
        totalBuyValue = totalBuyValue.add(value);
      } else {
        totalSellQty = totalSellQty.add(volume);
        totalSellValue = totalSellValue.add(value);
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
    BigDecimal buyQty = ZERO;
    BigDecimal sellQty = ZERO;
    BigDecimal buyValue = ZERO;
    BigDecimal sellValue = ZERO;
    BigDecimal realizedProfit = ZERO;
    BigDecimal dividends = ZERO;
  }

  private static final class AccountMonthAccumulator {
    BigDecimal deposits = ZERO;
    BigDecimal withdrawals = ZERO;
    BigDecimal performanceFlow = ZERO;
    BigDecimal buys = ZERO;
    BigDecimal sells = ZERO;
    BigDecimal dividends = ZERO;
    BigDecimal interest = ZERO;
    BigDecimal fees = ZERO;
    BigDecimal taxes = ZERO;
    BigDecimal cashAdjustments = ZERO;
    BigDecimal cashDeltaAccountCurrency = ZERO;
    BigDecimal realizedProfit = ZERO;
    BigDecimal endingMarketValue = ZERO;
    BigDecimal costBase = ZERO;
    boolean hasTradeCashMovements;
    boolean hasCashSettlementRows;

    void apply(
        CanonicalNormalizedCashOperation normalized,
        BigDecimal amountBase,
        BigDecimal amountAccountCurrency,
        BigDecimal accountFlowAmountInPortfolioBaseCurrency,
        BigDecimal performanceFlowAmountInPortfolioBaseCurrency,
        boolean cashOnlyAccount) {
      cashDeltaAccountCurrency = cashDeltaAccountCurrency.add(amountAccountCurrency);
      if (accountFlowAmountInPortfolioBaseCurrency != null) {
        if (accountFlowAmountInPortfolioBaseCurrency.signum() >= 0) {
          deposits = deposits.add(accountFlowAmountInPortfolioBaseCurrency);
        } else {
          withdrawals = withdrawals.subtract(accountFlowAmountInPortfolioBaseCurrency);
        }
      }
      if (performanceFlowAmountInPortfolioBaseCurrency != null) {
        performanceFlow = performanceFlow.add(performanceFlowAmountInPortfolioBaseCurrency);
      }
      switch (normalized.normalizedCategory()) {
        case EXTERNAL_DEPOSIT:
          break;
        case EXTERNAL_WITHDRAWAL:
          break;
        case INTERNAL_TRANSFER_IN:
        case INTERNAL_TRANSFER_OUT:
          // Internal transfers are excluded from portfolio net flows because the
          // matching in/out rows cancel across accounts. They are still account-level
          // flows, otherwise the receiving account reports the transfer as fake profit.
          break;
        case INTERNAL_BOOKKEEPING:
        case FX_CONVERSION:
          cashAdjustments = cashAdjustments.add(amountBase);
          break;
        case BOND_REDEMPTION:
          break;
        case DIVIDEND:
        case DIVIDEND_REVERSAL:
          dividends = dividends.add(amountBase);
          break;
        case INTEREST:
        case INTEREST_REVERSAL:
          interest = interest.add(amountBase);
          break;
        case WITHHOLDING_TAX:
        case WITHHOLDING_TAX_REVERSAL:
        case OTHER_TAX:
          // Expense convention in account_daily: expense > 0, reversal/refund < 0.
          taxes = taxes.subtract(amountBase);
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
          fees = fees.subtract(amountBase);
          break;
        case TRADE_PURCHASE:
        case TRADE_SALE:
          hasTradeCashMovements = true;
          hasCashSettlementRows =
              CashOperationType.STOCK_PURCHASE.name().equals(normalized.rawOperation())
                  || CashOperationType.STOCK_SELL.name().equals(normalized.rawOperation());
          cashAdjustments = cashAdjustments.add(amountBase);
          /*
           * A cash-only account intentionally excludes its positions from the reporting
           * boundary. Its trade cash must cross that same boundary: buying an excluded asset is
           * a withdrawal, while selling it is a deposit. Otherwise the cash movement becomes a
           * fake investment loss or gain in account_daily and every monthly aggregate above it.
           */
          if (cashOnlyAccount) {
            performanceFlow = performanceFlow.add(amountBase);
            if (normalized.normalizedCategory() == NormalizedCategory.TRADE_PURCHASE) {
              withdrawals = withdrawals.add(amountBase.abs());
            } else {
              deposits = deposits.add(amountBase.abs());
            }
          }
          break;
        case REALIZED_TRADE_RESULT:
          realizedProfit = realizedProfit.add(amountBase);
          cashAdjustments = cashAdjustments.add(amountBase);
          break;
        case CORRECTION:
        case UNCLASSIFIED:
          cashAdjustments = cashAdjustments.add(amountBase);
          break;
      }
    }
  }
}
