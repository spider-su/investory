package com.smartbox.investory.testsupport;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.testcontainers.containers.PostgreSQLContainer;

/** One PostgreSQL container per test JVM, with isolated databases for parallel workers. */
public final class SharedPostgres {

  static final String IMAGE_PROPERTY = "investory.test.postgres.image";
  static final String PREFIX_PROPERTY = "investory.test.database.prefix";
  static final String RUN_ID_PROPERTY = "investory.test.run-id";
  static final String WORKER_ID_PROPERTY = "investory.test.worker-id";
  static final String CLEANUP_PROPERTY = "investory.test.database.cleanup";
  static final String WORKERS_PROPERTY = "investory.test.workers";

  private static final String DEFAULT_IMAGE = "postgres:17-bookworm";
  private static final String USERNAME = "investory";
  private static final String PASSWORD = "investory";
  private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,62}");
  private static final Map<String, WorkerDatabase> DATABASES = new ConcurrentHashMap<>();
  private static final AtomicInteger START_COUNT = new AtomicInteger();
  private static final AtomicBoolean CLOSED = new AtomicBoolean();

  private SharedPostgres() {}

  public static WorkerDatabase workerDatabase() {
    return database(null);
  }

  /**
   * Returns a database for this worker, optionally isolated further for a destructive test scope.
   */
  public static WorkerDatabase database(String scope) {
    String name = databaseName(scope);
    return DATABASES.computeIfAbsent(name, SharedPostgres::createDatabase);
  }

  public static PostgreSQLContainer<?> container() {
    return Holder.POSTGRES;
  }

  public static int configuredWorkerCount() {
    int workers = Integer.getInteger(WORKERS_PROPERTY, 1);
    if (workers < 1) {
      throw new IllegalArgumentException(WORKERS_PROPERTY + " must be at least 1");
    }
    return workers;
  }

  public static int containerStartCount() {
    container();
    return START_COUNT.get();
  }

  /**
   * Resolves the deterministic name without starting the container. Useful for diagnostics/tests.
   */
  public static String databaseNameFor(String workerId, String scope) {
    String prefix = component(System.getProperty(PREFIX_PROPERTY, "it"));
    String runId =
        component(
            firstNonBlank(
                System.getProperty(RUN_ID_PROPERTY), localRunId(), System.getenv("GITHUB_RUN_ID")));
    String worker = component(firstNonBlank(workerId, "1"));
    String suffix = scope == null || scope.isBlank() ? "" : "_" + component(scope);
    return shorten(prefix + "_" + runId + "_" + worker + suffix);
  }

  public static void shutdown() {
    if (!CLOSED.compareAndSet(false, true)) return;

    List<WorkerDatabase> databases = new ArrayList<>(DATABASES.values());
    databases.sort(Comparator.comparing(WorkerDatabase::databaseName).reversed());
    for (WorkerDatabase database : databases) {
      try {
        database.close();
      } catch (RuntimeException cleanupFailure) {
        System.err.println(
            "Failed to clean PostgreSQL test database "
                + database.databaseName()
                + ": "
                + cleanupFailure.getMessage());
      }
    }
    if (Holder.POSTGRES.isRunning()) Holder.POSTGRES.stop();
  }

  static void dropDatabase(String databaseName) {
    validateIdentifier(databaseName);
    PostgreSQLContainer<?> postgres = container();
    try (Connection connection = adminConnection();
        Statement statement = connection.createStatement()) {
      try (var prepared =
          connection.prepareStatement(
              "SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                  + "WHERE datname = ? AND pid <> pg_backend_pid()")) {
        prepared.setString(1, databaseName);
        prepared.execute();
      }
      statement.execute("DROP DATABASE IF EXISTS " + quoteIdentifier(databaseName));
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Failed to drop PostgreSQL test database " + databaseName, exception);
    }
  }

  private static WorkerDatabase createDatabase(String databaseName) {
    validateIdentifier(databaseName);
    PostgreSQLContainer<?> postgres = container();
    try (Connection connection = adminConnection()) {
      connection.setAutoCommit(true);
      try (var lock = connection.prepareStatement("SELECT pg_advisory_lock(hashtext(?))")) {
        lock.setString(1, databaseName);
        lock.execute();
      }
      try {
        if (!databaseExists(connection, databaseName)) {
          try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
          }
        }
      } finally {
        try (var unlock = connection.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))")) {
          unlock.setString(1, databaseName);
          unlock.execute();
        }
      }
      return new WorkerDatabase(
          databaseName,
          jdbcUrl(postgres, databaseName),
          postgres.getUsername(),
          postgres.getPassword(),
          Boolean.parseBoolean(System.getProperty(CLEANUP_PROPERTY, "true")));
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Failed to create PostgreSQL test database "
              + databaseName
              + " using administrative database "
              + postgres.getDatabaseName(),
          exception);
    }
  }

  private static boolean databaseExists(Connection connection, String databaseName)
      throws SQLException {
    try (var query = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
      query.setString(1, databaseName);
      try (ResultSet result = query.executeQuery()) {
        return result.next();
      }
    }
  }

  private static Connection adminConnection() throws SQLException {
    PostgreSQLContainer<?> postgres = container();
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private static String jdbcUrl(PostgreSQLContainer<?> postgres, String databaseName) {
    String adminDatabase = "/" + postgres.getDatabaseName();
    int query = postgres.getJdbcUrl().indexOf('?');
    String base = query < 0 ? postgres.getJdbcUrl() : postgres.getJdbcUrl().substring(0, query);
    String suffix = query < 0 ? "" : postgres.getJdbcUrl().substring(query);
    if (!base.endsWith(adminDatabase)) {
      throw new IllegalStateException("Unexpected PostgreSQL administrative JDBC URL: " + base);
    }
    return base.substring(0, base.length() - adminDatabase.length()) + "/" + databaseName + suffix;
  }

  private static String databaseName(String scope) {
    return databaseNameFor(
        firstNonBlank(
            System.getProperty(WORKER_ID_PROPERTY),
            unresolvedProperty(System.getProperty("surefire.forkNumber"))
                ? null
                : System.getProperty("surefire.forkNumber"),
            System.getenv("TEST_WORKER_INDEX"),
            "1"),
        scope);
  }

  private static boolean unresolvedProperty(String value) {
    return value != null && value.contains("${");
  }

  private static String localRunId() {
    String runtime = ManagementFactory.getRuntimeMXBean().getName();
    String process = runtime.contains("@") ? runtime.substring(0, runtime.indexOf('@')) : runtime;
    return process + "_" + ManagementFactory.getRuntimeMXBean().getStartTime();
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) if (value != null && !value.isBlank()) return value;
    throw new IllegalArgumentException("At least one database-name component is required");
  }

  private static String component(String value) {
    String normalized =
        value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    if (normalized.isBlank()) return "x";
    if (!Character.isLetter(normalized.charAt(0))) normalized = "w_" + normalized;
    return normalized;
  }

  private static String shorten(String value) {
    if (value.length() <= 63) return value;
    String hash = Integer.toUnsignedString(value.hashCode(), 36);
    return value.substring(0, 62 - hash.length()) + "_" + hash;
  }

  private static void validateIdentifier(String value) {
    if (!IDENTIFIER.matcher(value).matches()) {
      throw new IllegalArgumentException("Unsafe PostgreSQL database identifier: " + value);
    }
  }

  private static String quoteIdentifier(String value) {
    validateIdentifier(value);
    return '"' + value.replace("\"", "\"\"") + '"';
  }

  private static final class Holder {
    private static final PostgreSQLContainer<?> POSTGRES = start();

    private static PostgreSQLContainer<?> start() {
      PostgreSQLContainer<?> postgres =
          new PostgreSQLContainer<>(System.getProperty(IMAGE_PROPERTY, DEFAULT_IMAGE))
              .withDatabaseName("postgres")
              .withUsername(USERNAME)
              .withPassword(PASSWORD);
      postgres.start();
      START_COUNT.incrementAndGet();
      Runtime.getRuntime()
          .addShutdownHook(new Thread(SharedPostgres::shutdown, "it-postgres-stop"));
      return postgres;
    }
  }
}
