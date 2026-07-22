package com.example.demo.controllers.rest.ghostfolio;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.infrastructure.repository.account.AccountDaily;
import com.example.demo.infrastructure.repository.account.AccountDailyRepository;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.services.models.AccountBalance;
import com.example.demo.services.models.OpenPositionValue;
import com.example.demo.services.models.Portfolio;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class GhostfolioCompatibilityService {

    private final PortfolioService portfolioService;
    private final AccountDailyRepository accountDailyRepository;
    private final AssetRepository assetRepository;
    private final CashOperationRepository cashOperationRepository;
    private final OpenedPositionRepository openedPositionRepository;
    private final ClosedPositionRepository closedPositionRepository;
    private final CurrencyRateService currencyRateService;

    Map<String, Object> accounts() {
        Portfolio portfolio = portfolio();
        List<Map<String, Object>> accounts = accountRows(portfolio, null);

        return Map.ofEntries(
                Map.entry("accounts", accounts),
                Map.entry("activitiesCount", accountActivitiesCount()),
                Map.entry("totalBalanceInBaseCurrency", sum(accounts, "balanceInBaseCurrency")),
                Map.entry("totalDividendInBaseCurrency", sum(accounts, "dividendInBaseCurrency")),
                Map.entry("totalInterestInBaseCurrency", sum(accounts, "interestInBaseCurrency")),
                Map.entry("totalValueInBaseCurrency", sum(accounts, "valueInBaseCurrency")));
    }

    Optional<Map<String, Object>> account(String accountId) {
        return accountRows(portfolio(), accountId).stream().findFirst();
    }

    Map<String, Object> accountBalances(String accountId) {
        Long id = parseLong(accountId);
        List<Map<String, Object>> balances = id == null
                ? List.of()
                : accountDailyRepository.findAllByAccountIdOrderByDateAsc(id).stream()
                        .map(this::toAccountBalance)
                        .toList();

        return Map.of("balances", balances);
    }

    Map<String, Object> holdings(String accountIds, String requestedSymbol) {
        List<Map<String, Object>> holdings = holdingRows(portfolio(), accountIds, requestedSymbol);
        return Map.of("holdings", holdings);
    }

    Map<String, Object> details(String accountIds, String requestedSymbol) {
        Portfolio portfolio = portfolio();
        List<Map<String, Object>> accountRows = accountRows(portfolio, accountIds);
        List<Map<String, Object>> holdingRows = holdingRows(portfolio, accountIds, requestedSymbol);

        Map<String, Map<String, Object>> accounts = keyedBy(accountRows, "id");
        Map<String, Map<String, Object>> holdings = keyedBy(holdingRows, "symbol");
        Map<String, Map<String, Object>> platforms = mapPlatforms(accounts);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accounts", accounts);
        response.put("createdAt", firstActivityInstant().map(Instant::toString).orElse(null));
        response.put("holdings", holdings);
        response.put("platforms", platforms);
        response.put("summary", portfolioSummary(portfolio, accountRows));
        response.put("hasError", false);
        return response;
    }

    Map<String, Object> performance(String accounts, String range) {
        Portfolio portfolio = portfolio();
        List<Map<String, Object>> chart = performanceChart(accounts, range);
        Optional<Instant> firstActivity = firstActivityInstant();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dateOfFirstActivity", firstActivity.map(Instant::toString).orElse(null));
        response.put("firstOrderDate", firstActivity.map(Instant::toString).orElse(null));
        response.put("performance", performanceSummary(portfolio));
        response.put("chart", chart);
        response.put("hasError", false);
        return response;
    }

    Map<String, Object> activities(
            String accounts,
            String activityTypes,
            String range,
            String symbol,
            String sortColumn,
            String sortDirection,
            int take,
            int skip) {
        Portfolio portfolio = portfolio();
        Set<String> allowedTypes = split(activityTypes);
        List<Map<String, Object>> rows = activityRows(portfolio).stream()
                .filter(row -> accountMatches(accounts, text(row.get("accountId"))))
                .filter(row -> allowedTypes.isEmpty() || allowedTypes.contains(text(row.get("type"))))
                .filter(row -> symbol == null || symbol.isBlank() || symbol.equalsIgnoreCase(text(row.get("symbol"))))
                .filter(row -> dateRangeMatches(range, text(row.get("date"))))
                .sorted(activityComparator(sortColumn, sortDirection))
                .toList();

        int normalizedSkip = Math.max(0, skip);
        int normalizedTake = take <= 0 ? rows.size() : take;
        int from = Math.min(normalizedSkip, rows.size());
        int to = Math.min(from + normalizedTake, rows.size());

        return Map.of("activities", rows.subList(from, to), "count", rows.size());
    }

    private Portfolio portfolio() {
        return portfolioService.calculateTotalProfitLoss();
    }

    private List<Map<String, Object>> accountRows(Portfolio portfolio, String requestedAccountIds) {
        List<AccountBalance> source = portfolio.getAccountBalances() == null
                ? List.of()
                : portfolio.getAccountBalances();
        Map<Long, AccountDaily> latestDaily = latestDailyByAccount();
        Map<Long, Long> activities = activitiesCountByAccount();
        double totalValue = portfolio.getBalance();

        return source.stream()
                .filter(account -> accountMatches(requestedAccountIds, String.valueOf(account.getAccountId())))
                .map(account -> {
                    AccountDaily daily = latestDaily.get(account.getAccountId());
                    double dividends = daily == null ? 0.0d : nz(daily.getDividends());
                    double interest = daily == null ? 0.0d : nz(daily.getInterest());
                    double value = account.getBalance();
                    double cash = account.getCash();

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", String.valueOf(account.getAccountId()));
                    row.put("name", account.getAccountName());
                    row.put("comment", "");
                    row.put("currency", String.valueOf(account.getLocalCurrency()));
                    row.put("balance", cash);
                    row.put("balanceInBaseCurrency", cash);
                    row.put("cash", cash);
                    row.put("value", value);
                    row.put("valueInBaseCurrency", value);
                    row.put("dividendInBaseCurrency", dividends);
                    row.put("interestInBaseCurrency", interest);
                    row.put("activitiesCount", activities.getOrDefault(account.getAccountId(), 0L));
                    row.put("allocationInPercentage", percentage(value, totalValue));
                    row.put("platformId", null);
                    row.put("platform", null);
                    row.put("isExcluded", false);
                    row.put("isDefault", false);
                    row.put("createdAt", firstAccountDate(account.getAccountId()).map(Instant::toString).orElse(null));
                    row.put("updatedAt", daily == null ? null : daily.getUpdatedAt().toInstant().toString());
                    return row;
                })
                .toList();
    }

    private Map<String, Object> toAccountBalance(AccountDaily daily) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("accountId", String.valueOf(daily.getAccountId()));
        row.put("date", daily.getDate().toString());
        row.put("id", String.valueOf(daily.getId()));
        row.put("value", nz(daily.getEquity()));
        row.put("valueInBaseCurrency", nz(daily.getEquity()));
        return row;
    }

    private List<Map<String, Object>> holdingRows(
            Portfolio portfolio, String accountIds, String requestedSymbol) {
        List<OpenPositionValue> source = portfolio.getOpenPositionValues() == null
                ? List.of()
                : portfolio.getOpenPositionValues();
        Map<String, Asset> assets = assetsBySymbol(source.stream().map(OpenPositionValue::getSymbol).toList());
        Map<String, List<OpenedPosition>> openBySymbol = openedPositionRepository.findAll().stream()
                .collect(Collectors.groupingBy(OpenedPosition::getSymbol));
        double totalValue = source.stream().mapToDouble(OpenPositionValue::getValue).sum();

        return source.stream()
                .filter(position -> requestedSymbol == null
                        || requestedSymbol.isBlank()
                        || requestedSymbol.equalsIgnoreCase(position.getSymbol()))
                .filter(position -> holdingAccountMatches(accountIds, openBySymbol.get(position.getSymbol())))
                .map(position -> toHolding(position, assets.get(position.getSymbol()), openBySymbol.get(position.getSymbol()), totalValue))
                .toList();
    }

    private Map<String, Object> toHolding(
            OpenPositionValue position,
            Asset asset,
            List<OpenedPosition> openPositions,
            double totalValue) {
        List<String> accountIds = openPositions == null
                ? List.of()
                : openPositions.stream()
                        .map(OpenedPosition::getAccount)
                        .filter(java.util.Objects::nonNull)
                        .map(String::valueOf)
                        .distinct()
                        .toList();
        double value = position.getValue();
        double cost = position.getCostBase();
        double performance = position.getUnrealized();
        double performancePercent = percentage(performance, cost);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("accountId", accountIds.size() == 1 ? accountIds.getFirst() : null);
        row.put("accountIds", accountIds);
        row.put("symbol", position.getSymbol());
        row.put("activitiesCount", activityCountForSymbol(position.getSymbol()));
        row.put("allocationInPercentage", percentage(value, totalValue));
        row.put("assetProfile", assetProfile(position.getSymbol(), position.getCurrency(), asset));
        row.put("dateOfFirstActivity", firstSymbolActivity(position.getSymbol()).map(Instant::toString).orElse(null));
        row.put("dividend", dividendsForSymbol(position.getSymbol()));
        row.put("grossPerformance", performance);
        row.put("grossPerformancePercent", performancePercent);
        row.put("grossPerformancePercentWithCurrencyEffect", performancePercent);
        row.put("grossPerformanceWithCurrencyEffect", performance);
        row.put("investment", cost);
        row.put("marketPrice", position.getAverageOpenPrice() + (position.getVolume() == 0 ? 0 : performance / position.getVolume()));
        row.put("netPerformance", performance);
        row.put("netPerformancePercent", performancePercent);
        row.put("netPerformancePercentWithCurrencyEffect", performancePercent);
        row.put("netPerformanceWithCurrencyEffect", performance);
        row.put("quantity", position.getVolume());
        row.put("tags", List.of());
        row.put("type", "OPEN");
        row.put("valueInBaseCurrency", value);
        row.put("valueInPercentage", percentage(value, totalValue));
        return row;
    }

    private Map<String, Object> assetProfile(String symbol, CurrencyType currency, Asset asset) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("assetClass", asset == null || asset.getAssetType() == null ? "EQUITY" : asset.getAssetType());
        profile.put("assetSubClass", asset == null || asset.getAssetType() == null ? "STOCK" : asset.getAssetType());
        profile.put("countries", asset == null || asset.getCountry() == null ? List.of() : List.of(asset.getCountry()));
        profile.put("currency", String.valueOf(asset == null ? currency : asset.getCurrency()));
        profile.put("dataSource", "YAHOO");
        profile.put("holdings", List.of());
        profile.put("name", asset == null ? symbol : asset.getName());
        profile.put("sectors", List.of());
        profile.put("symbol", asset == null || asset.getYahoo() == null || asset.getYahoo().isBlank() ? symbol : asset.getYahoo());
        profile.put("url", null);
        return profile;
    }

    private List<Map<String, Object>> performanceChart(String accounts, String range) {
        Collection<Long> requested = parseAccountIds(accounts);
        Map<LocalDate, List<AccountDaily>> byDate = accountDailyRepository.findAllByOrderByDateAscAccountIdAsc().stream()
                .filter(row -> requested.isEmpty() || requested.contains(row.getAccountId()))
                .filter(row -> dateRangeMatches(range, row.getDate().toString()))
                .collect(Collectors.groupingBy(AccountDaily::getDate, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> chart = new ArrayList<>();
        for (Map.Entry<LocalDate, List<AccountDaily>> entry : byDate.entrySet()) {
            double value = entry.getValue().stream().mapToDouble(row -> nz(row.getEquity())).sum();
            double investment = entry.getValue().stream().mapToDouble(row -> nz(row.getCostBase())).sum();
            double performance = entry.getValue().stream()
                    .mapToDouble(row -> nz(row.getRealizedProfit()) + nz(row.getUnrealizedProfit())
                            + nz(row.getDividends()) + nz(row.getInterest()) - nz(row.getFees()) - nz(row.getTaxes()))
                    .sum();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", entry.getKey().toString());
            point.put("value", value);
            point.put("netWorth", value);
            point.put("totalCashInBaseCurrency", entry.getValue().stream().mapToDouble(row -> nz(row.getCashBalance())).sum());
            point.put("totalInvestment", investment);
            point.put("totalInvestmentValueWithCurrencyEffect", investment);
            point.put("netPerformance", performance);
            point.put("netPerformanceWithCurrencyEffect", performance);
            point.put("netPerformanceInPercentage", percentage(performance, investment));
            point.put("netPerformanceInPercentageWithCurrencyEffect", percentage(performance, investment));
            chart.add(point);
        }
        return chart;
    }

    private Map<String, Object> performanceSummary(Portfolio portfolio) {
        double netPerformance = portfolio.getTotalProfit() - portfolio.getCapitalGainsTax();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("annualizedPerformancePercent", portfolio.getRoi());
        summary.put("currentNetWorth", portfolio.getBalance());
        summary.put("currentValueInBaseCurrency", portfolio.getBalance());
        summary.put("grossPerformance", portfolio.getTotalProfit());
        summary.put("grossPerformanceWithCurrencyEffect", portfolio.getTotalProfit());
        summary.put("netPerformance", netPerformance);
        summary.put("netPerformancePercentage", percentage(netPerformance, portfolio.getNetDeposits()));
        summary.put("netPerformancePercentageWithCurrencyEffect", percentage(netPerformance, portfolio.getNetDeposits()));
        summary.put("netPerformanceWithCurrencyEffect", netPerformance);
        summary.put("totalInvestment", portfolio.getNetDeposits());
        summary.put("totalInvestmentValueWithCurrencyEffect", portfolio.getNetDeposits());
        return summary;
    }

    private Map<String, Object> portfolioSummary(Portfolio portfolio, List<Map<String, Object>> accounts) {
        Map<String, Object> summary = new LinkedHashMap<>(performanceSummary(portfolio));
        summary.put("activityCount", accountActivitiesCount());
        summary.put("annualizedPerformancePercentWithCurrencyEffect", portfolio.getRoi());
        summary.put("cash", portfolio.getCash());
        summary.put("dateOfFirstActivity", firstActivityInstant().map(Instant::toString).orElse(null));
        summary.put("dividendInBaseCurrency", portfolio.getDividends());
        summary.put("emergencyFund", Map.of("assets", 0.0d, "cash", portfolio.getCash(), "total", portfolio.getCash()));
        summary.put("excludedAccountsAndActivities", 0);
        summary.put("fees", latestDailySum(AccountDaily::getFees));
        summary.put("filteredValueInBaseCurrency", sum(accounts, "valueInBaseCurrency"));
        summary.put("filteredValueInPercentage", percentage(sum(accounts, "valueInBaseCurrency"), portfolio.getBalance()));
        summary.put("interestInBaseCurrency", portfolio.getInterest());
        summary.put("liabilitiesInBaseCurrency", 0.0d);
        summary.put("totalBuy", totalCashOperation(CashOperationType.STOCK_PURCHASE));
        summary.put("totalSell", totalCashOperation(CashOperationType.STOCK_SELL) + totalCashOperation(CashOperationType.CLOSE_TRADE));
        summary.put("totalValueInBaseCurrency", portfolio.getBalance());
        return summary;
    }

    private List<Map<String, Object>> activityRows(Portfolio portfolio) {
        List<Map<String, Object>> rows = new ArrayList<>();
        cashOperationRepository.findAllByOrderByDateDescIdDesc().stream()
                .filter(row -> row.getDate() != null)
                .forEach(row -> rows.add(toActivity(row, portfolio)));
        openedPositionRepository.findAll().stream()
                .filter(row -> row.getOpenTime() != null)
                .forEach(row -> rows.add(toActivity(row, portfolio)));
        closedPositionRepository.findAll().stream()
                .filter(row -> row.getCloseTime() != null)
                .forEach(row -> rows.add(toActivity(row, portfolio)));
        return rows;
    }

    private Map<String, Object> toActivity(CashOperation row, Portfolio portfolio) {
        Instant date = toInstant(row.getDate());
        String symbol = row.getSymbol() == null || row.getSymbol().isBlank() ? "CASH" : row.getSymbol();
        double amount = nz(row.getAmount());
        double valueInBase = convert(Math.abs(amount), portfolio.getBaseCurrency(), row.getCurrency(), date);
        Map<String, Object> activity = baseActivity(
                String.valueOf(row.getId()),
                row.getAccount(),
                symbol,
                row.getCurrency(),
                row.getType() == null ? "UNKNOWN" : cashActivityType(row.getType()),
                date,
                portfolio);
        activity.put("quantity", 0.0d);
        activity.put("unitPrice", 0.0d);
        activity.put("fee", isFee(row.getType()) ? Math.abs(amount) : 0.0d);
        activity.put("feeInBaseCurrency", isFee(row.getType()) ? valueInBase : 0.0d);
        activity.put("value", Math.abs(amount));
        activity.put("valueInBaseCurrency", valueInBase);
        return activity;
    }

    private Map<String, Object> toActivity(OpenedPosition row, Portfolio portfolio) {
        Instant date = toInstant(row.getOpenTime());
        double value = Math.abs(nz(row.getPurchaseValue()));
        Map<String, Object> activity = baseActivity(
                "open-" + row.getId(),
                row.getAccount(),
                row.getSymbol(),
                row.getCurrency(),
                row.getType() == PositionType.SELL ? "SELL" : "BUY",
                date,
                portfolio);
        activity.put("quantity", Math.abs(nz(row.getVolume())));
        activity.put("unitPrice", nz(row.getOpenPrice()));
        activity.put("fee", Math.abs(nz(row.getCommission())));
        activity.put("feeInBaseCurrency", convert(Math.abs(nz(row.getCommission())), portfolio.getBaseCurrency(), row.getCurrency(), date));
        activity.put("value", value);
        activity.put("valueInBaseCurrency", convert(value, portfolio.getBaseCurrency(), row.getCurrency(), date));
        return activity;
    }

    private Map<String, Object> toActivity(ClosedPosition row, Portfolio portfolio) {
        Instant date = toInstant(row.getCloseTime());
        double value = Math.abs(nz(row.getSaleValue()));
        Map<String, Object> activity = baseActivity(
                "closed-" + row.getId(),
                row.getAccount(),
                row.getSymbol(),
                row.getCurrency(),
                "SELL",
                date,
                portfolio);
        activity.put("quantity", Math.abs(nz(row.getVolume())));
        activity.put("unitPrice", nz(row.getClosePrice()));
        activity.put("fee", Math.abs(nz(row.getCommission())));
        activity.put("feeInBaseCurrency", convert(Math.abs(nz(row.getCommission())), portfolio.getBaseCurrency(), row.getCurrency(), date));
        activity.put("value", value);
        activity.put("valueInBaseCurrency", convert(value, portfolio.getBaseCurrency(), row.getCurrency(), date));
        return activity;
    }

    private Map<String, Object> baseActivity(
            String id, Long accountId, String symbol, CurrencyType currency, String type, Instant date, Portfolio portfolio) {
        Asset asset = assetRepository.findBySymbol(symbol).orElse(null);
        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("id", id);
        activity.put("accountId", accountId == null ? null : String.valueOf(accountId));
        activity.put("symbol", symbol);
        activity.put("type", type);
        activity.put("date", date.toString());
        activity.put("createdAt", date.toString());
        activity.put("updatedAt", date.toString());
        activity.put("currency", String.valueOf(currency == null ? portfolio.getBaseCurrency() : currency));
        activity.put("assetProfile", assetProfile(symbol, currency == null ? portfolio.getBaseCurrency() : currency, asset));
        activity.put("symbolProfile", activity.get("assetProfile"));
        activity.put("unitPriceInAssetProfileCurrency", 0.0d);
        activity.put("feeInAssetProfileCurrency", 0.0d);
        return activity;
    }

    private Map<Long, AccountDaily> latestDailyByAccount() {
        Map<Long, AccountDaily> latest = new HashMap<>();
        for (AccountDaily row : accountDailyRepository.findAllByOrderByDateAscAccountIdAsc()) {
            latest.put(row.getAccountId(), row);
        }
        return latest;
    }

    private Map<Long, Long> activitiesCountByAccount() {
        Map<Long, Long> counts = new HashMap<>();
        cashOperationRepository.findAll().forEach(row -> increment(counts, row.getAccount()));
        openedPositionRepository.findAll().forEach(row -> increment(counts, row.getAccount()));
        closedPositionRepository.findAll().forEach(row -> increment(counts, row.getAccount()));
        return counts;
    }

    private long accountActivitiesCount() {
        return activitiesCountByAccount().values().stream().mapToLong(Long::longValue).sum();
    }

    private Optional<Instant> firstAccountDate(Long accountId) {
        return accountDailyRepository.findAllByAccountIdOrderByDateAsc(accountId).stream()
                .findFirst()
                .map(row -> toInstant(row.getDate()));
    }

    private Optional<Instant> firstActivityInstant() {
        return activityRows(portfolio()).stream()
                .map(row -> parseInstant(text(row.get("date"))))
                .flatMap(Optional::stream)
                .min(Comparator.naturalOrder());
    }

    private Optional<Instant> firstSymbolActivity(String symbol) {
        return activityRows(portfolio()).stream()
                .filter(row -> symbol.equals(row.get("symbol")))
                .map(row -> parseInstant(text(row.get("date"))))
                .flatMap(Optional::stream)
                .min(Comparator.naturalOrder());
    }

    private long activityCountForSymbol(String symbol) {
        return activityRows(portfolio()).stream().filter(row -> symbol.equals(row.get("symbol"))).count();
    }

    private double dividendsForSymbol(String symbol) {
        return cashOperationRepository.findAll().stream()
                .filter(row -> row.getType() == CashOperationType.DIVIDEND)
                .filter(row -> symbol.equals(row.getSymbol()))
                .mapToDouble(row -> Math.abs(nz(row.getAmount())))
                .sum();
    }

    private Map<String, Asset> assetsBySymbol(Collection<String> symbols) {
        return assetRepository.findAllBySymbolIn(symbols).stream()
                .collect(Collectors.toMap(Asset::getSymbol, asset -> asset, (left, ignored) -> left));
    }

    private boolean holdingAccountMatches(String accountIds, List<OpenedPosition> positions) {
        if (accountIds == null || accountIds.isBlank()) {
            return true;
        }
        if (positions == null || positions.isEmpty()) {
            return false;
        }
        return positions.stream().anyMatch(position -> accountMatches(accountIds, String.valueOf(position.getAccount())));
    }

    private Map<String, Map<String, Object>> keyedBy(List<Map<String, Object>> rows, String key) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(text(row.get(key)), row);
        }
        return result;
    }

    private Map<String, Map<String, Object>> mapPlatforms(Map<String, Map<String, Object>> accounts) {
        Map<String, Map<String, Object>> platforms = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : accounts.entrySet()) {
            Map<String, Object> account = entry.getValue();
            Map<String, Object> platform = new LinkedHashMap<>();
            platform.put("balance", account.get("balance"));
            platform.put("currency", account.get("currency"));
            platform.put("name", account.get("name"));
            platform.put("valueInBaseCurrency", account.get("valueInBaseCurrency"));
            platform.put("valueInPercentage", account.get("allocationInPercentage"));
            platforms.put(entry.getKey(), platform);
        }
        return platforms;
    }

    private double latestDailySum(java.util.function.Function<AccountDaily, Double> getter) {
        return latestDailyByAccount().values().stream().mapToDouble(row -> nz(getter.apply(row))).sum();
    }

    private double totalCashOperation(CashOperationType type) {
        return cashOperationRepository.findAll().stream()
                .filter(row -> row.getType() == type)
                .mapToDouble(row -> Math.abs(nz(row.getAmount())))
                .sum();
    }

    private Comparator<Map<String, Object>> activityComparator(String sortColumn, String sortDirection) {
        Comparator<Map<String, Object>> comparator = switch (sortColumn == null ? "date" : sortColumn) {
            case "symbol" -> Comparator.comparing(row -> text(row.get("symbol")));
            case "type" -> Comparator.comparing(row -> text(row.get("type")));
            case "value" -> Comparator.comparingDouble(row -> number(row.get("valueInBaseCurrency")));
            default -> Comparator.comparing(row -> text(row.get("date")));
        };
        return "asc".equalsIgnoreCase(sortDirection) ? comparator : comparator.reversed();
    }

    private boolean dateRangeMatches(String range, String dateText) {
        if (range == null || range.isBlank() || "max".equalsIgnoreCase(range)) {
            return true;
        }
        Optional<Instant> maybeDate = parseInstant(dateText);
        if (maybeDate.isEmpty()) {
            return false;
        }
        LocalDate date = maybeDate.get().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate today = LocalDate.now();
        LocalDate start = switch (range.toLowerCase(Locale.ROOT)) {
            case "1d" -> today.minusDays(1);
            case "1m" -> today.minusMonths(1);
            case "3m" -> today.minusMonths(3);
            case "6m" -> today.minusMonths(6);
            case "1y" -> today.minusYears(1);
            case "5y" -> today.minusYears(5);
            case "ytd" -> today.withDayOfYear(1);
            default -> LocalDate.MIN;
        };
        return !date.isBefore(start);
    }

    private String cashActivityType(CashOperationType type) {
        return switch (type) {
            case STOCK_PURCHASE -> "BUY";
            case STOCK_SELL, CLOSE_TRADE -> "SELL";
            case DIVIDEND -> "DIVIDEND";
            case FREE_FUNDS_INTEREST -> "INTEREST";
            case DEPOSIT -> "DEPOSIT";
            case WITHDRAWAL -> "WITHDRAWAL";
            case TRANSFER, SUBACCOUNT_TRANSFER -> "TRANSFER";
            case SEC_FEE, COMMISSION, FREE_FUNDS_INTEREST_TAX, WITHHOLDING_TAX, SWAP, STAMP_DUTY, TRANSACTION_TAX -> "FEE";
            default -> "ITEM";
        };
    }

    private boolean isFee(CashOperationType type) {
        return type == CashOperationType.SEC_FEE
                || type == CashOperationType.COMMISSION
                || type == CashOperationType.FREE_FUNDS_INTEREST_TAX
                || type == CashOperationType.WITHHOLDING_TAX
                || type == CashOperationType.SWAP
                || type == CashOperationType.STAMP_DUTY
                || type == CashOperationType.TRANSACTION_TAX;
    }

    private double convert(double amount, CurrencyType base, CurrencyType currency, Instant date) {
        return currencyRateService.convertToBaseCurrency(
                amount,
                base,
                currency == null ? base : currency,
                date.atZone(ZoneOffset.UTC).toLocalDate());
    }

    private static void increment(Map<Long, Long> counts, Long accountId) {
        if (accountId != null) {
            counts.merge(accountId, 1L, Long::sum);
        }
    }

    private static Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (String value : csv.split(",")) {
            if (!value.isBlank()) {
                result.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static Collection<Long> parseAccountIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String value : csv.split(",")) {
            Long parsed = parseLong(value.trim());
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    private static Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean accountMatches(String requested, String actual) {
        if (requested == null || requested.isBlank()) {
            return true;
        }
        if (actual == null || actual.isBlank() || "null".equals(actual)) {
            return false;
        }
        for (String accountId : requested.split(",")) {
            if (actual.equals(accountId.trim())) {
                return true;
            }
        }
        return false;
    }

    private static double sum(List<Map<String, Object>> rows, String key) {
        return rows.stream().mapToDouble(row -> number(row.get(key))).sum();
    }

    private static double percentage(double value, double denominator) {
        return Math.abs(denominator) < 0.0000001d ? 0.0d : value / Math.abs(denominator);
    }

    private static double nz(Double value) {
        return value == null ? 0.0d : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private static Instant toInstant(ZonedDateTime date) {
        return date == null ? null : date.toInstant();
    }

    private static Instant toInstant(LocalDate date) {
        return date == null ? null : date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Optional<Instant> parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(Instant.parse(value));
        } catch (RuntimeException e) {
            try {
                return Optional.of(LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC));
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
    }
}
