package com.smartbox.investory.investment.imports.xtb;

import com.smartbox.investory.investment.ledger.position.PositionType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.util.StringUtils;

final class XtbWorkbookReader {
  private static final DataFormatter DATA_FORMATTER = new DataFormatter(Locale.ROOT);
  private static final ZoneId UTC = ZoneId.of("UTC");
  private static final List<DateTimeFormatter> DATE_FORMATTERS =
      List.of(
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

  private XtbWorkbookReader() {}

  static String readHeaderValue(Sheet sheet, String key) {
    if (sheet == null) return null;
    for (Row row : sheet)
      if (key.equalsIgnoreCase(value(row.getCell(0)))) return value(row.getCell(1));
    return null;
  }

  static Map<String, Integer> findHeader(Sheet sheet, String... requiredColumns) {
    if (sheet == null) return Map.of();
    Set<String> required = Set.of(requiredColumns);
    for (Row row : sheet) {
      Map<String, Integer> columns = new HashMap<>();
      for (Cell cell : row) {
        String label = value(cell);
        if (!StringUtils.hasText(label)) continue;
        columns.put(label, cell.getColumnIndex());
        if ("Position ID".equalsIgnoreCase(label))
          columns.putIfAbsent("PositionEntity ID", cell.getColumnIndex());
      }
      if (columns.keySet().containsAll(required)) {
        columns.put("__row", row.getRowNum());
        return columns;
      }
    }
    return Map.of();
  }

  static List<Row> dataRows(Sheet sheet, Map<String, Integer> columns) {
    Integer headerRow = columns.get("__row");
    if (headerRow == null) return List.of();
    List<Integer> indexes =
        columns.entrySet().stream()
            .filter(entry -> !"__row".equals(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList();
    List<Row> rows = new java.util.ArrayList<>();
    for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
      Row row = sheet.getRow(i);
      if (row != null && !isDataRowEmpty(row, indexes)) rows.add(row);
    }
    return rows;
  }

  static ZonedDateTime parseDate(Cell cell) {
    if (cell == null) return null;
    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
      return cell.getLocalDateTimeCellValue().atZone(UTC);
    String text = value(cell);
    if (!StringUtils.hasText(text)) return null;
    for (DateTimeFormatter formatter : DATE_FORMATTERS) {
      try {
        return LocalDateTime.parse(text, formatter).atZone(UTC);
      } catch (DateTimeParseException ignored) {
        // Try next known date format.
      }
    }
    throw new IllegalArgumentException("Malformed XTB date: " + text);
  }

  static Optional<Long> parseLong(String value) {
    if (!StringUtils.hasText(value)) return Optional.empty();
    try {
      return Optional.of((long) Double.parseDouble(value.replace(',', '.')));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Malformed XTB number: " + value, exception);
    }
  }

  static PositionType parsePositionType(String value, int rowNumber) {
    if (!StringUtils.hasText(value))
      throw new IllegalArgumentException("Missing XTB position side at row " + rowNumber);
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (normalized.contains("SELL") || normalized.contains("SHORT")) return PositionType.SELL;
    if (normalized.contains("BUY") || normalized.contains("LONG")) return PositionType.BUY;
    throw new IllegalArgumentException("Unknown XTB position side: " + value);
  }

  static boolean hasCashRowData(Row row, Map<String, Integer> columns) {
    String type = cellValue(row, columns.get("Type"));
    String id = cellValue(row, columns.get("ID"));
    if (!StringUtils.hasText(id)
        && StringUtils.hasText(type)
        && Set.of("TOTAL", "SUMMARY").contains(type.trim().toUpperCase(Locale.ROOT))) return false;
    return List.of("ID", "Type", "Ticker", "Comment").stream()
        .map(columns::get)
        .map(index -> index == null ? null : value(row.getCell(index)))
        .anyMatch(StringUtils::hasText);
  }

  static Optional<Double> parseDouble(String value) {
    if (!StringUtils.hasText(value)) return Optional.empty();
    try {
      return Optional.of(Double.parseDouble(value.replace(',', '.')));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Malformed XTB number: " + value, exception);
    }
  }

  static Optional<BigDecimal> parseDecimal(String value) {
    if (!StringUtils.hasText(value)) return Optional.empty();
    try {
      return Optional.of(new BigDecimal(value.replace(',', '.')));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Malformed XTB number: " + value, exception);
    }
  }

  static String sourceTicker(Row row, Map<String, Integer> columns) {
    String ticker = cellValue(row, columns.get("Ticker"));
    if (!StringUtils.hasText(ticker) || !"3".equals(ticker.trim())) return ticker;
    boolean placeholderShape =
        columns.containsKey("Instrument")
            || columns.containsKey("Category")
            || columns.containsKey("PositionEntity ID")
            || columns.containsKey("Position ID");
    if (!placeholderShape) return ticker;
    String positionId =
        firstNonBlank(
            cellValue(row, columns.get("PositionEntity ID")),
            cellValue(row, columns.get("Position ID")));
    return isBlankSentinel(cellValue(row, columns.get("Instrument")))
            && isBlankSentinel(cellValue(row, columns.get("Category")))
            && isBlankSentinel(positionId)
        ? null
        : ticker;
  }

  static Map<String, String> rawRowValues(Row row, Map<String, Integer> columns) {
    Map<String, String> values = new LinkedHashMap<>();
    columns.forEach((name, index) -> values.put(name, cellValue(row, index)));
    return values;
  }

  static String rawRowText(Row row, Map<String, Integer> columns) {
    return String.join(
        "\t", columns.keySet().stream().map(name -> cellValue(row, columns.get(name))).toList());
  }

  static Cell cell(Row row, Integer index) {
    return index == null ? null : row.getCell(index);
  }

  static String cellValue(Row row, Integer index) {
    return index == null ? null : value(row.getCell(index));
  }

  static String value(Cell cell) {
    if (cell == null || cell.getCellType() == CellType.BLANK) return null;
    String rendered = DATA_FORMATTER.formatCellValue(cell);
    return StringUtils.hasText(rendered) ? rendered.trim() : null;
  }

  static byte[] readAllBytes(InputStream inputStream) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = inputStream.read(buffer)) != -1) output.write(buffer, 0, read);
    return output.toByteArray();
  }

  private static boolean isDataRowEmpty(Row row, Collection<Integer> indexes) {
    for (Integer index : indexes)
      if (index != null && index >= 0 && StringUtils.hasText(value(row.getCell(index))))
        return false;
    return true;
  }

  private static boolean isBlankSentinel(String value) {
    return !StringUtils.hasText(value) || "3".equals(value.trim());
  }

  private static String firstNonBlank(String first, String second) {
    return StringUtils.hasText(first) ? first : second;
  }
}
