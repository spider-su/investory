package com.smartbox.investory.infrastructure;

import com.smartbox.investory.testsupport.SharedPostgres;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;

final class MigrationTestDatabase {

  private MigrationTestDatabase() {}

  static WorkerDatabase open(String scope) {
    return SharedPostgres.database(scope);
  }

  static void migrate(WorkerDatabase database) {
    assertDisposable(database);
    flyway(database).clean();
    flyway(database).migrate();
  }

  static Connection connection(WorkerDatabase database) throws SQLException {
    return DriverManager.getConnection(
        database.jdbcUrl(), database.username(), database.password());
  }

  static Flyway flyway(WorkerDatabase database) {
    return Flyway.configure()
        .cleanDisabled(false)
        .dataSource(database.jdbcUrl(), database.username(), database.password())
        .schemas("investory")
        .defaultSchema("investory")
        .createSchemas(true)
        .locations("classpath:sql/migration")
        .load();
  }

  static void assertDisposable(WorkerDatabase database) {
    String url = database.jdbcUrl();
    if (url == null
        || !url.matches("^jdbc:postgresql://.*/" + database.databaseName() + "(?:\\?.*)?$")) {
      throw new IllegalStateException(
          "Refusing to run migration test against non-disposable database: " + url);
    }
  }

  static int singleInt(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      if (!result.next()) {
        throw new SQLException("Expected one row for query: " + sql);
      }
      return result.getInt(1);
    }
  }

  static boolean exists(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      return result.next();
    }
  }

  static int migrationScriptCount() throws SQLException {
    try (Stream<Path> files = Files.list(Path.of("src", "main", "resources", "sql", "migration"))) {
      return (int)
          files
              .map(Path::getFileName)
              .map(Path::toString)
              .filter(name -> name.matches("^V\\d+\\.\\d+__.*\\.sql$"))
              .count();
    } catch (java.io.IOException exception) {
      throw new SQLException("Cannot list Flyway migrations", exception);
    }
  }
}
