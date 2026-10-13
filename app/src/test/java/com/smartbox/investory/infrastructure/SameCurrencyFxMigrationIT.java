package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

class SameCurrencyFxMigrationIT {

  @Test
  void upgradePreservesFxAndDailyValuesAndSkipsIdentityRateScans() throws Exception {
    try (var database = MigrationTestDatabase.open("same_currency_fx_upgrade")) {
      MigrationTestDatabase.migrateTo(database, "01.013");
      try (Connection connection = MigrationTestDatabase.connection(database);
          Statement statement = connection.createStatement()) {
        statement.execute("SET search_path TO investory, public");
        statement.execute(
            """
            INSERT INTO exchange_rates(rate_date,base,to_currency,rate,source,method)
            VALUES ('2035-01-01','USD','PLN',4,'TEST','OBSERVED'),
                   ('2035-01-01','EUR','USD',2,'TEST','OBSERVED'),
                   ('2035-01-02','USD','PLN',5,'TEST','INTERPOLATED'),
                   ('2035-01-01','PLN','USD',0.2,'TEST','OBSERVED'),
                   ('2035-01-03','PLN','USD',0.1,'TEST','OBSERVED')
            """);
        statement.execute(
            """
            CREATE TEMP TABLE fx_cases(label,valuation_date,source_currency,target_currency) AS
            VALUES ('same',DATE '2035-01-01','USD'::varchar,'USD'::varchar),
                   ('same_null_date',NULL::date,'USD','USD'),
                   ('direct',DATE '2035-01-01','USD','PLN'),
                   ('inverse',DATE '2035-01-01','USD','EUR'),
                   ('estimated',DATE '2035-01-02','USD','PLN'),
                   ('carry_forward',DATE '2035-01-04','USD','PLN'),
                   ('newer_inverse',DATE '2035-01-03','USD','PLN'),
                   ('missing',DATE '1900-01-01','EUR','PLN'),
                   ('null_source',DATE '2035-01-01',NULL,'USD'),
                   ('null_target',DATE '2035-01-01','USD',NULL),
                   ('both_null',DATE '2035-01-01',NULL,NULL)
            """);
        statement.execute("CREATE TEMP TABLE fx_before AS " + resolvedCases());
        statement.execute(
            """
            INSERT INTO accounts(external_account_id,currency,name,owner,portfolio_id)
            VALUES ('fx-upgrade-usd','USD','FX USD','test',1),
                   ('fx-upgrade-pln','PLN','FX PLN','test',1),
                   ('fx-upgrade-missing','EUR','FX missing','test',1)
            """);
        statement.execute(
            """
            INSERT INTO account_daily(account_id,snapshot_date,valuation_currency,equity,market_value)
            SELECT a.id,v.day,a.currency,v.equity,v.equity
            FROM accounts a CROSS JOIN
              (VALUES (DATE '2035-01-01',100::numeric),
                      (DATE '2035-01-03',110::numeric)) v(day,equity)
            WHERE a.external_account_id IN ('fx-upgrade-usd','fx-upgrade-pln')
            UNION ALL
            SELECT a.id,DATE '1900-01-01',a.currency,10,10
            FROM accounts a WHERE a.external_account_id='fx-upgrade-missing'
            """);
        statement.execute(
            "CREATE TEMP TABLE daily_before AS SELECT * FROM app_v_portfolio_performance_daily");
        assertTrue(
            MigrationTestDatabase.singleInt(statement, "SELECT count(*) FROM daily_before") > 0);

        MigrationTestDatabase.flyway(database).migrate();
        assertEquals(0, MigrationTestDatabase.flyway(database).info().pending().length);
        assertEquals(
            0,
            MigrationTestDatabase.singleInt(
                statement,
                "WITH after AS ("
                    + resolvedCases()
                    + "), difference AS ((TABLE fx_before EXCEPT ALL TABLE after) "
                    + "UNION ALL (TABLE after EXCEPT ALL TABLE fx_before)) "
                    + "SELECT count(*) FROM difference"));
        assertEquals(
            0,
            MigrationTestDatabase.singleInt(
                statement,
                """
                WITH difference AS (
                  (TABLE daily_before EXCEPT ALL TABLE app_v_portfolio_performance_daily)
                  UNION ALL
                  (TABLE app_v_portfolio_performance_daily EXCEPT ALL TABLE daily_before))
                SELECT count(*) FROM difference
                """));
        try (ResultSet result =
            statement.executeQuery(
                "SELECT fx_rate_to_target,conversion_status FROM "
                    + "resolve_fx_rate(DATE '2035-01-01','USD','USD')")) {
          assertTrue(result.next());
          assertEquals(0, java.math.BigDecimal.ONE.compareTo(result.getBigDecimal(1)));
          assertEquals("SAME_CURRENCY", result.getString(2));
        }
        try (ResultSet result =
            statement.executeQuery(
                "EXPLAIN (ANALYZE, FORMAT XML) SELECT * FROM "
                    + "resolve_fx_rate(DATE '2035-01-01','USD','USD')")) {
          assertTrue(result.next());
          var document =
              DocumentBuilderFactory.newInstance()
                  .newDocumentBuilder()
                  .parse(new InputSource(new StringReader(result.getString(1))));
          NodeList scans =
              (NodeList)
                  XPathFactory.newInstance()
                      .newXPath()
                      .evaluate(
                          "//Plan[Relation-Name='exchange_rates']/Actual-Loops",
                          document,
                          XPathConstants.NODESET);
          for (int i = 0; i < scans.getLength(); i++) {
            assertEquals(
                "0", scans.item(i).getTextContent(), "identity conversion must not scan rates");
          }
        }
        // Prefer the intended access path even for this tiny fixture; a large live rate history
        // must need only one observation from each direction, rather than sorting all prior rates.
        statement.execute("SET enable_seqscan=off");
        try (ResultSet result =
            statement.executeQuery(
                "EXPLAIN (ANALYZE, FORMAT XML) SELECT * FROM "
                    + "resolve_fx_rate(DATE '2035-01-04','USD','PLN')")) {
          assertTrue(result.next());
          String plan = result.getString(1);
          assertTrue(plan.contains("<Index-Name>"));
          var document =
              DocumentBuilderFactory.newInstance()
                  .newDocumentBuilder()
                  .parse(new InputSource(new StringReader(plan)));
          NodeList rows =
              (NodeList)
                  XPathFactory.newInstance()
                      .newXPath()
                      .evaluate(
                          "//Plan[Relation-Name='exchange_rates']/Actual-Rows",
                          document,
                          XPathConstants.NODESET);
          assertEquals(2, rows.getLength());
          for (int i = 0; i < rows.getLength(); i++) {
            assertTrue(Double.parseDouble(rows.item(i).getTextContent()) <= 1);
          }
        }
      }
    }
  }

  private static String resolvedCases() {
    return """
        SELECT c.label,r.* FROM fx_cases c CROSS JOIN LATERAL
        resolve_fx_rate(c.valuation_date,c.source_currency,c.target_currency) r
        """;
  }
}
