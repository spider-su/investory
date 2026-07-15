package com.example.demo.services.imports.yahoo;

import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.infrastructure.repository.account.AccountStatistics;
import com.example.demo.infrastructure.repository.account.AccountStatisticsRepository;
import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class YahooExportService {

  private final OpenedPositionRepository openedPositionRepository;
  private final AccountStatisticsRepository accountStatisticsRepository;

  /** Accounts to include in Yahoo export. Empty = all accounts. */
  private final Set<String> exportAccounts;

  public YahooExportService(
      OpenedPositionRepository openedPositionRepository,
      AccountStatisticsRepository accountStatisticsRepository,
      @Value("${app.export.yahoo.accounts:}") String accountsCsv) {
    this.openedPositionRepository = openedPositionRepository;
    this.accountStatisticsRepository = accountStatisticsRepository;
    this.exportAccounts =
        accountsCsv == null || accountsCsv.isBlank()
            ? Set.of()
            : Arrays.stream(accountsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
  }

  private static final String[] HEADER = {
    "Symbol",
    "Current Price",
    "Date",
    "Time",
    "Change",
    "Open",
    "High",
    "Low",
    "Volume",
    "Trade Date",
    "Purchase Price",
    "Quantity",
    "Commission",
    "High Limit",
    "Low Limit",
    "Comment",
    "Transaction Type"
  };

  private static final int IDX_SYMBOL = 0;
  private static final int IDX_CURRENT_PRICE = 1;
  private static final int IDX_DATE = 2;
  private static final int IDX_TIME = 3;
  private static final int IDX_TRADE_DATE = 9;
  private static final int IDX_PURCHASE_PRICE = 10;
  private static final int IDX_QUANTITY = 11;
  private static final int IDX_TRANSACTION_TYPE = 16;
  private static final String USDT_TICKER = "USDT-USD";
  private static final DateTimeFormatter CSV_DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH);
  private static final DateTimeFormatter CSV_TRADE_DATE_FMT =
      DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH);
  private static final DateTimeFormatter CSV_TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Warsaw");
  private static final double EPSILON = 1.0e-6;

  /**
   * Exchange suffix hints for IBKR bare tickers that have NO XTB counterpart to derive from.
   */
  private static final Map<String, String> IBKR_EXCHANGE_HINTS = Map.ofEntries(
      Map.entry("VWRA", ".L"),
      Map.entry("CSPX", ".L"),
      Map.entry("IUVL", ".L"),
      Map.entry("JGPI", ".MU"),
      Map.entry("JEPG", ".L"),
      Map.entry("DTLA", ".L"),
      Map.entry("BRKB", "")
  );

  /**
   * Accounts to include in the Yahoo export. Set to empty to include ALL accounts.
   */
  // Configured via app.export.yahoo.accounts in application.yml

  public void exportToYahooCsv(String filePath) throws IOException {
    CsvExportPayload payload = buildPayloadFromSummary();
    writeCsv(filePath, payload.rows());
  }

  /**
   * Kept for endpoint compatibility. The input file is ignored now because export is generated
   * entirely from DB snapshots.
   */
  public ReconcileResult extendPortfolioCsvWithAverageOperations(
      String inputFilePath, String outputFilePath) throws IOException {
    CsvExportPayload payload = buildPayloadFromSummary();
    writeCsv(outputFilePath, payload.rows());
    return new ReconcileResult(
        payload.rows().size(), payload.lastSnapshotAt(), inputFilePath, outputFilePath);
  }

  /**
   * Builds the CSV export payload for Yahoo Finance portfolio tracking.
   *
   * <p>Strategy:
   * <ol>
   *   <li>Open positions grouped by Yahoo symbol → one BUY row per symbol with
   *       volume-weighted average price and total quantity.
   *   <li>Free cash balance from account summary fields → single USDT-USD BUY row.
   * </ol>
   */
  private CsvExportPayload buildPayloadFromSummary() {
    List<OpenedPosition> openedPositions =
        openedPositionRepository.findAll().stream()
            .filter(
                p ->
                    exportAccounts.isEmpty()
                        || (p.getAccount() != null
                            && exportAccounts.contains(String.valueOf(p.getAccount()))))
            .toList();

    log.info(
        "Yahoo export: exportAccounts={}, openedPositions={}",
        exportAccounts.isEmpty() ? "<ALL>" : exportAccounts,
        openedPositions.size());

    Map<String, String> symbolResolution = buildSymbolResolutionMap(openedPositions);

    // Group open positions by Yahoo symbol and compute weighted average price.
    // openPrice / marketPrice are always in the instrument's native trading currency
    // (determined by exchange, not account), so no currency conversion is needed.
    Map<String, SymbolAccumulator> bySymbol = new HashMap<>();
    for (OpenedPosition pos : openedPositions) {
      String yahooSymbol = resolveSymbol(pos.getSymbol(), symbolResolution);
      if (yahooSymbol.isBlank()) {
        continue;
      }
      double qty = Math.abs(safe(pos.getVolume()));
      if (qty < EPSILON) {
        continue;
      }
      double openPrice = safe(pos.getOpenPrice());
      Double marketPrice = pos.getMarketPrice();

      bySymbol
          .computeIfAbsent(yahooSymbol, _ -> new SymbolAccumulator())
          .add(qty, openPrice, marketPrice);
    }

    List<String[]> rows = new ArrayList<>();

    // Emit one BUY row per symbol with weighted average price
    for (Map.Entry<String, SymbolAccumulator> entry : bySymbol.entrySet()) {
      SymbolAccumulator acc = entry.getValue();
      rows.add(
          buildRow(
              entry.getKey(),
              toString(acc.latestMarketPrice),
              currentMonthStart(),
              acc.weightedAveragePrice(),
              acc.totalQty,
              "BUY"));
    }

    // Cash balance from account summary fields -> USDT-USD
    double totalCashUsd = computeTotalCashBalanceUsd();
    if (totalCashUsd > EPSILON) {
      rows.add(
          buildRow(USDT_TICKER, "1.0", currentMonthStart(), 1.0, totalCashUsd, "BUY"));
    }

    rows.sort(Comparator.comparing(row -> row[IDX_SYMBOL]));
    ZonedDateTime lastSnapshotAt = ZonedDateTime.now(DEFAULT_ZONE);
    return new CsvExportPayload(rows, lastSnapshotAt);
  }

  private double computeTotalCashBalanceUsd() {
    List<AccountStatistics> statistics = accountStatisticsRepository.findAll().stream()
        .filter(stat -> stat.getCashBalance() != null)
        .filter(stat -> exportAccounts.isEmpty()
            || (stat.getAccountId() != null
                && exportAccounts.contains(String.valueOf(stat.getAccountId()))))
        .toList();
    if (!statistics.isEmpty()) {
      return statistics.stream()
          .mapToDouble(stat -> safe(stat.getCashBalance()))
          .sum();
    }
    return 0.0;
  }

  private static final class SymbolAccumulator {
    double totalQty;
    double totalNotional;
    Double latestMarketPrice;

    void add(double qty, double openPrice, Double marketPrice) {
      totalQty += qty;
      totalNotional += qty * openPrice;
      if (marketPrice != null) {
        latestMarketPrice = marketPrice;
      }
    }

    double weightedAveragePrice() {
      return totalQty > 0 ? totalNotional / totalQty : 0.0;
    }
  }

  private static LocalDate currentMonthStart() {
    return LocalDate.now(DEFAULT_ZONE).withDayOfMonth(1);
  }

  private void writeCsv(String filePath, List<String[]> rows) throws IOException {
    try (CSVWriter writer =
        new CSVWriter(
            new FileWriter(filePath),
            CSVWriter.DEFAULT_SEPARATOR,
            CSVWriter.NO_QUOTE_CHARACTER,
            CSVWriter.DEFAULT_ESCAPE_CHARACTER,
            CSVWriter.DEFAULT_LINE_END)) {
      writer.writeNext(HEADER);
      writer.writeAll(rows);
    }
  }

  /**
   * Builds a resolution map: DB symbol → canonical Yahoo symbol.
   */
  private Map<String, String> buildSymbolResolutionMap(List<OpenedPosition> openedPositions) {
    Map<String, String> tickerToYahoo = new HashMap<>();
    Set<String> allDbSymbols = new HashSet<>();

    for (OpenedPosition p : openedPositions) {
      allDbSymbols.add(p.getSymbol());
      if (p.getSymbol() != null && hasKnownExchangeSuffix(p.getSymbol())) {
        String baseTicker = p.getSymbol().substring(0, p.getSymbol().indexOf('.'));
        tickerToYahoo.putIfAbsent(baseTicker, toYahooSymbol(p.getSymbol()));
      }
    }

    Map<String, String> resolution = new HashMap<>();
    for (String dbSymbol : allDbSymbols) {
      if (dbSymbol == null) {
        continue;
      }
      if (dbSymbol.contains(".")) {
        resolution.put(dbSymbol, toYahooSymbol(dbSymbol));
      } else {
        String fromXtb = tickerToYahoo.get(dbSymbol);
        if (fromXtb != null) {
          resolution.put(dbSymbol, fromXtb);
        } else {
          String hintSuffix = IBKR_EXCHANGE_HINTS.get(dbSymbol);
          resolution.put(dbSymbol, hintSuffix != null ? dbSymbol + hintSuffix : dbSymbol);
        }
      }
    }
    return resolution;
  }


  private static String resolveSymbol(String dbSymbol, Map<String, String> resolution) {
    if (dbSymbol == null) {
      return "";
    }
    String resolved = resolution.get(dbSymbol);
    return resolved != null ? resolved : toYahooSymbol(dbSymbol);
  }

  private String[] buildRow(
      String symbol,
      String currentPrice,
      LocalDate month,
      double price,
      double quantity,
      String transactionType) {
    String[] row = new String[HEADER.length];
    java.util.Arrays.fill(row, "");
    LocalDateTime rowTime = month.atStartOfDay();
    row[IDX_SYMBOL] = symbol;
    row[IDX_CURRENT_PRICE] = currentPrice != null ? currentPrice : "";
    row[IDX_DATE] = rowTime.toLocalDate().format(CSV_DATE_FMT);
    row[IDX_TIME] = rowTime.toLocalTime().format(CSV_TIME_FMT) + " " + DEFAULT_ZONE.getId();
    row[IDX_TRADE_DATE] = rowTime.toLocalDate().format(CSV_TRADE_DATE_FMT);
    row[IDX_PURCHASE_PRICE] = toString(price);
    row[IDX_QUANTITY] = trimDouble(quantity);
    row[IDX_TRANSACTION_TYPE] = transactionType;
    return row;
  }

  private String toString(Double value) {
    if (value == null) {
      return "";
    }
    return String.format(Locale.ENGLISH, "%s", value);
  }

  private String toString(double value) {
    return String.format(Locale.ENGLISH, "%s", value);
  }

  private static final Map<String, String> EXCHANGE_MAPPING =
      Map.ofEntries(
          Map.entry(".US", ""),
          Map.entry(".UK", ".L"),
          Map.entry(".PL", ".WA"),
          Map.entry(".FR", ".PA"),
          Map.entry(".NL", ".AS"),
          Map.entry(".ES", ".MC"),
          Map.entry(".IT", ".MI"),
          Map.entry(".SE", ".ST"),
          Map.entry(".NO", ".OL"),
          Map.entry(".FI", ".HE"),
          Map.entry(".DK", ".CO"));

  private static String toYahooSymbol(String dbSymbol) {
    if (dbSymbol == null) {
      return "";
    }
    for (Map.Entry<String, String> mapping : EXCHANGE_MAPPING.entrySet()) {
      if (dbSymbol.endsWith(mapping.getKey())) {
        String ticker = dbSymbol.substring(0, dbSymbol.length() - mapping.getKey().length());
        return ticker + mapping.getValue();
      }
    }
    return dbSymbol;
  }

  /** Returns true if the symbol ends with a known XTB exchange suffix (.US, .UK, .PL, etc.). */
  private static boolean hasKnownExchangeSuffix(String symbol) {
    for (String suffix : EXCHANGE_MAPPING.keySet()) {
      if (symbol.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private static double safe(Double value) {
    return value == null ? 0.0d : value;
  }

  private static String trimDouble(double value) {
    if (Math.rint(value) == value) {
      return String.format(Locale.ENGLISH, "%.0f", value);
    }
    return String.format(Locale.ENGLISH, "%.6f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  public record ReconcileResult(
      int appendedRows, ZonedDateTime lastUpdate, String inputPath, String outputPath) {}

  private record CsvExportPayload(List<String[]> rows, ZonedDateTime lastSnapshotAt) {}

}
