package com.smartbox.investory.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Connection details and cleanup lifecycle for one isolated test-worker database. */
public final class WorkerDatabase implements AutoCloseable {

  private final String databaseName;
  private final String jdbcUrl;
  private final String username;
  private final String password;
  private final boolean cleanupEnabled;
  private final AtomicBoolean closed = new AtomicBoolean();

  WorkerDatabase(
      String databaseName,
      String jdbcUrl,
      String username,
      String password,
      boolean cleanupEnabled) {
    this.databaseName = databaseName;
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.password = password;
    this.cleanupEnabled = cleanupEnabled;
  }

  public String databaseName() {
    return databaseName;
  }

  public String jdbcUrl() {
    return jdbcUrl;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }

  public Connection openConnection() throws SQLException {
    if (closed.get())
      throw new IllegalStateException("Worker database is already closed: " + databaseName);
    return DriverManager.getConnection(jdbcUrl, username, password);
  }

  @Override
  public void close() {
    if (!cleanupEnabled || !closed.compareAndSet(false, true)) return;
    SharedPostgres.dropDatabase(databaseName);
  }
}
