package com.example.demo.services;

import com.example.demo.data.CashOperationType;
import com.example.demo.data.CurrencyType;
import com.example.demo.data.PositionType;
import com.example.demo.data.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.util.Pair;
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

    private final ClosedPositionRepository closedPositionRepository;
    private final OpenedPositionRepository openedPositionRepository;
    private final CashOperationRepository cashOperationRepository;

    public void importXtbExport(InputStream excelInputStream) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(excelInputStream)) {
            List<CashOperation> cashOperations = importCashOperations(workbook.getSheet("CASH OPERATION HISTORY"));
            cashOperationRepository.saveAll(cashOperations);

            List<ClosedPosition> closedPositions = importClosedPositions(workbook.getSheet("CLOSED POSITION HISTORY MT"));
            closedPositions.addAll(importClosedPositions(workbook.getSheet("CLOSED POSITION HISTORY")));
            closedPositionRepository.saveAll(closedPositions);

            ImportedItems<OpenedPosition> openedImport = importItems(findOpenPositionsSheet(workbook), this::convertToOpenedPosition);
            if (StringUtils.hasText(openedImport.context.account)) {
                openedPositionRepository.removeAllByAccountNotIn(openedImport.context.account, openedImport.items);
            }
            openedPositionRepository.saveAll(openedImport.items);
        }
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
        operation.setId(getLong(row.getCell(context.columnIndexes.get("ID"))));
        operation.setAccount(context.account);
        operation.setType(CashOperationType.fromString(getString(row.getCell(context.columnIndexes.get("Type")))));
        operation.setSymbol(getString(row.getCell(context.columnIndexes.get("Symbol"))));
        operation.setAmount(getDouble(row.getCell(context.columnIndexes.get("Amount"))));
        operation.setCurrency(context.currency);
        operation.setComment(getString(row.getCell(context.columnIndexes.get("Comment"))));
        operation.setDate(getDateTime(row.getCell(context.columnIndexes.get("Time"))));
        return operation;
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
        if (cell.getCellType() == CellType.NUMERIC) {
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
        if (cell == null) return null;
        return cell.getStringCellValue().trim();
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
