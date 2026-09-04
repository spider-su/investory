import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

public class TempRecreateAll {
  public static void main(String[] args) throws Exception {
    Class.forName("org.postgresql.Driver");
    try (var c = DriverManager.getConnection(System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
      apply(c, "V01.001 functions", read("V01.001__functions.sql"));
      apply(c, "V01.002 triggers", read("V01.002__triggers.sql"));
      apply(c, "V01.005 portfolio objects", read("V01.005__portfolio_views.sql"));
      apply(c, "V01.006 reconciliation objects", read("V01.006__reconciliation_views.sql"));
      String v008 = read("V01.008__long_term_assets.sql");
      apply(c, "V01.007 audit objects", read("V01.007__persisted_system_audit.sql"));
      apply(c, "V01.008 long-term objects", v008.substring(v008.indexOf("CREATE OR REPLACE FUNCTION investory.assert_long_term_subtype_consistency()")));
    }
  }

  private static String read(String name) throws Exception {
    return Files.readString(Path.of("app/src/main/resources/sql/migration", name));
  }

  private static void apply(java.sql.Connection c, String label, String sql) throws Exception {
    c.setAutoCommit(false);
    try (var s = c.createStatement()) {
      s.execute(sql);
      c.commit();
      System.out.println(label + " committed");
    } catch (Exception e) {
      c.rollback();
      throw e;
    }
  }
}
