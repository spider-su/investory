import java.io.InputStream;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.*;
import org.apache.poi.ss.usermodel.*;

/**
 * L3 staging reconciliation runner. Implements checkpoints C0 (import completeness)
 * and C1 (files -> cash_operations) from docs/quality/reconciliation.md.
 *
 * Args: sourceDir jdbcUrl user pass
 * Exit code is non-zero if any checkpoint fails.
 */
public class ReconRunner {

  public static void main(String[] args) throws Exception {
    if (args.length < 4) {
      System.err.println("usage: ReconRunner <sourceDir> <jdbcUrl> <user> <pass>");
      System.exit(2);
    }
    Path sourceDir = Paths.get(args[0]);
    boolean ok;
    try (Connection conn = DriverManager.getConnection(args[1], args[2], args[3])) {
      List<Path> zips = new ArrayList<>();
      List<Path> csvs = new ArrayList<>();
      try (DirectoryStream<Path> ds = Files.newDirectoryStream(sourceDir)) {
        for (Path p : ds) {
          String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
          if (n.endsWith(".zip")) zips.add(p);
          else if (n.endsWith(".csv")) csvs.add(p);
        }
      }
      zips.sort(Comparator.comparing(p -> p.getFileName().toString()));
      csvs.sort(Comparator.comparing(p -> p.getFileName().toString()));

      Map<String, ImportRow> history = loadHistory(conn);
      boolean c0 = runC0(zips, csvs, history);
      boolean c1 = runC1(conn, zips, csvs, history);
      ok = c0 && c1;
    }
    System.out.println();
    System.out.println(ok ? "OVERALL: PASS" : "OVERALL: FAIL");
    System.exit(ok ? 0 : 1);
  }

  // ---------- C0 ----------

  static boolean runC0(List<Path> zips, List<Path> csvs, Map<String, ImportRow> history) {
    System.out.println("==================== C0  import completeness ====================");
    System.out.printf("%-62s %-9s %-10s %8s %8s%n", "FILE", "IMPORTED", "STATUS", "APPLIED", "FAILED");
    boolean pass = true;
    List<Path> all = new ArrayList<>();
    all.addAll(zips);
    all.addAll(csvs);
    for (Path p : all) {
      String name = p.getFileName().toString();
      ImportRow r = history.get(name);
      boolean imported = r != null && "COMPLETED".equalsIgnoreCase(r.status) && r.rowsFailed == 0;
      if (!imported) {
        pass = false;
      }
      System.out.printf(
          "%-62s %-9s %-10s %8s %8s%n",
          truncate(name, 62),
          imported ? "yes" : "NO",
          r == null ? "-" : r.status,
          r == null ? "-" : String.valueOf(r.rowsApplied),
          r == null ? "-" : String.valueOf(r.rowsFailed));
    }
    System.out.println("C0: " + (pass ? "PASS" : "FAIL"));
    System.out.println();
    return pass;
  }

  // ---------- C1 ----------

