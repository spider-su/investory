package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.testsupport.WorkerDatabase;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class MarketRadarLegacySchemaMigrationIT {

  private static final WorkerDatabase DATABASE =
      MigrationTestDatabase.open("market_radar_legacy_schema");

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void upgradesExistingPartialSnapshotTable() throws Exception {
    MigrationTestDatabase.assertDisposable(DATABASE);
    MigrationTestDatabase.flyway(DATABASE).clean();
    MigrationTestDatabase.migrateTo(DATABASE, "01.046");

    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          create table investory.market_radar_snapshot (
              id bigserial primary key,
              symbol varchar(64) not null,
              observed_on date not null,
              state varchar(32) not null,
              close_price double precision not null,
              created_at timestamp with time zone not null default current_timestamp
          )
          """);
      statement.execute(
          """
          insert into investory.market_radar_snapshot
              (symbol, observed_on, state, close_price)
          values ('TEST', date '2026-10-02', 'NORMAL', 100)
          """);
    }

    MigrationTestDatabase.flyway(DATABASE).migrate();

    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      assertEquals(
          11,
          MigrationTestDatabase.singleInt(
              statement,
              "select count(*) from information_schema.columns "
                  + "where table_schema='investory' and table_name='market_radar_snapshot' "
                  + "and column_name in ('id','symbol','observed_on','state','close_price',"
                  + "'return_20d','return_60d','relative_volume_20d','distance_sma50','rsi14','reasons')"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "select count(*) from investory.market_radar_snapshot "
                  + "where symbol='TEST' and observed_on=date '2026-10-02'"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "select count(*) from pg_constraint "
                  + "where conrelid='investory.market_radar_snapshot'::regclass "
                  + "and conname='uk_market_radar_snapshot_symbol_date'"));
    }
  }
}
