package com.smartbox.investory.investment.accounting;

import com.smartbox.investory.investment.accounting.CashOperationNormalizer.NormalizedCategory;
import com.smartbox.investory.investment.accounting.model.PositionSettlementModel;
import com.smartbox.investory.investment.accounting.model.PositionType;
import com.smartbox.investory.investment.infrastructure.persistence.AssetEntity;
import com.smartbox.investory.investment.infrastructure.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.investment.infrastructure.persistence.AssetRepository;
import com.smartbox.investory.investment.infrastructure.persistence.CashOperationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.ClosedPosition;
import com.smartbox.investory.investment.infrastructure.persistence.ClosedPositionRepository;
import com.smartbox.investory.investment.infrastructure.persistence.NormalizedCashOperationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPosition;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPositionRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.market.fx.CurrencyRateService;
import com.smartbox.investory.investment.market.price.AssetPriceHistoryGapFillService;
import com.smartbox.investory.investment.reporting.ReportingDateHelper;
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

  private static final double EPSILON = 1e-9;
  private static final long MAX_HISTORICAL_PRICE_STALENESS_DAYS = 10;
  private static final String QUALITY_EXACT_LISTING_MARKET_CLOSE = "EXACT_LISTING_MARKET_CLOSE";
  private static final String QUALITY_EXACT_LISTING_SCALED = "EXACT_LISTING_SCALED";
  private static final String QUALITY_STALE_CARRY_FORWARD = "STALE_CARRY_FORWARD";
  private final ReentrantLock rebuildLock = new ReentrantLock(true);

  private final OpenedPositionRepository openedPositionRepository;
  private final ClosedPositionRepository closedPositionRepository;
  private final CashOperationRepository cashOperationRepository;
  private final AssetRepository assetRepository;
  private final AccountRepository accountRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;
  private final AccountDailyRepository accountDailyRepository;
  private final CurrencyRateService currencyRateService;
  private final NormalizedCashOperationRepository normalizedCashOperationRepository;
  private final AssetPriceHistoryGapFillService assetPriceHistoryGapFillService;

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
    // Do not wrap the full reconstruction pipeline in one application transaction. Each native
    // refresh call commits before the next independent stage starts, reducing lock duration and
    // making stage timing visible in PostgreSQL logs.
    refreshReconciliationStage(
        "reconstructed-position-daily", accountDailyRepository::refreshReconstructedPositionDaily);
    refreshReconciliationStage(
        "reconstructed-account-market-daily",
        accountDailyRepository::refreshReconstructedAccountMarketDaily);
    refreshReconciliationStage(
        "reconstructed-cash-daily", accountDailyRepository::refreshReconstructedCashDaily);
    refreshReconciliationStage(
        "account-daily-reconciliation", accountDailyRepository::refreshAccountDailyReconciliation);
    refreshReconciliationStage(
        "reconciliation-reporting", accountDailyRepository::refreshReconciliationReportingViews);
  }

  private void refreshReconciliationStage(String stage, Runnable refresh) {
    long started = System.nanoTime();
    try {
      refresh.run();
    } finally {
      log.info(
          "IMPORT PERF reconciliation-stage={}ms stage={}",
          (System.nanoTime() - started) / 1_000_000,
          stage);
    }
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

    List<AccountEntity> allAccounts = accountRepository.findAll();
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
    List<OpenedPosition> opened =
        openedPositionRepository.findAllByAccountIn(affectedAccounts).stream()
            .filter(position -> !cashOnlyAccounts.contains(position.getAccount()))
            .filter(position -> !excludedAssetIds.contains(position.getAssetId()))
            .toList();
    List<ClosedPosition> closed =
        closedPositionRepository.findAllByAccountIn(affectedAccounts).stream()
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
    openedPositionRepository
        .findAll()
        .forEach(position -> addAccountId(accountIds, position.getAccount()));
    closedPositionRepository
        .findAll()
        .forEach(position -> addAccountId(accountIds, position.getAccount()));
    cashOperationRepository
        .findAll()
        .forEach(operation -> addAccountId(accountIds, operation.getAccount()));
    return accountIds;
  }

  private static void addAccountId(Set<Long> accountIds, Long accountId) {
    if (accountId != null) {
      accountIds.add(accountId);
    }
  }

  private Map<Long, LocalDate> earliestActivityDateByAccount(Set<Long> accountIds) {
    Map<Long, LocalDate> earliest = new HashMap<>();
    openedPositionRepository
        .findAllByAccountIn(accountIds)
        .forEach(
            position ->
                includeEarlier(earliest, position.getAccount(), toDate(position.getOpenTime())));
    closedPositionRepository
        .findAllByAccountIn(accountIds)
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
      List<OpenedPosition> opened,
      Map<PositionKey, PositionAccumulator> positions,
      Map<DayTickerKey, TickerMonthAccumulator> tickerDaily,
      Map<Long, CurrencyType> portfolioBaseCurrencies) {
    for (OpenedPosition position : opened) {
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
      double volume = Math.abs(nz(position.getVolume()));
      if (volume <= EPSILON) {
        continue;
      }

      double openValue = nz(position.getPurchaseValue());
      if (openValue <= EPSILON) {
        openValue = volume * nz(position.getOpenPrice());
      }

      PositionKey key = new PositionKey(position.getAccount(), position.getSymbol(), costCurrency);
      PositionAccumulator acc =
          positions.computeIfAbsent(key, ignored -> new PositionAccumulator());
      acc.applyOpen(position.getType(), volume, openValue, date);
      acc.unrealizedProfit +=
          convertBetween(
                  nz(position.getProfit()) + nz(position.getSwap()),
                  costCurrency,
                  profitCurrency,
                  date)
              + convertBetween(
                  nz(position.getCommission()), costCurrency, commissionCurrency, date);
      acc.totalCommission +=
          convertBetween(nz(position.getCommission()), costCurrency, commissionCurrency, date)
              + convertBetween(nz(position.getSwap()), costCurrency, profitCurrency, date);

      DayTickerKey dayKey = DayTickerKey.of(position.getAccount(), position.getSymbol(), date);
      TickerMonthAccumulator dayAcc =
          tickerDaily.computeIfAbsent(dayKey, ignored -> new TickerMonthAccumulator());
      if (position.getType() == PositionType.SELL) {
        double valueBase =
            convert(
                openValue,
                baseCurrency(position.getAccount(), portfolioBaseCurrencies),
                costCurrency,
                date);
        dayAcc.sellQty += volume;
        dayAcc.sellValue += valueBase;
      } else {
        double valueBase =
            convert(
                openValue,
                baseCurrency(position.getAccount(), portfolioBaseCurrencies),
                costCurrency,
                date);
        dayAcc.buyQty += volume;
        dayAcc.buyValue += valueBase;
      }
    }
  }

  private void processClosed(
      List<ClosedPosition> closed,
      Map<PositionKey, PositionAccumulator> positions,
      Map<DayTickerKey, TickerMonthAccumulator> tickerDaily,
      Map<Long, CurrencyType> portfolioBaseCurrencies) {
    for (ClosedPosition position : closed) {
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
      double netRealized =
          convertBetween(
                  nz(position.getProfit()) + nz(position.getSwap()),
                  costCurrency,
                  profitCurrency,
                  closeDate)
              + convertBetween(
                  nz(position.getCommission()), costCurrency, commissionCurrency, closeDate);

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
      acc.realizedProfit += netRealized;
      acc.totalCommission +=
          convertBetween(nz(position.getCommission()), costCurrency, commissionCurrency, closeDate)
              + convertBetween(nz(position.getSwap()), costCurrency, profitCurrency, closeDate);

      DayTickerKey openDay = DayTickerKey.of(position.getAccount(), position.getSymbol(), openDate);
      DayTickerKey closeDay =
          DayTickerKey.of(position.getAccount(), position.getSymbol(), closeDate);
      TickerMonthAccumulator openAcc =
          tickerDaily.computeIfAbsent(openDay, ignored -> new TickerMonthAccumulator());
      TickerMonthAccumulator closeAcc =
          tickerDaily.computeIfAbsent(closeDay, ignored -> new TickerMonthAccumulator());
      CurrencyType portfolioBase = baseCurrency(position.getAccount(), portfolioBaseCurrencies);
      double openValueBase = convert(openValue, portfolioBase, costCurrency, openDate);
      double closeValueBase = convert(closeValue, portfolioBase, costCurrency, closeDate);
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
      closeAcc.realizedProfit += convert(netRealized, portfolioBase, costCurrency, closeDate);
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
      double amount = normalized.amount();
      if (!normalized.isComplete()) {
        throw new IllegalStateException(
            "Cash operation "
                + normalized.operationId()
                + " has unavailable portfolio/account FX: portfolioStatus="
                + normalized.portfolioConversionStatus()
                + ", accountStatus="
                + normalized.accountConversionStatus());
      }
      double amountBase = normalized.amountInPortfolioBaseCurrency();
      double amountAccountCurrency = normalized.amountInAccountCurrency();

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
        accountAcc.realizedProfit += amountBase;
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
        tickerAcc.dividends += amountBase;
        positionAcc.totalDividends += amount;
      }
      if (!hasPositionValuation
          && normalized.normalizedCategory() == NormalizedCategory.TRADE_PURCHASE) {
        tickerAcc.buyValue += Math.abs(amountBase);
      }
      if (!hasPositionValuation
          && normalized.normalizedCategory() == NormalizedCategory.TRADE_SALE) {
        tickerAcc.sellValue += Math.abs(amountBase);
      }
      if (isTaxCategory(normalized.normalizedCategory())) {
        positionAcc.totalTax -= amount;
      }
      if (isFeeCategory(normalized.normalizedCategory())) {
        positionAcc.totalCommission -= amount;
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
                    row.getAmountInPortfolioBaseCurrency(),
                    row.getPortfolioConversionStatus(),
                    accountFlowAmount(row, parseCategory(row.getNormalizedCategory())),
                    performanceFlowAmount(row, parseCategory(row.getNormalizedCategory())),
                    row.getAmountInAccountCurrency(),
                    row.getAccountConversionStatus(),
                    row.getDate(),
                    row.getComment()))
        .toList();
  }

  private static Double accountFlowAmount(
      NormalizedCashOperationRepository.NormalizedCashOperationRow row,
      NormalizedCategory category) {
    if (row.getAccountFlowAmountInPortfolioBaseCurrency() != null) {
      return row.getAccountFlowAmountInPortfolioBaseCurrency();
    }
    return switch (category) {
      case EXTERNAL_DEPOSIT, EXTERNAL_WITHDRAWAL, INTERNAL_TRANSFER_IN, INTERNAL_TRANSFER_OUT ->
          row.getAmountInPortfolioBaseCurrency();
      default -> null;
    };
  }

  private static Double performanceFlowAmount(
      NormalizedCashOperationRepository.NormalizedCashOperationRow row,
      NormalizedCategory category) {
    if (row.getPerformanceFlowAmountInPortfolioBaseCurrency() != null) {
      return row.getPerformanceFlowAmountInPortfolioBaseCurrency();
    }
    return switch (category) {
      case EXTERNAL_DEPOSIT, EXTERNAL_WITHDRAWAL, INTERNAL_TRANSFER_IN, INTERNAL_TRANSFER_OUT ->
          row.getAmountInPortfolioBaseCurrency();
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
      List<OpenedPosition> opened,
      List<ClosedPosition> closed) {
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
      double shares = 0.0;
      double runningCostBasis = 0.0;
      LocalDate holdingStartDate = null;
      for (LocalDate day : accountDays.getOrDefault(entry.getKey().accountId(), List.of())) {
        TickerMonthAccumulator tickerAcc =
            entry.getValue().getOrDefault(day, new TickerMonthAccumulator());
        if (tickerAcc.buyQty > EPSILON) {
          if (shares <= EPSILON) {
            holdingStartDate = day;
          }
          shares += tickerAcc.buyQty;
          runningCostBasis += tickerAcc.buyValue;
        }
        if (tickerAcc.sellQty > EPSILON) {
          double avg = shares > EPSILON ? runningCostBasis / shares : 0.0;
          double sold = Math.min(shares, tickerAcc.sellQty);
          runningCostBasis = Math.max(0.0, runningCostBasis - sold * avg);
          shares = Math.max(0.0, shares - tickerAcc.sellQty);
          if (shares <= EPSILON) {
            holdingStartDate = null;
          }
        }

        AssetEntity asset = assets.get(entry.getKey().ticker());
        double marketValue =
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
        acc.buys += tickerAcc.buyValue;
        acc.sells += tickerAcc.sellValue;
        acc.realizedProfit += tickerAcc.realizedProfit;
        acc.endingMarketValue += marketValue;
        acc.costBase += runningCostBasis;
      }
    }

    Map<DayAccountKey, Double> canonicalPositionCosts =
        canonicalPositionCostsByDay(opened, closed, accountDays, portfolioBaseCurrencies);
    for (Map.Entry<DayAccountKey, AccountMonthAccumulator> entry : aggregated.entrySet()) {
      Double canonicalCost = canonicalPositionCosts.get(entry.getKey());
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
        double cashBalance =
            convert(cashBalanceAccountCurrency, portfolioBase, accountCurrency, entry.key().date());
        double marketValue = acc.endingMarketValue > EPSILON ? acc.endingMarketValue : 0.0;
        double equity = cashBalance + marketValue;
        double previousEquity = previousCash + previousMarket;
        double costBase = acc.costBase > EPSILON ? acc.costBase : 0.0;
        double unrealizedProfit = marketValue - costBase;
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
        double dailyProfit =
            ModifiedDietzCalculator.profit(previousEquity, equity, acc.performanceFlow);
        double dailyReturn =
            ModifiedDietzCalculator.returnRate(
                previousEquity, equity, List.of(acc.performanceFlow));

        rows.add(
            AccountDailyEntity.builder()
                .accountId(entry.key().accountId())
                .date(entry.key().date())
                .valuationCurrency(portfolioBase.name())
                .cashBalance(BigDecimal.valueOf(cashBalance))
                .marketValue(BigDecimal.valueOf(marketValue))
                .equity(BigDecimal.valueOf(equity))
                .unrealizedProfit(BigDecimal.valueOf(unrealizedProfit))
                .costBase(BigDecimal.valueOf(costBase))
                .realizedProfit(BigDecimal.valueOf(acc.realizedProfit))
                .dividends(BigDecimal.valueOf(acc.dividends))
                .interest(BigDecimal.valueOf(acc.interest))
                .fees(BigDecimal.valueOf(acc.fees))
                .taxes(BigDecimal.valueOf(acc.taxes))
                .deposits(BigDecimal.valueOf(acc.deposits))
                .withdrawals(BigDecimal.valueOf(acc.withdrawals))
                .dailyProfitAmount(BigDecimal.valueOf(dailyProfit))
                .dailyReturn(BigDecimal.valueOf(dailyReturn))
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

  private Map<DayAccountKey, Double> canonicalPositionCostsByDay(
      List<OpenedPosition> opened,
      List<ClosedPosition> closed,
      Map<Long, List<LocalDate>> accountDays,
      Map<Long, CurrencyType> portfolioBaseCurrencies) {
    Map<DayAccountKey, Double> costs = new HashMap<>();
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
      Map<DayAccountKey, Double> costs,
      Map<Long, List<LocalDate>> accountDays,
      Map<Long, CurrencyType> portfolioBaseCurrencies,
      Long accountId,
      CurrencyType costCurrency,
      Double purchaseValue,
      Double volume,
      Double openPrice,
      LocalDate openDate,
      LocalDate closeDate) {
    if (accountId == null || costCurrency == null || openDate == null) {
      return;
    }
    double nativeCost = nz(purchaseValue);
    if (nativeCost <= EPSILON) {
      nativeCost = Math.abs(nz(volume)) * nz(openPrice);
    }
    double baseCost =
        convert(
            nativeCost, baseCurrency(accountId, portfolioBaseCurrencies), costCurrency, openDate);
    for (LocalDate day : accountDays.getOrDefault(accountId, List.of())) {
      if (day.isBefore(openDate) || (closeDate != null && !day.isBefore(closeDate))) {
        continue;
      }
      costs.merge(new DayAccountKey(accountId, day), baseCost, Double::sum);
    }
  }

  private void rebuildDerivedSummaries() {
    accountDailyRepository.refreshReportingViews();
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
    for (Map.Entry<AccountTickerKey, Map<LocalDate, TickerMonthAccumulator>> entry :
        byTicker.entrySet()) {
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
      double scaledClosePrice = scaledClosePriceValue.doubleValue();
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

  private void logResultOnlyOpenPosition(OpenedPosition position) {
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

  private static double resolveContractMultiplier(String qualityClass) {
    String quality = qualityClass == null ? "" : qualityClass.trim().toUpperCase(Locale.ROOT);
    if (quality.contains("PERCENT_OF_PAR")) {
      return 0.01;
    }
    return 1.0;
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

  private double convert(
      double amount, CurrencyType targetCurrency, CurrencyType sourceCurrency, LocalDate date) {
    return currencyRateService.convertToBaseCurrency(amount, targetCurrency, sourceCurrency, date);
  }

  private double convertBetween(
      double amount, CurrencyType targetCurrency, CurrencyType sourceCurrency, LocalDate date) {
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
      double normalizedClosePrice,
      CurrencyType currency,
      String source,
      String sourceSymbol,
      String originalSourceSymbol,
      String priceOrigin,
      String qualityClass,
      int qualityScore,
      double contractMultiplier,
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
      double amount,
      Double amountInPortfolioBaseCurrency,
      String portfolioConversionStatus,
      Double accountFlowAmountInPortfolioBaseCurrency,
      Double performanceFlowAmountInPortfolioBaseCurrency,
      Double amountInAccountCurrency,
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

    private double marketValue(
        String symbol,
        double shares,
        LocalDate date,
        LocalDate valuationDate,
        LocalDate holdingStartDate,
        AssetEntity asset,
        CurrencyType portfolioBaseCurrency) {
      if (shares <= EPSILON) {
        return 0.0;
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
              Double fallbackValue =
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
              shares
                  * historicalPrice.normalizedClosePrice()
                  * historicalPrice.contractMultiplier();
          return convert(localMarketValue, portfolioBaseCurrency, historicalPrice.currency(), date);
        }
        log.debug(
            "Valuation uses zero because no historical price row exists: symbol={}, date={}",
            symbol,
            date);
      }
      Double fallbackValue =
          currentMarketValueFallback(shares, date, valuationDate, asset, portfolioBaseCurrency);
      if (fallbackValue != null) {
        return fallbackValue;
      }
      log.debug(
          "Valuation uses zero because no current or historical price is available: symbol={}, date={}",
          symbol,
          date);
      return 0.0;
    }

    private Double currentMarketValueFallback(
        double shares,
        LocalDate date,
        LocalDate valuationDate,
        AssetEntity asset,
        CurrencyType portfolioBaseCurrency) {
      if (!date.equals(valuationDate)
          || asset == null
          || asset.getMarketPrice() == null
          || asset.getMarketPrice() <= EPSILON) {
        return null;
      }
      CurrencyType currency = requiredCurrency(asset.getCurrency(), asset.getId(), "market price");
      return convert(shares * asset.getMarketPrice(), portfolioBaseCurrency, currency, date);
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
        firstBuyDate =
            firstBuyDate == null ? date : firstBuyDate.isAfter(date) ? date : firstBuyDate;
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
    double performanceFlow;
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
        Double accountFlowAmountInPortfolioBaseCurrency,
        Double performanceFlowAmountInPortfolioBaseCurrency,
        boolean cashOnlyAccount) {
      cashDeltaAccountCurrency += amountAccountCurrency;
      if (accountFlowAmountInPortfolioBaseCurrency != null) {
        if (accountFlowAmountInPortfolioBaseCurrency >= 0.0) {
          deposits += accountFlowAmountInPortfolioBaseCurrency;
        } else {
          withdrawals -= accountFlowAmountInPortfolioBaseCurrency;
        }
      }
      if (performanceFlowAmountInPortfolioBaseCurrency != null) {
        performanceFlow += performanceFlowAmountInPortfolioBaseCurrency;
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
          cashAdjustments += amountBase;
          break;
        case BOND_REDEMPTION:
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
          taxes -= amountBase;
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
          fees -= amountBase;
          break;
        case TRADE_PURCHASE:
        case TRADE_SALE:
          hasTradeCashMovements = true;
          hasCashSettlementRows =
              CashOperationType.STOCK_PURCHASE.name().equals(normalized.rawOperation())
                  || CashOperationType.STOCK_SELL.name().equals(normalized.rawOperation());
          cashAdjustments += amountBase;
          /*
           * A cash-only account intentionally excludes its positions from the reporting
           * boundary. Its trade cash must cross that same boundary: buying an excluded asset is
           * a withdrawal, while selling it is a deposit. Otherwise the cash movement becomes a
           * fake investment loss or gain in account_daily and every monthly aggregate above it.
           */
          if (cashOnlyAccount) {
            performanceFlow += amountBase;
            if (normalized.normalizedCategory() == NormalizedCategory.TRADE_PURCHASE) {
              withdrawals += Math.abs(amountBase);
            } else {
              deposits += Math.abs(amountBase);
            }
          }
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
}
