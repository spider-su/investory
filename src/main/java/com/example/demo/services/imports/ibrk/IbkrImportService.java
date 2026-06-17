package com.example.demo.services.imports.ibrk;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionType;
import com.example.demo.infrastructure.repository.*;
import com.example.demo.services.imports.ImportExecutionResult;
import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Imports an Interactive Brokers (IBKR) "Transaction History" activity CSV.
 *
 * <p>IBKR exports a flat cash ledger rather than XTB's position-based sheets. The money
 * columns (Gross / Commission / Net Amount) are always in the account base currency (USD),
 * while Price is in the instrument's trade currency. Therefore:
 * <ul>
 *   <li>Buy / Sell rows are FIFO lot-matched into {@code closed_positions} with realized
 *       P/L computed straight from the USD net amounts (no FX needed).</li>
 *   <li>All other rows (dividends, interest, withholding, deposits, withdrawals, forex,
 *       corporate actions) become {@code cash_operations}.</li>
 * </ul>
 * IBKR rows have no broker id, so stable <b>negative</b> synthetic ids are generated
 * (XTB uses positive ids) to keep re-imports idempotent.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class IbkrImportService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final String SECTION = "Transaction History";
    private static final String IBKR_ACCOUNT = "IBKR";

    private final ClosedPositionRepository closedPositionRepository;
    private final CashOperationRepository cashOperationRepository;
    private final OpenedPositionRepository openedPositionRepository;
    private final AccountSummaryRepository accountSummaryRepository;

    public ImportExecutionResult importStatement(InputStream csvStream) throws Exception {
        List<String[]> rows;
        try (CSVReader reader = new CSVReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
            rows = reader.readAll();
        }

        Map<String, Integer> col = locateHeader(rows, SECTION);
        Map<String, Integer> dedup = new HashMap<>();
        List<Trade> trades = new ArrayList<>();
        List<CashOperation> cashOps = new ArrayList<>();
        Set<String> disposedSymbols = new HashSet<>();
        int total = 0;
        int failed = 0;

        for (String[] r : rows) {
            if (col.isEmpty() || r.length <= 2 || !SECTION.equals(r[0]) || !"Data".equals(r[1])) {
                continue;
            }
            total++;
            try {
                String type = value(r, col, "Transaction Type");
                String account = orDefault(value(r, col, "Account"), "IBKR");
                String symbol = cleanSymbol(value(r, col, "Symbol"));
                String description = value(r, col, "Description");
                ZonedDateTime date = parseDate(value(r, col, "Date"));
                Double quantity = parseNumber(value(r, col, "Quantity"));
                Double price = parseNumber(value(r, col, "Price"));
                Double net = parseNumber(value(r, col, "Net Amount"));

                if (isTrade(type)) {
                    if (symbol == null || quantity == null || net == null) {
                        failed++;
                        continue;
                    }
                    trades.add(new Trade(account, symbol, date, quantity, price, net));
                } else {
                    CashOperation op = new CashOperation();
                    String key = String.join("|", account, isoDate(date), type, String.valueOf(symbol),
                            String.valueOf(net), description);
                    op.setId(syntheticId(key, dedup));
                    op.setAccount(account);
                    op.setType(mapCashType(type));
                    op.setSymbol(symbol);
                    op.setAmount(net != null ? net : 0.0);
                    op.setCurrency(CurrencyType.USD);
                    op.setComment(description);
                    op.setDate(date);
                    cashOps.add(op);

                    // A corporate action on a held symbol (e.g. bond call / redemption) disposes
                    // the holding; its proceeds are already reflected in cash, so drop the lot.
                    if ("Corporate Action".equalsIgnoreCase(type) && symbol != null) {
                        disposedSymbols.add(symbol);
                    }
                }
            } catch (Exception e) {
                failed++;
                log.warn("Skipping IBKR row: {}", e.getMessage());
            }
        }

        FifoResult fifo = matchFifo(trades, dedup);

        cashOperationRepository.saveAll(cashOps);
        closedPositionRepository.saveAll(fifo.closed);

        List<OpenedPosition> openPositions;
        if (findSection(rows, "Open Positions", "Positions") != null) {
            // Activity Statement: use broker-reported open positions (market values) + NAV.
            openPositions = importOpenPositions(rows, dedup);
            importNav(rows, openPositions);
        } else {
            // Transactions-only file: derive open holdings (at cost) and equity from the
            // ledger, so the IBKR account still contributes to Balance.
            disposedSymbols.forEach(fifo.openLots::remove); // redeemed/called holdings are no longer held
            openPositions = buildOpenPositionsFromLots(fifo.openLots, dedup);
            replaceIbkrOpenPositions(openPositions);
            Double endingCash = readEndingCash(rows);
            double openCostBasis = openPositions.stream().mapToDouble(p -> nz(p.getPurchaseValue())).sum();
            double equity = nz(endingCash) + openCostBasis;
            if (equity != 0.0) {
                upsertIbkrSummary(round(equity), endingCash);
            }
        }

        String details = String.format(
                "IBKR: %d cash operations, %d closed trades (FIFO), %d open positions, %d skipped",
                cashOps.size(), fifo.closed.size(), openPositions.size(), failed);
        log.info(details);
        return new ImportExecutionResult(total, cashOps.size() + fifo.closed.size() + openPositions.size(), failed, details);
    }

    /** Parses the "Open Positions" section (if present) into IBKR opened positions and replaces them. */
    private List<OpenedPosition> importOpenPositions(List<String[]> rows, Map<String, Integer> dedup) {
        String section = findSection(rows, "Open Positions", "Positions");
        if (section == null) {
            return List.of();
        }
        Map<String, Integer> col = locateHeader(rows, section);
        Integer cSymbol = colIndex(col, "Symbol");
        Integer cQuantity = colIndex(col, "Quantity");
        Integer cCurrency = colIndex(col, "Currency");
        Integer cCost = colIndex(col, "Cost Price");
        Integer cBasis = colIndex(col, "Cost Basis", "Cost Basis Money");
        Integer cClose = colIndex(col, "Close Price", "Mark Price");
        Integer cUnrealized = colIndex(col, "Unrealized P/L", "Unrealized P&L", "Fifo P/L Unrealized");
        Integer cDiscriminator = colIndex(col, "DataDiscriminator");

        ZonedDateTime now = ZonedDateTime.now();
        List<OpenedPosition> positions = new ArrayList<>();
        for (String[] r : rows) {
            if (r.length <= 2 || !section.equals(r[0]) || !"Data".equals(r[1])) {
                continue;
            }
            // Skip lot-level / subtotal rows; keep the consolidated "Summary" position.
            if (cDiscriminator != null && cDiscriminator < r.length
                    && r[cDiscriminator] != null && !r[cDiscriminator].trim().isEmpty()
                    && !"Summary".equalsIgnoreCase(r[cDiscriminator].trim())) {
                continue;
            }
            String symbol = cleanSymbol(at(r, cSymbol));
            Double quantity = parseNumber(at(r, cQuantity));
            if (symbol == null || quantity == null || quantity == 0.0) {
                continue;
            }
            OpenedPosition p = new OpenedPosition();
            p.setId(syntheticId("POS|" + IBKR_ACCOUNT + "|" + symbol, dedup));
            p.setAccount(IBKR_ACCOUNT);
            p.setSymbol(symbol);
            p.setType(quantity >= 0 ? PositionType.BUY : PositionType.SELL);
            p.setCurrency(parseCurrency(at(r, cCurrency)));
            p.setVolume(quantity);
            p.setOpenTime(now);
            p.setOpenPrice(parseNumber(at(r, cCost)));
            p.setMarketPrice(parseNumber(at(r, cClose)));
            p.setPurchaseValue(parseNumber(at(r, cBasis)));
            p.setProfit(orZero(parseNumber(at(r, cUnrealized))));
            p.setCommission(0.0);
            p.setSwap(0.0);
            p.setComment("IBKR position snapshot");
            positions.add(p);
        }

        // Full replace of the IBKR account's open positions (snapshot semantics).
        replaceIbkrOpenPositions(positions);
        return positions;
    }

    /** Builds IBKR open holdings from the FIFO residual buy lots, valued at cost (no market price available). */
    private List<OpenedPosition> buildOpenPositionsFromLots(Map<String, Deque<Lot>> openLots, Map<String, Integer> dedup) {
        ZonedDateTime now = ZonedDateTime.now();
        List<OpenedPosition> positions = new ArrayList<>();
        for (Map.Entry<String, Deque<Lot>> entry : openLots.entrySet()) {
            double quantity = 0.0;
            double cost = 0.0;
            for (Lot lot : entry.getValue()) {
                quantity += lot.quantity;
                cost += lot.quantity * lot.costPerShare;
            }
            if (quantity <= 1e-9) {
                continue;
            }
            double avgCost = cost / quantity;
            OpenedPosition p = new OpenedPosition();
            p.setId(syntheticId("POS|" + IBKR_ACCOUNT + "|" + entry.getKey(), dedup));
            p.setAccount(IBKR_ACCOUNT);
            p.setSymbol(entry.getKey());
            p.setType(PositionType.BUY);
            p.setCurrency(CurrencyType.USD);
            p.setVolume(round(quantity));
            p.setOpenTime(now);
            p.setOpenPrice(round(avgCost));
            p.setMarketPrice(round(avgCost)); // no market price in a transactions file -> at cost
            p.setPurchaseValue(round(cost));
            p.setProfit(0.0);                  // unrealized unknown without market prices
            p.setCommission(0.0);
            p.setSwap(0.0);
            p.setComment("IBKR holding (cost basis from transactions)");
            positions.add(p);
        }
        return positions;
    }

    private void replaceIbkrOpenPositions(List<OpenedPosition> positions) {
        List<OpenedPosition> existing = openedPositionRepository.findAllByAccount(IBKR_ACCOUNT);
        if (!existing.isEmpty()) {
            openedPositionRepository.deleteAll(existing);
        }
        if (!positions.isEmpty()) {
            openedPositionRepository.saveAll(positions);
        }
    }

    /** Reads "Ending Cash" from the IBKR Summary section, if present. */
    private Double readEndingCash(List<String[]> rows) {
        String section = findSection(rows, "Summary");
        if (section == null) {
            return null;
        }
        Map<String, Integer> col = locateHeader(rows, section);
        Integer cName = colIndex(col, "Field Name");
        Integer cValue = colIndex(col, "Field Value");
        if (cName == null || cValue == null) {
            return null;
        }
        for (String[] r : rows) {
            if (r.length <= 2 || !section.equals(r[0]) || !"Data".equals(r[1])) {
                continue;
            }
            if ("Ending Cash".equalsIgnoreCase(at(r, cName))) {
                return parseNumber(at(r, cValue));
            }
        }
        return null;
    }

    private void upsertIbkrSummary(double equity, Double cash) {
        AccountSummary summary = accountSummaryRepository.findById(IBKR_ACCOUNT).orElseGet(AccountSummary::new);
        summary.setAccount(IBKR_ACCOUNT);
        summary.setCurrency(CurrencyType.USD);
        summary.setEquity(equity);
        summary.setBalance(cash);
        summary.setUpdatedAt(ZonedDateTime.now());
        accountSummaryRepository.save(summary);
    }

    /** Reads the "Net Asset Value" section (or derives from positions) into the IBKR account summary. */
    private void importNav(List<String[]> rows, List<OpenedPosition> openPositions) {
        Double equity = null;
        Double cash = null;

        String section = findSection(rows, "Net Asset Value", "NAV");
        if (section != null) {
            Map<String, Integer> col = locateHeader(rows, section);
            Integer cClass = colIndex(col, "Asset Class", "Asset Category");
            Integer cTotal = colIndex(col, "Current Total", "Total");
            for (String[] r : rows) {
                if (r.length <= 2 || !section.equals(r[0]) || !"Data".equals(r[1])) {
                    continue;
                }
                String assetClass = at(r, cClass);
                Double currentTotal = parseNumber(at(r, cTotal));
                if (assetClass == null || currentTotal == null) {
                    continue;
                }
                if ("Total".equalsIgnoreCase(assetClass.trim())) {
                    equity = currentTotal;
                } else if ("Cash".equalsIgnoreCase(assetClass.trim())) {
                    cash = currentTotal;
                }
            }
        }

        // Fallback: no NAV section but we have positions -> approximate equity from market value.
        if (equity == null && !openPositions.isEmpty()) {
            equity = round(openPositions.stream()
                    .mapToDouble(p -> nz(p.getVolume()) * nz(p.getMarketPrice()))
                    .sum());
        }

        if (equity == null) {
            return;
        }
        upsertIbkrSummary(equity, cash);
    }

    private Map<String, Integer> locateHeader(List<String[]> rows, String section) {
        Map<String, Integer> col = new HashMap<>();
        for (String[] r : rows) {
            if (r.length > 2 && section.equals(r[0]) && "Header".equals(r[1])) {
                for (int i = 2; i < r.length; i++) {
                    col.put(r[i].trim(), i);
                }
                break;
            }
        }
        return col;
    }

    /** Returns the actual section name (first column) matching one of the candidate aliases. */
    private String findSection(List<String[]> rows, String... candidates) {
        Set<String> wanted = new HashSet<>();
        for (String c : candidates) {
            wanted.add(c.toLowerCase());
        }
        for (String[] r : rows) {
            if (r.length > 1 && "Header".equals(r[1]) && r[0] != null && wanted.contains(r[0].trim().toLowerCase())) {
                return r[0];
            }
        }
        return null;
    }

    private Integer colIndex(Map<String, Integer> col, String... aliases) {
        for (String alias : aliases) {
            Integer idx = col.get(alias);
            if (idx != null) {
                return idx;
            }
        }
        return null;
    }

    private String at(String[] row, Integer idx) {
        if (idx == null || idx >= row.length) {
            return null;
        }
        String v = row[idx];
        return v == null ? null : v.trim();
    }

    private CurrencyType parseCurrency(String raw) {
        if (StringUtils.hasText(raw)) {
            try {
                return CurrencyType.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // unsupported currency (e.g. GBP) -> treat as base USD
            }
        }
        return CurrencyType.USD;
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    /** FIFO-match Buy/Sell trades per symbol into realized closed positions (USD). */
    private FifoResult matchFifo(List<Trade> trades, Map<String, Integer> dedup) {
        trades.sort(Comparator.comparing(t -> t.date));
        Map<String, Deque<Lot>> openLots = new HashMap<>();
        List<ClosedPosition> result = new ArrayList<>();

        for (Trade t : trades) {
            if (t.quantity > 0) {
                // Buy: net is negative cash-out incl. commission -> cost per share in USD.
                double costPerShare = -t.net / t.quantity;
                openLots.computeIfAbsent(t.symbol, k -> new ArrayDeque<>())
                        .addLast(new Lot(t.quantity, costPerShare, t.date));
            } else if (t.quantity < 0) {
                double sellShares = -t.quantity;
                double proceedsPerShare = t.net / sellShares; // net is positive cash-in incl. commission
                Deque<Lot> queue = openLots.get(t.symbol);

                double matched = 0.0;
                double realized = 0.0;
                double costSum = 0.0;
                ZonedDateTime openTime = t.date;
                while (sellShares > 1e-9 && queue != null && !queue.isEmpty()) {
                    Lot lot = queue.peekFirst();
                    double take = Math.min(lot.quantity, sellShares);
                    realized += (proceedsPerShare - lot.costPerShare) * take;
                    costSum += lot.costPerShare * take;
                    if (matched == 0.0) {
                        openTime = lot.date; // earliest matched lot opens the trade
                    }
                    matched += take;
                    lot.quantity -= take;
                    sellShares -= take;
                    if (lot.quantity <= 1e-9) {
                        queue.pollFirst();
                    }
                }

                if (matched > 1e-9) {
                    ClosedPosition cp = new ClosedPosition();
                    cp.setId(syntheticId(String.join("|", t.symbol, isoDate(t.date),
                            String.valueOf(matched), String.valueOf(t.net)), dedup));
                    cp.setAccount(t.account);
                    cp.setSymbol(t.symbol);
                    cp.setType(PositionType.BUY);
                    cp.setCurrency(CurrencyType.USD);
                    cp.setVolume(matched);
                    cp.setOpenTime(openTime);
                    cp.setCloseTime(t.date);
                    cp.setOpenPrice(round(costSum / matched));   // USD cost basis / share
                    cp.setClosePrice(round(proceedsPerShare));   // USD proceeds / share
                    cp.setProfit(round(realized));               // realized P/L in USD (net of commissions)
                    cp.setCommission(0.0);
                    cp.setSwap(0.0);
                    result.add(cp);
                } else {
                    log.warn("IBKR sell of {} {} has no matching buy lots; skipped from realized P/L", sellShares, t.symbol);
                }
            }
        }
        return new FifoResult(result, openLots);
    }

    private CashOperationType mapCashType(String type) {
        if (type == null) {
            return CashOperationType.UNKNOWN;
        }
        switch (type.trim().toLowerCase()) {
            case "dividend":
                return CashOperationType.DIVIDEND;
            case "foreign tax withholding":
                return CashOperationType.WITHHOLDING_TAX;
            case "credit interest":
            case "investment interest received":
            case "investment interest paid":
                return CashOperationType.FREE_FUNDS_INTEREST;
            case "deposit":
                return CashOperationType.DEPOSIT;
            case "withdrawal":
                return CashOperationType.WITHDRAWAL;
            case "forex trade component":
                return CashOperationType.TRANSFER; // internal FX, not external funding
            default:
                return CashOperationType.UNKNOWN; // Corporate Action, Adjustment, ...
        }
    }

    private boolean isTrade(String type) {
        return "Buy".equalsIgnoreCase(type) || "Sell".equalsIgnoreCase(type);
    }

    private String value(String[] row, Map<String, Integer> col, String name) {
        Integer idx = col.get(name);
        if (idx == null || idx >= row.length) {
            return null;
        }
        String v = row[idx];
        return v == null ? null : v.trim();
    }

    private String cleanSymbol(String symbol) {
        if (!StringUtils.hasText(symbol) || "-".equals(symbol)) {
            return null;
        }
        return symbol.trim();
    }

    private Double parseNumber(String raw) {
        if (!StringUtils.hasText(raw) || "-".equals(raw)) {
            return null;
        }
        try {
            return Double.valueOf(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ZonedDateTime parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("Missing date");
        }
        return LocalDate.parse(raw.trim().substring(0, 10)).atStartOfDay(ZONE);
    }

    private String isoDate(ZonedDateTime date) {
        return date == null ? "" : date.toLocalDate().toString();
    }

    private String orDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /** Stable negative id from content hash; an occurrence counter disambiguates identical rows. */
    private long syntheticId(String key, Map<String, Integer> dedup) {
        int occurrence = dedup.merge(key, 1, Integer::sum);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((key + "#" + occurrence).getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (hash[i] & 0xffL);
            }
            value &= Long.MAX_VALUE; // non-negative
            return -(value == 0 ? 1 : value); // negative -> never collides with positive XTB ids
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash IBKR row id", e);
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class FifoResult {
        private final List<ClosedPosition> closed;
        private final Map<String, Deque<Lot>> openLots;

        private FifoResult(List<ClosedPosition> closed, Map<String, Deque<Lot>> openLots) {
            this.closed = closed;
            this.openLots = openLots;
        }
    }

    private static final class Trade {
        private final String account;
        private final String symbol;
        private final ZonedDateTime date;
        private final double quantity;
        private final Double price;
        private final double net;

        private Trade(String account, String symbol, ZonedDateTime date, double quantity, Double price, double net) {
            this.account = account;
            this.symbol = symbol;
            this.date = date;
            this.quantity = quantity;
            this.price = price;
            this.net = net;
        }
    }

    private static final class Lot {
        private double quantity;
        private final double costPerShare;
        private final ZonedDateTime date;

        private Lot(double quantity, double costPerShare, ZonedDateTime date) {
            this.quantity = quantity;
            this.costPerShare = costPerShare;
            this.date = date;
        }
    }
}