  static boolean runC1(Connection conn, List<Path> zips, List<Path> csvs, Map<String, ImportRow> history)
      throws Exception {
    System.out.println("==================== C1  files -> cash_operations ====================");
    // Expected per account, aggregated across imported zips only.
    Map<Long, Agg> expected = new TreeMap<>();
    for (Path zip : zips) {
      ImportRow r = history.get(zip.getFileName().toString());
      boolean imported = r != null && "COMPLETED".equalsIgnoreCase(r.status) && r.rowsFailed == 0;
      if (!imported) continue;
      parseZip(zip, expected);
    }
    // IBKR expected: money-conservation model (Net Amount) per account, from imported CSVs.
    Map<Long, Agg> ibkr = new TreeMap<>();
    for (Path csv : csvs) {
      ImportRow r = history.get(csv.getFileName().toString());
      boolean imported = r != null && "COMPLETED".equalsIgnoreCase(r.status) && r.rowsFailed == 0;
      if (!imported) continue;
      parseIbkrCsv(csv, ibkr);
    }
    Map<Long, Agg> db = loadDbCashByAccount(conn);

    Set<Long> accounts = new TreeSet<>();
    accounts.addAll(expected.keySet());
    accounts.addAll(ibkr.keySet());
    accounts.addAll(db.keySet());

    System.out.printf("%-11s %6s %6s %14s %14s %-10s %-12s %-12s%n",
        "ACCOUNT", "fRows", "dRows", "fSum", "dSum", "rows", "sum", "maxDate");
    boolean pass = true;
    for (Long acc : accounts) {
      if (ibkr.containsKey(acc) && !expected.containsKey(acc)) {
        // IBKR: assert money conservation (sum). Row count is informational (importer may
        // expand a transaction into several cash operations; here it is 1:1).
        Agg f = ibkr.get(acc);
        Agg d = db.getOrDefault(acc, Agg.empty());
        boolean sumOk = Math.abs(f.sum - d.sum) < 0.01;
        boolean rowsOk = f.rows == d.rows;
        pass = pass && sumOk;
        System.out.printf("%-11d %6d %6d %14.2f %14.2f %-10s %-12s %-12s%n",
            acc, f.rows, d.rows, f.sum, d.sum,
            rowsOk ? "OK" : "info(exp?)",
            sumOk ? "OK" : String.format("d=%.2f", d.sum - f.sum),
            "IBKR");
        continue;
      }
      if (!expected.containsKey(acc)) {
        // DB-only account: not produced by any parsed XTB file (e.g. IBKR). Out of C1
        // (XTB) scope; reported for visibility but does not affect pass/fail. IBKR C1
        // is a documented follow-up.
        Agg d0 = db.getOrDefault(acc, Agg.empty());
        System.out.printf("%-11d %6s %6d %14s %14.2f %-10s%n",
            acc, "-", d0.rows, "-", d0.sum, "SKIP(non-XTB)");
        continue;
      }
      Agg f = expected.getOrDefault(acc, Agg.empty());
      Agg d = db.getOrDefault(acc, Agg.empty());
      boolean rowsOk = f.rows == d.rows;
      boolean sumOk = Math.abs(f.sum - d.sum) < 0.01;
      // maxDate only meaningful when both sides have data.
      boolean dateOk = f.rows == 0 || d.maxDate == null || f.maxDate == null || f.maxDate.equals(d.maxDate);
      boolean rowPass = rowsOk && sumOk && dateOk;
      pass = pass && rowPass;
      System.out.printf("%-11d %6d %6d %14.2f %14.2f %-10s %-12s %-12s%n",
          acc, f.rows, d.rows, f.sum, d.sum,
          rowsOk ? "OK" : "MISMATCH",
          sumOk ? "OK" : String.format("d=%.2f", d.sum - f.sum),
          dateOk ? "OK" : (f.maxDate + "/" + d.maxDate));
      if (!rowPass) {
        // per-operation diff to localise the mismatch
        Set<String> ops = new TreeSet<>();
        ops.addAll(f.byOp.keySet());
        ops.addAll(d.byOp.keySet());
        for (String op : ops) {
          long fc = f.byOp.getOrDefault(op, new double[] {0, 0})[0] == 0 ? 0 : (long) f.byOp.get(op)[0];
          double[] fv = f.byOp.getOrDefault(op, new double[] {0, 0});
          double[] dv = d.byOp.getOrDefault(op, new double[] {0, 0});
          if ((long) fv[0] != (long) dv[0] || Math.abs(fv[1] - dv[1]) >= 0.01) {
            System.out.printf("      op %-24s file(%d, %.2f)  db(%d, %.2f)%n",
                op, (long) fv[0], fv[1], (long) dv[0], dv[1]);
          }
        }
      }
    }
    System.out.println("C1: " + (pass ? "PASS" : "FAIL"));
    System.out.println();
    return pass;
  }

