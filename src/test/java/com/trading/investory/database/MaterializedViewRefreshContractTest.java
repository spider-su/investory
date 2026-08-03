package com.trading.investory.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class MaterializedViewRefreshContractTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("investory_mv_test")
                    .withUsername("investory")
                    .withPassword("investory");

    @BeforeAll
    static void migrateDatabase() {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:sql/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void refreshProcedureCoversEveryMaterializedViewInDependencyOrder() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CALL investory.refresh_reporting_materialized_views()");

            String latestRunId;
            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT refresh_run_id::text "
                                    + "FROM investory.materialized_view_refresh_history "
                                    + "ORDER BY started_at DESC, id DESC LIMIT 1")) {
                assertTrue(result.next());
                latestRunId = result.getString(1);
            }

            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT "
                                    + "(SELECT count(*) FROM pg_matviews WHERE schemaname = 'investory') AS mv_count, "
                                    + "count(*) AS history_count, "
                                    + "count(*) FILTER (WHERE status = 'COMPLETED') AS completed_count, "
                                    + "count(*) FILTER (WHERE finished_at IS NULL) AS missing_finished_at "
                                    + "FROM investory.materialized_view_refresh_history "
                                    + "WHERE refresh_run_id = '"
                                    + latestRunId
                                    + "'::uuid")) {
                assertTrue(result.next());
                assertEquals(result.getLong("mv_count"), result.getLong("history_count"));
                assertEquals(result.getLong("mv_count"), result.getLong("completed_count"));
                assertEquals(0, result.getLong("missing_finished_at"));
            }

            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT count(*) "
                                    + "FROM investory.reporting_materialized_view_dependencies dependent "
                                    + "CROSS JOIN LATERAL unnest(dependent.depends_on) dependency_name "
                                    + "JOIN investory.reporting_materialized_view_dependencies dependency "
                                    + "  ON dependency.materialized_view = dependency_name "
                                    + "WHERE dependency.dependency_level >= dependent.dependency_level")) {
                assertTrue(result.next());
                assertEquals(0, result.getLong(1), "Dependencies must refresh before dependents");
            }

            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT count(*) "
                                    + "FROM investory.reporting_materialized_view_refresh_status "
                                    + "WHERE last_refresh_status <> 'COMPLETED' "
                                    + "   OR last_refresh_finished_at IS NULL")) {
                assertTrue(result.next());
                assertEquals(0, result.getLong(1));
            }
        }
    }

    @Test
    void concurrentEligibilityRequiresAnUnconditionalColumnUniqueIndex() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT count(*) "
                                    + "FROM investory.reporting_materialized_view_dependencies dependency "
                                    + "JOIN pg_class mv ON mv.relname = dependency.materialized_view "
                                    + "JOIN pg_namespace ns ON ns.oid = mv.relnamespace AND ns.nspname = 'investory' "
                                    + "WHERE dependency.concurrent_refresh_eligible <> EXISTS ("
                                    + "    SELECT 1 FROM pg_index idx "
                                    + "    WHERE idx.indrelid = mv.oid "
                                    + "      AND idx.indisunique "
                                    + "      AND idx.indisvalid "
                                    + "      AND idx.indpred IS NULL "
                                    + "      AND idx.indexprs IS NULL"
                                    + ")")) {
                assertTrue(result.next());
                assertEquals(0, result.getLong(1));
            }
        }
    }

    @Test
    void portfolioDailyMaterializationAgreesWithItsSourceProjection() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CALL investory.refresh_reporting_materialized_views()");

            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT count(*) FROM ("
                                    + "  (SELECT to_jsonb(materialized) - 'updated_at' "
                                    + "   FROM investory.portfolio_daily_mv materialized "
                                    + "   EXCEPT "
                                    + "   SELECT to_jsonb(source_projection) - 'updated_at' "
                                    + "   FROM investory.v_portfolio_daily source_projection) "
                                    + "  UNION ALL "
                                    + "  (SELECT to_jsonb(source_projection) - 'updated_at' "
                                    + "   FROM investory.v_portfolio_daily source_projection "
                                    + "   EXCEPT "
                                    + "   SELECT to_jsonb(materialized) - 'updated_at' "
                                    + "   FROM investory.portfolio_daily_mv materialized)"
                                    + ") differences")) {
                assertTrue(result.next());
                assertEquals(0, result.getLong(1));
            }
        }
    }

    @Test
    void sectorAllocationRemainsDeferredWithoutCanonicalTaxonomy() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT count(*) FROM information_schema.tables "
                                    + "WHERE table_schema = 'investory' "
                                    + "AND table_name IN ('sectors', 'asset_sectors', 'sector_allocation')")) {
                assertTrue(result.next());
                assertEquals(0, result.getLong(1));
            }

            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT col_description('investory.assets'::regclass, "
                                    + "(SELECT ordinal_position FROM information_schema.columns "
                                    + " WHERE table_schema = 'investory' "
                                    + " AND table_name = 'assets' AND column_name = 'asset_type'))")) {
                assertTrue(result.next());
                assertFalse(result.getString(1).isBlank());
                assertTrue(result.getString(1).contains("Sector allocation must not be introduced"));
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
