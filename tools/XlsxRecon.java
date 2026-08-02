import java.io.FileInputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.*;

/**
 * Reconciliation helper: reads an XTB "Cash Operations" sheet the same way
 * XtbImportV2Service does (find the header row containing Type/Time/Amount,
 * skip rows without a parseable Time) and prints per-file:
 *   account, dataRows, sumAmount, and a per-Type breakdown (count,sum).
 */
public class XlsxRecon {
  public static void main(String[] args) throws Exception {
    for (String path : args) {
      try (Workbook wb = WorkbookFactory.create(new FileInputStream(path))) {
        Sheet sheet = wb.getSheet("Cash Operations");
        if (sheet == null) {
          System.out.println(path + "\tNO_CASH_SHEET");
          continue;
        }
        String account = readHeaderValue(sheet, "Account number");
        int[] hdr = findHeader(sheet);
        int headerRow = hdr[0];
        Map<String, Integer> col = new HashMap<>();
        Row hr = sheet.getRow(headerRow);
        for (int c = hr.getFirstCellNum(); c < hr.getLastCellNum(); c++) {
          String v = str(hr.getCell(c));
          if (v != null && !v.isBlank()) col.put(v.trim(), c);
        }
        Integer typeIdx = col.get("Type");
        Integer timeIdx = col.get("Time");
        Integer amtIdx = col.get("Amount");
        Integer commentIdx = col.get("Comment");
        int rows = 0;
        double sum = 0;
        Map<String, double[]> byType = new TreeMap<>();
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
          Row row = sheet.getRow(r);
          if (row == null) continue;
          String time = timeIdx == null ? null : str(row.getCell(timeIdx));
          if (time == null || time.isBlank()) continue; // footer/total rows
          rows++;
          double amt = amtIdx == null ? 0 : num(row.getCell(amtIdx));
          sum += amt;
          String type = typeIdx == null ? "" : str(row.getCell(typeIdx));
          if (type == null || type.isBlank()) {
            String cm = commentIdx == null ? null : str(row.getCell(commentIdx));
            type = cm == null ? "(blank)" : cm;
          }
          byType.computeIfAbsent(type.trim(), k -> new double[2]);
          byType.get(type.trim())[0] += 1;
          byType.get(type.trim())[1] += amt;
        }
        System.out.printf("FILE\t%s%n", path);
        System.out.printf("ACCT\t%s\tROWS\t%d\tSUM\t%.2f%n", account, rows, sum);
        for (Map.Entry<String, double[]> e : byType.entrySet()) {
          System.out.printf("TYPE\t%s\t%.0f\t%.2f%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
        }
        System.out.println();
      }
    }
  }

  static int[] findHeader(Sheet sheet) {
    for (int r = 0; r <= Math.min(30, sheet.getLastRowNum()); r++) {
      Row row = sheet.getRow(r);
      if (row == null) continue;
      boolean t = false, ti = false, a = false;
      for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
        String v = str(row.getCell(c));
        if (v == null) continue;
        v = v.trim();
        if (v.equals("Type")) t = true;
        if (v.equals("Time")) ti = true;
        if (v.equals("Amount")) a = true;
      }
      if (t && ti && a) return new int[] {r};
    }
    throw new IllegalStateException("no header row");
  }

  static String readHeaderValue(Sheet sheet, String label) {
    for (int r = 0; r <= Math.min(30, sheet.getLastRowNum()); r++) {
      Row row = sheet.getRow(r);
      if (row == null) continue;
      for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
        String v = str(row.getCell(c));
        if (v != null && v.trim().equalsIgnoreCase(label)) {
          for (int k = c + 1; k < c + 4 && k < row.getLastCellNum(); k++) {
            String nv = str(row.getCell(k));
            if (nv != null && !nv.isBlank()) return nv.trim();
          }
        }
      }
    }
    return null;
  }

  static String str(Cell cell) {
    if (cell == null) return null;
    switch (cell.getCellType()) {
      case STRING: return cell.getStringCellValue();
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toString();
        double d = cell.getNumericCellValue();
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.valueOf(d);
      case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
      case FORMULA:
        try { return cell.getStringCellValue(); } catch (Exception e) { return String.valueOf(cell.getNumericCellValue()); }
      default: return null;
    }
  }

  static double num(Cell cell) {
    if (cell == null) return 0;
    if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
    if (cell.getCellType() == CellType.STRING) {
      String s = cell.getStringCellValue().trim().replace(" ", "").replace(",", ".");
      if (s.isEmpty()) return 0;
      try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }
    return 0;
  }
}