  static void parseZip(Path zip, Map<Long, Agg> expected) throws Exception {
    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
      ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        if (e.isDirectory() || !e.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) continue;
        // POI needs a full stream; copy the entry bytes.
        byte[] bytes = zis.readAllBytes();
        try (Workbook wb = WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
          Sheet sheet = wb.getSheet("Cash Operations");
          if (sheet == null) continue;
          Long account = parseLong(readHeaderValue(sheet, "AccountEntity number"));
          if (account == null) continue;
          Agg agg = expected.computeIfAbsent(account, k -> Agg.empty());
          accumulate(sheet, agg);
        }
      }
    }
  }

  static void parseIbkrCsv(Path csv, Map<Long, Agg> ibkr) throws Exception {
    // AccountEntity id from the U<digits> filename; the CSV "AccountEntity" column is a name.
    Long account = parseLong(csv.getFileName().toString().replaceFirst("(?i)^u", "").split("\\.")[0]);
    if (account == null) return;
    Agg agg = ibkr.computeIfAbsent(account, k -> Agg.empty());
    for (String line : Files.readAllLines(csv)) {
      if (!line.startsWith("Transaction History,Data,")) continue;
      // Net Amount is the last field; robust to unquoted commas in Description.
      int comma = line.lastIndexOf(',');
      if (comma < 0 || comma + 1 >= line.length()) continue;
      String net = line.substring(comma + 1).trim();
      if (net.isEmpty() || "-".equals(net)) {
        agg.rows++;
        continue;
      }
      try {
        agg.sum += Double.parseDouble(net);
        agg.rows++;
      } catch (NumberFormatException ignore) {
        // header-like or malformed row
      }
    }
  }

  static void accumulate(Sheet sheet, Agg agg) {
    int headerRow = findHeader(sheet);
    Row hr = sheet.getRow(headerRow);
    Map<String, Integer> col = new HashMap<>();
    for (int c = hr.getFirstCellNum(); c < hr.getLastCellNum(); c++) {
      String v = str(hr.getCell(c));
      if (v != null && !v.isBlank()) col.put(v.trim(), c);
    }
    Integer typeIdx = col.get("Type");
    Integer timeIdx = col.get("Time");
    Integer amtIdx = col.get("Amount");
    Integer commentIdx = col.get("Comment");
    for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
      Row row = sheet.getRow(r);
      if (row == null) continue;
      String datePart = timeIdx == null ? null : datePart(row.getCell(timeIdx));
      if (datePart == null) continue; // footer/total row (no parseable Time)
      double amt = amtIdx == null ? 0 : num(row.getCell(amtIdx));
      agg.rows++;
      agg.sum += amt;
      if (agg.maxDate == null || datePart.compareTo(agg.maxDate) > 0) agg.maxDate = datePart;
      String typeText = typeIdx == null ? null : str(row.getCell(typeIdx));
      String comment = commentIdx == null ? null : str(row.getCell(commentIdx));
      String op = mapType(typeText, comment);
      double[] slot = agg.byOp.computeIfAbsent(op, k -> new double[2]);
      slot[0] += 1;
      slot[1] += amt;
    }
  }

  // Replicates CashOperationType.fromString: type cell first, comment fallback on UNKNOWN.
  static String mapType(String typeText, String comment) {
    String t = fromString(typeText);
    if ("UNKNOWN".equals(t) && comment != null) t = fromString(comment);
    return t;
  }

  static String fromString(String value) {
    if (value == null) return "UNKNOWN";
    String n = value
        .toLowerCase(Locale.ROOT)
        .replace('\u2010', '-').replace('\u2011', '-').replace('\u2012', '-')
        .replace('\u2013', '-').replace('\u2014', '-').replace('\u2212', '-')
        .replace('\u00a0', ' ')
        .replaceAll("\\s+", " ").trim();
    if (n.startsWith("free-funds interest tax")) return "FREE_FUNDS_INTEREST_TAX";
    if (n.startsWith("free-funds interest")) return "FREE_FUNDS_INTEREST";
    switch (n) {
      case "sec fee": return "SEC_FEE";
      case "subaccount transfer": return "SUBACCOUNT_TRANSFER";
      case "stock purchase": return "STOCK_PURCHASE";
      case "stock sell":
      case "stock sale": return "STOCK_SELL";
      case "close trade": return "CLOSE_TRADE";
      case "dividend": return "DIVIDEND";
      case "commission": return "COMMISSION";
      case "transfer": return "TRANSFER";
      case "withdrawal":
      case "withdraw": return "WITHDRAWAL";
      case "deposit":
      case "ike deposit": return "DEPOSIT";
      case "withholding tax": return "WITHHOLDING_TAX";
      case "swap": return "SWAP";
      case "rollover": return "ROLLOVER";
      case "correction": return "CORRECTION";
      case "stamp duty": return "STAMP_DUTY";
      case "tax iftt": return "TRANSACTION_TAX";
      default: return "UNKNOWN";
    }
  }

  // ---------- DB ----------

  static Map<String, ImportRow> loadHistory(Connection conn) throws SQLException {
    Map<String, ImportRow> map = new HashMap<>();
    String sql =
        "select file_name, status, coalesce(rows_applied,0) ra, coalesce(rows_failed,0) rf "
            + "from investory.import_history";
    try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) {
        ImportRow r = new ImportRow();
        r.status = rs.getString("status");
        r.rowsApplied = rs.getLong("ra");
        r.rowsFailed = rs.getLong("rf");
        // keep the latest COMPLETED if duplicates
        ImportRow prev = map.get(rs.getString("file_name"));
        if (prev == null || "COMPLETED".equalsIgnoreCase(r.status)) {
          map.put(rs.getString("file_name"), r);
        }
      }
    }
    return map;
  }

  static Map<Long, Agg> loadDbCashByAccount(Connection conn) throws SQLException {
    Map<Long, Agg> map = new TreeMap<>();
    String sql =
        "select account_id, operation::text op, count(*) c, sum(amount) s, max(date::date) mx "
            + "from investory.cash_operations group by account_id, operation::text";
    try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) {
        long acc = rs.getLong("account_id");
        Agg agg = map.computeIfAbsent(acc, k -> Agg.empty());
        long c = rs.getLong("c");
        double sum = rs.getDouble("s");
        String mx = rs.getString("mx");
        agg.rows += c;
        agg.sum += sum;
        if (agg.maxDate == null || (mx != null && mx.compareTo(agg.maxDate) > 0)) agg.maxDate = mx;
        agg.byOp.put(rs.getString("op"), new double[] {c, sum});
      }
    }
    return map;
  }

  // ---------- POI helpers ----------

  static int findHeader(Sheet sheet) {
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
      if (t && ti && a) return r;
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

  static String datePart(Cell cell) {
    if (cell == null) return null;
    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
      java.util.Date d = cell.getDateCellValue();
      return new java.text.SimpleDateFormat("yyyy-MM-dd").format(d);
    }
    String s = str(cell);
    if (s == null) return null;
    s = s.trim();
    if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') return s.substring(0, 10);
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

  static Long parseLong(String s) {
    if (s == null) return null;
    try { return Long.parseLong(s.trim().replaceAll("[^0-9]", "")); } catch (Exception e) { return null; }
  }

  static String truncate(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "~"; }

  // ---------- structs ----------

  static final class ImportRow {
    String status;
    long rowsApplied;
    long rowsFailed;
  }

  static final class Agg {
    long rows;
    double sum;
    String maxDate;
    Map<String, double[]> byOp = new TreeMap<>();
    static Agg empty() { return new Agg(); }
  }
}


