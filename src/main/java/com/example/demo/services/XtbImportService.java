package com.example.demo.services;

import com.example.demo.data.CashOperationType;
import com.example.demo.data.CurrencyType;
import com.example.demo.data.PositionType;
import com.example.demo.data.repository.*;
import com.example.demo.services.imports.ImportExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BiFunction;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class XtbImportService {

    /**
     * Locale-neutral formatter for non-string cells. {@code Locale.ROOT} keeps a "." as the
     * decimal separator so numeric ids stay parseable downstream.
     */
    private static final DataFormatter DATA_FORMATTER = new DataFormatter(Locale.ROOT);

    private final ClosedPositionRepository closedPositionRepository;
    private final OpenedPositionRepository openedPositionRepository;
    private final CashOperationRepository cashOperationRepository;
    private final AccountSummaryRepository accountSummaryRepository;

    public ImportExecutionResult importXtbExport(InputStream excelInputStream) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(excelInputStream)) {
            Sheet openSheet = findOpenPositionsSheet(workbook);
            ImportContext openContext = getImportContext(openSheet);

            // Some XTB exports leave the account-currency header blank. Since Gross P/L /
            // Commission / Swap are reported in the ACCOUNT currency, a wrong/null currency
            // corrupts every conversion, so resolve it explicitly before importing anything.
            CurrencyType resolved = openContext.currency;
            if (resolved == null) {
                resolved = inferCurrency(openSheet);
            }
            if (resolved == null) {
                resolved = inferCurrency(workbook.getSheet("CLOSED POSITION HISTORY"));
            }
            if (resolved == null) {
                throw new IllegalStateException("Account " + openContext.account
                        + ": the account currency is missing from the export and could not be inferred "
                        + "(unsupported exchange?). Import aborted to avoid corrupt data.");
            }
            final CurrencyType currency = resolved;

            List<CashOperation> cashOperations = importCashOperations(workbook.getSheet("CASH OPERATION HISTORY"));
            // The MT (leveraged/CFD) sub-account keeps its own cash ledger; merge it so
            // CFD-account dividends and adjustments are not silently dropped.
            cashOperations.addAll(importCashOperations(workbook.getSheet("BALANCE OPERATION HISTORY MT")));
            cashOperations.forEach(operation -> {
                if (operation.getCurrency() == null) {
                    operation.setCurrency(currency);
                }
            });
            cashOperationRepository.saveAll(cashOperations);

            List<ClosedPosition> closedPositions = importClosedPositions(workbook.getSheet("CLOSED POSITION HISTORY MT"));
            closedPositions.addAll(importClosedPositions(workbook.getSheet("CLOSED POSITION HISTORY")));
            closedPositions.forEach(position -> {
                if (position.getCurrency() == null) {
                    position.setCurrency(currency);
                }
            });
            closedPositionRepository.saveAll(closedPositions);

            ImportedItems<OpenedPosition> openedImport = importItems(openSheet, this::convertToOpenedPosition);
            openedImport.items.forEach(position -> {
                if (position.getCurrency() == null) {
                    position.setCurrency(currency);
                }
            });
            if (StringUtils.hasText(openedImport.context.account)) {
                openedPositionRepository.removeAllByAccountNotIn(openedImport.context.account, openedImport.items);
            }
            openedPositionRepository.saveAll(openedImport.items);

            // Capture the broker-reported total assets value (Equity) per account so the
            // dashboard "Balance" reflects today's holdings + cash, not P/L.
            importAccountSummary(openSheet, currency);
            importAccountSummary(workbook.getSheet("BALANCE OPERATION HISTORY MT"), currency);

            int total = cashOperations.size() + closedPositions.size() + openedImport.items.size();
            String details = String.format(
                    "XTB %s: %d cash operations, %d closed positions, %d open positions",
                    openContext.account != null ? openContext.account : "?",
                    cashOperations.size(), closedPositions.size(), openedImport.items.size());
            return new ImportExecutionResult(total, total, 0, details);
        }
    }

    /**
     * Maps an XTB symbol's exchange suffix to its quote currency. Used only to infer a
     * missing account currency together with the Gross-P/L ratio check, so it must stay
     * conservative: only suffixes whose quote currency is a supported {@link CurrencyType}.
     */
    private CurrencyType currencyForSuffix(String symbol) {
        if (symbol == null) {
            return null;
        }
        int dot = symbol.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        switch (symbol.substring(dot + 1).trim().toUpperCase()) {
            case "US":
            case "UK": // LSE ETFs on XTB (SGLD, VWRA, ...) are USD-denominated
                return CurrencyType.USD;
            case "PL":
                return CurrencyType.PLN;
            case "DE":
            case "FR":
            case "NL":
            case "IT":
            case "ES":
            case "FI":
            case "PT":
            case "IE":
            case "AT":
            case "BE":
                return CurrencyType.EUR;
            default:
                return null;
        }
    }

    /**
     * Infers the account currency from position rows when the header is blank. XTB reports
     * Gross P/L in the account currency while prices are in the instrument currency, so a
     * position whose {@code grossPL ≈ volume * (price - openPrice)} reveals that the account
     * currency equals that instrument's quote currency. The most-voted currency wins.
     */
    private CurrencyType inferCurrency(Sheet sheet) {
        if (sheet == null) {
            return null;
        }
        ImportContext context = getImportContext(sheet);
        Map<String, Integer> columns = context.columnIndexes;
        if (CollectionUtils.isEmpty(columns)) {
            return null;
        }
        Integer volumeCol = columns.get("Volume");
        Integer openCol = columns.get("Open price");
        Integer priceCol = columns.containsKey("Market price") ? columns.get("Market price") : columns.get("Close price");
        Integer grossCol = columns.get("Gross P/L");
        Integer symbolCol = columns.get("Symbol");
        if (volumeCol == null || openCol == null || priceCol == null || grossCol == null || symbolCol == null) {
            return null;
        }

        Map<CurrencyType, Integer> votes = new EnumMap<>(CurrencyType.class);
        for (Row row : sheet) {
            if (isRowEmpty(row) || row.getRowNum() <= context.headerRowNum
                    || row.getCell(1) == null || row.getCell(1).getCellType() != CellType.NUMERIC) {
                continue;
            }
            Double volume = getDouble(row.getCell(volumeCol));
            Double openPrice = getDouble(row.getCell(openCol));
            Double price = getDouble(row.getCell(priceCol));
            Double gross = getDouble(row.getCell(grossCol));
            String symbol = getString(row.getCell(symbolCol));
            if (volume == null || openPrice == null || price == null || gross == null || symbol == null) {
                continue;
            }
            double instrumentPl = volume * (price - openPrice);
            if (Math.abs(instrumentPl) < 0.5) {
                continue; // too small to give a reliable ratio
            }
            double ratio = gross / instrumentPl;
            if (ratio >= 0.97 && ratio <= 1.03) {
                CurrencyType currency = currencyForSuffix(symbol);
                if (currency != null) {
                    votes.merge(currency, 1, Integer::sum);
                }
            }
        }
        return votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void importAccountSummary(Sheet sheet, CurrencyType fallbackCurrency) {
        if (sheet == null) {
            return;
        }
        ImportContext context = getImportContext(sheet);
        CurrencyType currency = context.currency != null ? context.currency : fallbackCurrency;
        if (!StringUtils.hasText(context.account) || currency == null) {
            return;
        }

        Integer balanceCol = null;
        Integer equityCol = null;
        int headerRowNum = -1;
        for (Row row : sheet) {
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell != null && cell.getCellType() == CellType.STRING) {
                    String value = cell.getStringCellValue().trim();
                    if ("Balance".equalsIgnoreCase(value)) {
                        balanceCol = c;
                        headerRowNum = row.getRowNum();
                    } else if ("Equity".equalsIgnoreCase(value)) {
                        equityCol = c;
                    }
                }
            }
            if (balanceCol != null && equityCol != null) {
                break;
            }
        }
        if (balanceCol == null || equityCol == null) {
            return;
        }

        Row valueRow = sheet.getRow(headerRowNum + 1);
        if (valueRow == null) {
            return;
        }

        AccountSummary summary = accountSummaryRepository.findById(context.account)
                .orElseGet(AccountSummary::new);
        summary.setAccount(context.account);
        summary.setCurrency(currency);
        summary.setBalance(getDouble(valueRow.getCell(balanceCol)));
        summary.setEquity(getDouble(valueRow.getCell(equityCol)));
        summary.setUpdatedAt(ZonedDateTime.now());
        accountSummaryRepository.save(summary);
    }

    private List<CashOperation> importCashOperations(Sheet sheet) {
        return importItems(sheet, this::convertToCashOperation).items;
    }

    private List<ClosedPosition> importClosedPositions(Sheet sheet) {
        return importItems(sheet, this::convertToClosedPosition).items;
    }

    private <T> ImportedItems<T> importItems(Sheet sheet, BiFunction<Row, ImportContext, T> converter) {
        ImportContext context = getImportContext(sheet);
        if (CollectionUtils.isEmpty(context.columnIndexes)) {
            return new ImportedItems<>(context, new ArrayList<>());
        }

        List<T> items = new ArrayList<>();
        for (Row row : sheet) {
            if (isRowEmpty(row)
                    || row.getRowNum() <= context.headerRowNum
                    || row.getCell(1) == null
                    || row.getCell(1).getCellType() != CellType.NUMERIC) {
                continue;
            }

            items.add(converter.apply(row, context));
        }
        return new ImportedItems<>(context, items);
    }

    private ClosedPosition convertToClosedPosition(Row row, ImportContext context) {
        ClosedPosition position = new ClosedPosition();
        position.setId(getLong(row.getCell(context.columnIndexes.get("Position"))));
        position.setAccount(context.account);
        position.setSymbol(getString(row.getCell(context.columnIndexes.get("Symbol"))));
        position.setType(PositionType.fromString(getString(row.getCell(context.columnIndexes.get("Type")))));
        position.setVolume(getDouble(row.getCell(context.columnIndexes.get("Volume"))));
        position.setCurrency(context.currency);
        position.setOpenTime(getDateTime(row.getCell(context.columnIndexes.get("Open time"))));
        position.setOpenPrice(getDouble(row.getCell(context.columnIndexes.get("Open price"))));
        position.setCloseTime(getDateTime(row.getCell(context.columnIndexes.get("Close time"))));
        position.setClosePrice(getDouble(row.getCell(context.columnIndexes.get("Close price"))));
        position.setComment(getString(row.getCell(context.columnIndexes.get("Comment"))));
        position.setCommission(getDouble(row.getCell(context.columnIndexes.get("Commission"))));
        position.setSwap(getDouble(cell(row, context, "Swap")));
        position.setProfit(getDouble(row.getCell(context.columnIndexes.get("Gross P/L"))));
        return position;
    }


    private OpenedPosition convertToOpenedPosition(Row row, ImportContext context) {
        OpenedPosition position = new OpenedPosition();
        position.setId(getLong(row.getCell(context.columnIndexes.get("Position"))));
        position.setAccount(context.account);
        position.setSymbol(getString(row.getCell(context.columnIndexes.get("Symbol"))));
        position.setType(PositionType.fromString(getString(row.getCell(context.columnIndexes.get("Type")))));
        position.setVolume(getDouble(row.getCell(context.columnIndexes.get("Volume"))));
        position.setCurrency(context.currency);
        position.setOpenTime(getDateTime(row.getCell(context.columnIndexes.get("Open time"))));
        position.setOpenPrice(getDouble(row.getCell(context.columnIndexes.get("Open price"))));
        position.setMarketPrice(getDouble(row.getCell(context.columnIndexes.get("Market price"))));
        position.setPurchaseValue(getDouble(row.getCell(context.columnIndexes.get("Purchase value"))));
        position.setSwap(getDouble(row.getCell(context.columnIndexes.get("Swap"))));
        position.setMargin(getDouble(row.getCell(context.columnIndexes.get("Margin"))));
        position.setComment(getString(row.getCell(context.columnIndexes.get("Comment"))));
        position.setCommission(getDouble(row.getCell(context.columnIndexes.get("Commission"))));
        position.setProfit(getDouble(row.getCell(context.columnIndexes.get("Gross P/L"))));
        return position;
    }

    private CashOperation convertToCashOperation(Row row, ImportContext context) {
        CashOperation operation = new CashOperation();
        operation.setId(getLong(cell(row, context, "ID")));
        operation.setAccount(context.account);
        operation.setType(CashOperationType.fromString(getString(cell(row, context, "Type"))));
        operation.setSymbol(getString(cell(row, context, "Symbol")));
        operation.setAmount(getDouble(cell(row, context, "Amount")));
        operation.setCurrency(context.currency);
        operation.setComment(getString(cell(row, context, "Comment")));
        operation.setDate(getDateTime(cell(row, context, "Time")));
        return operation;
    }

    /**
     * Safe column accessor: returns {@code null} when the header label is absent on
     * this sheet (e.g. the MT balance ledger has no {@code Symbol} column), avoiding
     * a NullPointerException when unboxing a missing column index.
     */
    private Cell cell(Row row, ImportContext context, String label) {
        Integer index = context.columnIndexes.get(label);
        return index == null ? null : row.getCell(index);
    }

    private ImportContext getImportContext(Sheet sheet) {
        Map<String, Integer> columnIndexes = new HashMap<>();
        int headerRowNum = -1;
        Pair<Integer, Integer> accountCell = null;
        Pair<Integer, Integer> currencyCell = null;
        String account = null;
        CurrencyType currency = null;

        if (sheet == null) {
            return new ImportContext(headerRowNum, columnIndexes, account, currency);
        }

        for (Row row : sheet) {
            if (!isRowEmpty(row)) {
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null && cell.getCellType() == CellType.STRING) {
                        if ("Account".equalsIgnoreCase(cell.getStringCellValue())) {
                            accountCell = Pair.of(cell.getRowIndex() + 1, cell.getColumnIndex());
                        } else if ("Currency".equalsIgnoreCase(cell.getStringCellValue())) {
                            currencyCell = Pair.of(cell.getRowIndex() + 1, cell.getColumnIndex());
                        }
                    }
                }
            }
            if (accountCell != null && accountCell.getFirst() == row.getRowNum()) {
                account = getString(row.getCell(accountCell.getSecond()));
            }
            if (currencyCell != null && currencyCell.getFirst() == row.getRowNum()) {
                String currencyValue = getString(row.getCell(currencyCell.getSecond()));
                if (StringUtils.hasText(currencyValue)) {
                    currency = CurrencyType.valueOf(currencyValue);
                }
            }

            Cell firstCell = row.getCell(1);
            if (firstCell != null && firstCell.getCellType() == CellType.STRING && (Set.of("position", "id")
                    .contains(firstCell.getStringCellValue().trim().toLowerCase()))) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.STRING) {
                        columnIndexes.put(cell.getStringCellValue().trim(), cell.getColumnIndex());
                    }
                }
                headerRowNum = row.getRowNum();
                break;
            }
        }
        return new ImportContext(headerRowNum, columnIndexes, account, currency);
    }

    private Sheet findOpenPositionsSheet(Workbook workbook) {
        for (Sheet sheet : workbook) {
            if (sheet == null || sheet.getSheetName() == null) {
                continue;
            }
            String normalized = sheet.getSheetName().trim().toUpperCase();
            if (normalized.startsWith("OPEN POSITION") || normalized.startsWith("OPENED POSITION")) {
                return sheet;
            }
        }
        return workbook.getNumberOfSheets() > 1 ? workbook.getSheetAt(1) : null;
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private ZonedDateTime getDateTime(Cell cell) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            return cell.getDateCellValue().toInstant()
                    .atZone(ZoneId.systemDefault());
        }
        return null;
    }

    private Long getLong(Cell cell) {
        try {
            if (cell == null) {
                return -1L;
            }
            if (cell.getCellType() == CellType.NUMERIC) {
                return (long) cell.getNumericCellValue();
            }
            String raw = getString(cell);
            if (!StringUtils.hasText(raw)) {
                return -1L;
            }
            return Long.valueOf(raw);
        } catch (Exception e) {
            return -1L;
        }
    }

    private String getString(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            String raw = cell.getStringCellValue();
            return raw == null ? null : raw.trim();
        }
        // POI would throw IllegalStateException for non-STRING cells; the formatter
        // safely renders NUMERIC / BOOLEAN / FORMULA / ERROR with Locale.ROOT (so a
        // numeric id stays "12345", not "12 345" or "12,345.00").
        String formatted = DATA_FORMATTER.formatCellValue(cell);
        if (formatted == null) {
            return null;
        }
        String trimmed = formatted.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Double getDouble(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            String stringCellValue = cell.getStringCellValue();
            if (!StringUtils.hasText(stringCellValue)) {
                return null;
            }
            return Double.valueOf(stringCellValue);
        }
        return null;
    }

    private static class ImportContext {
        private final int headerRowNum;
        private final Map<String, Integer> columnIndexes;
        private final String account;
        private final CurrencyType currency;

        private ImportContext(int headerRowNum,
                              Map<String, Integer> columnIndexes,
                              String account,
                              CurrencyType currency) {
            this.headerRowNum = headerRowNum;
            this.columnIndexes = columnIndexes;
            this.account = account;
            this.currency = currency;
        }
    }

    private static class ImportedItems<T> {
        private final ImportContext context;
        private final List<T> items;

        private ImportedItems(ImportContext context, List<T> items) {
            this.context = context;
            this.items = items;
        }
    }
}
