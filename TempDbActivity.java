import java.sql.DriverManager;

public class TempDbActivity {
  public static void main(String[] args) throws Exception {
    Class.forName("org.postgresql.Driver");
    try (var c = DriverManager.getConnection(System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
        var s = c.createStatement();
        var r = s.executeQuery("select pid, state, wait_event_type, wait_event, left(query, 180) from pg_stat_activity where datname=current_database() and pid <> pg_backend_pid()")) {
      while (r.next()) System.out.println(r.getLong(1) + "|" + r.getString(2) + "|" + r.getString(3) + "|" + r.getString(4) + "|" + r.getString(5));
    }
  }
}
