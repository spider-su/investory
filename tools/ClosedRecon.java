import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.apache.poi.ss.usermodel.*;

/** C2 helper: count XTB "Closed Positions" rows and sum profit per account from source zips. */
public class ClosedRecon {
  public static void main(String[] args) throws Exception {
    Path dir = Paths.get(args[0]);
    Map<Long, long[]> count = new TreeMap<>();
    Map<Long, double[]> profit = new TreeMap<>();
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.zip")) {
      for (Path zip : ds) {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
          ZipEntry e;
          while ((e = zis.getNextEntry()) != null) {
            if (e.isDirectory() || !e.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) continue;
            byte[] bytes = zis.readAllBytes();
            try (Workbook wb = WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
              Sheet closed = wb.getSheet("Closed Positions");
              Sheet cash = wb.getSheet("Cash Operations");
              if (closed == null || cash == null) continue;
              Long acc = parseLong(readHeaderValue(cash, "AccountEntity number"));
              if (acc == null) acc = parseLong(readHeaderValue(closed, "AccountEntity"));
              if (acc == null) continue;
              int hdr = findHeader(closed, "Ticker", "Type", "Volume");
              Row hr = closed.getRow(hdr);
              Map<String, Integer> col = new HashMap<>();
              for (int c = hr.getFirstCellNum(); c < hr.getLastCellNum(); c++) {
                String v = str(hr.getCell(c));
                if (v != null && !v.isBlank()) col.put(v.trim(), c);
              }
              Integer tick = col.get("Ticker");
              Integer pl = col.getOrDefault("Profit/Loss", col.get("Gross Profit"));
              long rows = 0;
              double sum = 0;
              for (int r = hdr + 1; r <= closed.getLastRowNum(); r++) {
                Row row = closed.getRow(r);
                if (row == null) continue;
                String sym = tick == null ? null : str(row.getCell(tick));
                if (sym == null || sym.isBlank()) continue; // footer
                rows++;
                if (pl != null) sum += num(row.getCell(pl));
              }
              count.computeIfAbsent(acc, k -> new long[1])[0] += rows;
              profit.computeIfAbsent(acc, k -> new double[1])[0] += sum;
            }
          }
        }
      }
    }
    System.out.printf("%-11s %8s %14s%n", "ACCOUNT", "closed", "profitSum");
    for (Long acc : count.keySet()) {
      System.out.printf("%-11d %8d %14.2f%n", acc, count.get(acc)[0], profit.getOrDefault(acc, new double[1])[0]);
    }
  }

  static int findHeader(Sheet sheet, String... need) {
    for (int r = 0; r <= Math.min(30, sheet.getLastRowNum()); r++) {
      Row row = sheet.getRow(r);
      if (row == null) continue;
      Set<String> found = new HashSet<>();
      for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
        String v = str(row.getCell(c));
        if (v != null) found.add(v.trim());
      }
      boolean ok = true;
      for (String n : need) if (!found.contains(n)) ok = false;
      if (ok) return r;
    }
    throw new IllegalStateException("no closed header");
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
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
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
      try { return s.isEmpty() ? 0 : Double.parseDouble(s); } catch (Exception e) { return 0; }
    }
    return 0;
  }

  static Long parseLong(String s) {
    if (s == null) return null;
    try { return Long.parseLong(s.trim().replaceAll("[^0-9]", "")); } catch (Exception e) { return null; }
  }
}

