import java.sql.DriverManager;

public class TempDbInventory {
  public static void main(String[] args) throws Exception {
    Class.forName("org.postgresql.Driver");
    try (var c = DriverManager.getConnection(System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
        var s = c.createStatement()) {
      try (var r = s.executeQuery("select 'relation|' || relkind::text || '|' || relname from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='investory' and relkind in ('v','m') order by relkind, relname")) {
        while (r.next()) System.out.println(r.getString(1));
      }
      try (var r = s.executeQuery("select 'function|' || p.proname from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='investory' order by p.proname, p.oid")) {
        while (r.next()) System.out.println(r.getString(1));
      }
      try (var r = s.executeQuery("select 'trigger|' || t.tgname || '|' || c.relname from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='investory' and not t.tgisinternal order by t.tgname")) {
        while (r.next()) System.out.println(r.getString(1));
      }
    }
  }
}
