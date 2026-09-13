package com.smartbox.investory.testsupport.accounting;

import java.io.IOException;
import java.net.URL;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Shared PostgreSQL instance for integration tests that do not need Flyway validation. */
public final class AccountingDatabase {

  // Use the canonical generated schema. The old accounting-only snapshot predates staging
  // tables and the immutable source-evidence trigger, so it allowed tests to pass against a
  // materially different database than Flyway production.
  private static final String SNAPSHOT = "db/snapshot/schema.sql";
  private static final String POC_SNAPSHOT = "db/snapshot/accounting/poc.sql";

  private static final com.smartbox.investory.testsupport.WorkerDatabase DATABASE = startDatabase();

  private AccountingDatabase() {}

  public static PostgreSQLContainer<?> container() {
    return com.smartbox.investory.testsupport.SharedPostgres.container();
  }

  public static String jdbcUrl() {
    return DATABASE.jdbcUrl();
  }

  public static String username() {
    return DATABASE.username();
  }

  public static String password() {
    return DATABASE.password();
  }

  public static String pocJdbcUrl() {
    return PocDatabaseHolder.INSTANCE.jdbcUrl();
  }

  public static String pocUsername() {
    return PocDatabaseHolder.INSTANCE.username();
  }

  public static String pocPassword() {
    return PocDatabaseHolder.INSTANCE.password();
  }

  /** Returns a separately initialized snapshot-backed database for a stateful test scope. */
  public static com.smartbox.investory.testsupport.WorkerDatabase scopedDatabase(String scope) {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("A non-blank database scope is required");
    }
    return startDatabase(scope);
  }

  private static com.smartbox.investory.testsupport.WorkerDatabase startDatabase() {
    return startDatabase(null);
  }

  private static com.smartbox.investory.testsupport.WorkerDatabase startDatabase(String scope) {
    com.smartbox.investory.testsupport.WorkerDatabase database =
        com.smartbox.investory.testsupport.SharedPostgres.database(
            scope == null || scope.isBlank() ? "accounting" : "accounting_" + scope);

    loadSnapshot(database, false);
    return database;
  }

  private static com.smartbox.investory.testsupport.WorkerDatabase startPocDatabase() {
    com.smartbox.investory.testsupport.WorkerDatabase database =
        com.smartbox.investory.testsupport.SharedPostgres.database("accounting_poc");

    loadSnapshot(database, false);
    // Prepare compatibility objects before loading poc.sql: older accounting snapshots do not
    // contain the source-evidence table referenced by the POC fixture.
    ensureSourceEvidence(database);
    loadSnapshot(database, true);
    ensureZusFixture(database);
    return database;
  }

  private static void ensureSourceEvidence(
      com.smartbox.investory.testsupport.WorkerDatabase database) {
    try (var connection = database.openConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS investory.accounting_source_evidence (
              id BIGSERIAL PRIMARY KEY,
              source_type VARCHAR(16) NOT NULL,
              external_reference VARCHAR(256) NOT NULL,
              original_filename VARCHAR(512), content_type VARCHAR(128),
              received_at TIMESTAMPTZ NOT NULL, document_date DATE,
              content_hash BYTEA NOT NULL, payload BYTEA NOT NULL,
              processing_status VARCHAR(32) NOT NULL, processing_error VARCHAR(1000),
              UNIQUE (source_type, external_reference)
          )
          """);
      statement.execute(
          "ALTER TABLE investory.accounting_source_evidence ADD COLUMN IF NOT EXISTS profile_id BIGINT");
      statement.execute(
          "UPDATE investory.accounting_source_evidence SET profile_id = 1 WHERE profile_id IS NULL");
      statement.execute(
          "ALTER TABLE investory.accounting_source_evidence ALTER COLUMN profile_id SET NOT NULL");
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS investory.accounting_tax_profile_period (
              id BIGSERIAL PRIMARY KEY,
              profile_id BIGINT NOT NULL,
              valid_from DATE NOT NULL,
              valid_to DATE,
              jdg_active BOOLEAN NOT NULL,
              ryczalt_rate NUMERIC(8, 5),
              vat_registered BOOLEAN NOT NULL,
              vat_eu_registered BOOLEAN NOT NULL,
              zus_regime VARCHAR(32),
              voluntary_sickness BOOLEAN NOT NULL
          )
          """);
    } catch (java.sql.SQLException exception) {
      throw new IllegalStateException(
          "Cannot initialize accounting source evidence fixture", exception);
    }
  }

  private static void ensureZusFixture(com.smartbox.investory.testsupport.WorkerDatabase database) {
    try (var connection = database.openConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          "ALTER TABLE investory.accounting_poc_invoice ADD COLUMN IF NOT EXISTS source_id BIGINT");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_expense_invoice ADD COLUMN IF NOT EXISTS source_id BIGINT");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_bank_transaction ADD COLUMN IF NOT EXISTS source_id BIGINT");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_bank_transaction ADD COLUMN IF NOT EXISTS source_row_identity VARCHAR(256)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_bank_transaction ADD COLUMN IF NOT EXISTS provider VARCHAR(32)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_bank_transaction ADD COLUMN IF NOT EXISTS external_account_id VARCHAR(256)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_bank_transaction ADD COLUMN IF NOT EXISTS external_transaction_id VARCHAR(256)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_bank_transaction ADD COLUMN IF NOT EXISTS source_payload_hash VARCHAR(128)");
      statement.execute(
          "ALTER TABLE investory.portfolios ADD COLUMN IF NOT EXISTS taxpayer_nip VARCHAR(10), ADD COLUMN IF NOT EXISTS taxpayer_full_name VARCHAR(240), ADD COLUMN IF NOT EXISTS taxpayer_first_name VARCHAR(120), ADD COLUMN IF NOT EXISTS taxpayer_surname VARCHAR(160), ADD COLUMN IF NOT EXISTS taxpayer_date_of_birth DATE, ADD COLUMN IF NOT EXISTS taxpayer_tax_office_code VARCHAR(4), ADD COLUMN IF NOT EXISTS taxpayer_email VARCHAR(255), ADD COLUMN IF NOT EXISTS tax_micro_account VARCHAR(34), ADD COLUMN IF NOT EXISTS zus_payment_account VARCHAR(34)");
      statement.execute(
          "UPDATE investory.accounting_poc_bank_transaction SET provider = COALESCE(provider, 'CSV'), external_account_id = COALESCE(external_account_id, 'LEGACY_SOURCE'), external_transaction_id = COALESCE(external_transaction_id, COALESCE(source_row_identity, 'legacy-' || id::varchar)) WHERE provider IS NULL OR external_account_id IS NULL OR external_transaction_id IS NULL");
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_poc_bank_external_transaction ON investory.accounting_poc_bank_transaction (provider, external_account_id, external_transaction_id)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_invoice ADD COLUMN IF NOT EXISTS counterparty_tax_identifier VARCHAR(32)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_invoice ADD COLUMN IF NOT EXISTS counterparty_country VARCHAR(2)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_invoice ADD COLUMN IF NOT EXISTS ksef_number VARCHAR(256)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_invoice ADD COLUMN IF NOT EXISTS filing_evidence VARCHAR(8)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_expense_invoice ADD COLUMN IF NOT EXISTS counterparty_tax_identifier VARCHAR(32)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_expense_invoice ADD COLUMN IF NOT EXISTS counterparty_country VARCHAR(2)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_expense_invoice ADD COLUMN IF NOT EXISTS ksef_number VARCHAR(256)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_expense_invoice ADD COLUMN IF NOT EXISTS filing_evidence VARCHAR(8)");
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS investory.employment_period (
              id BIGSERIAL PRIMARY KEY,
              profile_id BIGINT NOT NULL,
              employment_type VARCHAR(8) NOT NULL,
              date_from DATE NOT NULL,
              date_to DATE,
              CONSTRAINT chk_accounting_employment_type CHECK (employment_type IN ('UOP', 'JDG')),
              CONSTRAINT chk_accounting_employment_dates CHECK (date_to IS NULL OR date_to >= date_from)
          )
          """);
      statement.execute(
          "ALTER TABLE investory.accounting_poc_profile ADD COLUMN IF NOT EXISTS nip VARCHAR(10)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_profile ADD COLUMN IF NOT EXISTS full_name VARCHAR(240)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_profile ADD COLUMN IF NOT EXISTS tax_office_code VARCHAR(4)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_profile ADD COLUMN IF NOT EXISTS email VARCHAR(255)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_profile ADD COLUMN IF NOT EXISTS vat_payment_account VARCHAR(34)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_profile ADD COLUMN IF NOT EXISTS ryczalt_payment_account VARCHAR(34)");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_profile ADD COLUMN IF NOT EXISTS zus_payment_account VARCHAR(34)");
      statement.execute(
          "UPDATE investory.accounting_poc_profile SET nip = COALESCE(nip, '1010000000'), full_name = COALESCE(full_name, 'Investory Accounting POC'), tax_office_code = COALESCE(tax_office_code, '1215'), email = COALESCE(email, 'accounting@example.invalid') WHERE id = 1");
      statement.execute(
          "CREATE TABLE IF NOT EXISTS investory.accounting_poc_period_state (tax_period DATE PRIMARY KEY, confirmed_at TIMESTAMPTZ, confirmed_calculation_hash VARCHAR(64))");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_period_state ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'OPEN'");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_period_state ADD COLUMN IF NOT EXISTS reopened_at TIMESTAMPTZ");
      statement.execute(
          "ALTER TABLE investory.accounting_poc_period_state ADD COLUMN IF NOT EXISTS reopen_reason VARCHAR(1000)");
      statement.execute(
          "CREATE TABLE IF NOT EXISTS investory.accounting_filing_artifact (id BIGSERIAL PRIMARY KEY, artifact_type VARCHAR(40) NOT NULL, tax_period DATE NOT NULL, schema_version VARCHAR(40) NOT NULL, payload BYTEA NOT NULL, payload_hash VARCHAR(64) NOT NULL, generated_at TIMESTAMPTZ NOT NULL, status VARCHAR(16) NOT NULL)");
      statement.execute(
          "CREATE TABLE IF NOT EXISTS investory.accounting_authority_confirmation (id BIGSERIAL PRIMARY KEY, authority VARCHAR(32) NOT NULL, obligation_or_artifact_type VARCHAR(40) NOT NULL, tax_period DATE NOT NULL, external_reference VARCHAR(256) NOT NULL, confirmation_type VARCHAR(40) NOT NULL, status VARCHAR(16) NOT NULL, received_at TIMESTAMPTZ NOT NULL, source_document_id BIGINT, note VARCHAR(1000))");
      statement.execute(
          "CREATE TABLE IF NOT EXISTS investory.accounting_vat_transaction (id BIGSERIAL PRIMARY KEY, tax_period DATE NOT NULL, tax_date DATE NOT NULL, source_document_id VARCHAR(256) NOT NULL, reference VARCHAR(256) NOT NULL, direction VARCHAR(16) NOT NULL, treatment VARCHAR(48) NOT NULL, counterparty_country VARCHAR(2), counterparty_tax_identifier VARCHAR(64), identifier_type VARCHAR(16), vat_eu_number VARCHAR(64), vies_verified_at DATE, vies_status VARCHAR(24), net_amount NUMERIC(18, 2) NOT NULL, vat_amount NUMERIC(18, 2) NOT NULL, deductible_vat NUMERIC(18, 2) NOT NULL, evidence VARCHAR(256) NOT NULL)");
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_filing_artifact_hash ON investory.accounting_filing_artifact (artifact_type, tax_period, payload_hash)");
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_poc_bank_source_row ON investory.accounting_poc_bank_transaction (source_row_identity) WHERE source_row_identity IS NOT NULL");
      statement.execute(
          "ALTER TABLE investory.accounting_source_evidence DROP CONSTRAINT IF EXISTS chk_accounting_source_type");
      statement.execute(
          "ALTER TABLE investory.accounting_source_evidence ADD CONSTRAINT chk_accounting_source_type CHECK (source_type IN ('KSEF', 'UPLOAD', 'BANK'))");
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS investory.accounting_poc_profile (
              id SMALLINT PRIMARY KEY,
              has_uop BOOLEAN NOT NULL,
              CONSTRAINT chk_accounting_poc_profile_singleton CHECK (id = 1)
          )
          """);
      statement.executeUpdate(
          "INSERT INTO investory.accounting_poc_profile (id, profile_id, has_uop) VALUES (1, 1, TRUE) "
              + "ON CONFLICT (id) DO NOTHING");
      statement.executeUpdate(
          """
          INSERT INTO investory.accounting_poc_tax_input (profile_id, tax_period, input_type, amount, note)
          SELECT 1, period, 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900,
                 '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.'
            FROM generate_series(DATE '2026-01-01', DATE '2026-08-01', INTERVAL '1 month') period
           WHERE NOT EXISTS (
                 SELECT 1
                   FROM investory.accounting_poc_tax_input input
                  WHERE input.profile_id = 1 AND input.tax_period = period
                    AND input.input_type = 'JDG_COMPULSORY_SOCIAL_ZUS')
          """);
      // Legacy golden tests exercise the calculation service directly. Keep their historical
      // operational fixture isolated from the Flyway application baseline, which is empty by
      // design after V01.013.
      statement.execute(
          """
          INSERT INTO investory.accounting_poc_invoice
            (id,tax_period,issue_date,sale_date,fx_rate_date,reference,customer_alias,invoice_kind,currency,
             net_amount,vat_amount,gross_amount,correction_gross_amount,correction_net_amount,correction_vat_amount,
             expected_receivable,booked_net_pln,ryczalt_rate,note,source_id,counterparty_tax_identifier,
             counterparty_country,ksef_number,filing_evidence,profile_id)
          SELECT id,tax_period,issue_date,sale_date,fx_rate_date,reference,counterparty_alias,invoice_kind,currency,
                 net_amount,vat_amount,gross_amount,correction_gross_amount,correction_net_amount,correction_vat_amount,
                 expected_receivable,booked_net_pln,ryczalt_rate,note,source_id,counterparty_tax_identifier,
                 counterparty_country,ksef_number,filing_evidence,profile_id
            FROM investory.accounting_reference_invoice
           WHERE NOT EXISTS (SELECT 1 FROM investory.accounting_poc_invoice)
          """);
      statement.execute(
          """
          INSERT INTO investory.accounting_poc_expense_invoice
            (id,tax_period,invoice_date,reference,supplier_alias,category,currency,net_amount,vat_amount,gross_amount,
             vat_deduction_ratio,source_quality,note,source_id,counterparty_tax_identifier,counterparty_country,
             ksef_number,filing_evidence,profile_id)
          SELECT id,tax_period,invoice_date,reference,supplier_alias,category,currency,net_amount,vat_amount,gross_amount,
                 vat_deduction_ratio,source_quality,note,source_id,counterparty_tax_identifier,counterparty_country,
                 ksef_number,filing_evidence,profile_id
            FROM investory.accounting_reference_expense_invoice
           WHERE NOT EXISTS (SELECT 1 FROM investory.accounting_poc_expense_invoice)
          """);
      statement.execute(
          """
          INSERT INTO investory.accounting_poc_bank_transaction
            (id,booking_date,related_period,reference,counterparty_alias,currency,amount,transaction_type,scope,note,
             source_id,source_row_identity,provider,external_account_id,external_transaction_id,source_payload_hash,profile_id)
          SELECT id,booking_date,related_period,reference,counterparty_alias,currency,amount,transaction_type,scope,note,
                 source_id,source_row_identity,provider,external_account_id,external_transaction_id,source_payload_hash,profile_id
            FROM investory.accounting_reference_bank_transaction
           WHERE NOT EXISTS (SELECT 1 FROM investory.accounting_poc_bank_transaction)
          """);
      statement.execute(
          """
          INSERT INTO investory.accounting_poc_obligation
            (profile_id,tax_period,obligation_type,due_date,expected_amount,paid_amount,payment_date,status,note)
          SELECT 1,tax_period,obligation_type,due_date,expected_amount,paid_amount,payment_date,status,note
            FROM investory.accounting_reference_obligation
           WHERE NOT EXISTS (SELECT 1 FROM investory.accounting_poc_obligation)
          """);
      statement.execute(
          """
            INSERT INTO investory.accounting_poc_tax_input (profile_id,tax_period,input_type,amount,note)
          SELECT 1,tax_period,input_type,amount,note
            FROM investory.accounting_reference_tax_input
           WHERE NOT EXISTS (
                 SELECT 1 FROM investory.accounting_poc_tax_input current
                  WHERE current.profile_id = 1 AND current.tax_period = accounting_reference_tax_input.tax_period
                    AND current.input_type = accounting_reference_tax_input.input_type)
          """);
      statement.execute(
          "SELECT setval('investory.accounting_poc_invoice_id_seq', COALESCE((SELECT max(id) FROM investory.accounting_poc_invoice), 1), true), setval('investory.accounting_poc_expense_invoice_id_seq', COALESCE((SELECT max(id) FROM investory.accounting_poc_expense_invoice), 1), true), setval('investory.accounting_poc_bank_transaction_id_seq', COALESCE((SELECT max(id) FROM investory.accounting_poc_bank_transaction), 1), true)");
    } catch (java.sql.SQLException exception) {
      throw new IllegalStateException("Cannot initialize accounting ZUS test fixture", exception);
    }
  }

  private static final class PocDatabaseHolder {
    private static final com.smartbox.investory.testsupport.WorkerDatabase INSTANCE =
        startPocDatabase();
  }

  private static void loadSnapshot(
      com.smartbox.investory.testsupport.WorkerDatabase database, boolean includePoc) {

    if (!resourceExists(SNAPSHOT)) {
      throw new IllegalStateException(
          "Missing fast test database snapshot "
              + SNAPSHOT
              + ". Run bash scripts/update-test-db-snapshot.sh and commit the result.");
    }
    if (includePoc && !resourceExists(POC_SNAPSHOT)) {
      throw new IllegalStateException(
          "Missing fast test database POC snapshot "
              + POC_SNAPSHOT
              + ". Run bash scripts/update-test-db-snapshot.sh and commit the result.");
    }
    if (!schemaLoaded(database)) {
      executeResource(database, SNAPSHOT, "/tmp/investory-schema.sql");
    }
    if (includePoc && !pocSnapshotLoaded(database)) {
      executeResource(database, POC_SNAPSHOT, "/tmp/investory-poc.sql");
    }
  }

  private static boolean resourceExists(String resource) {
    URL url = AccountingDatabase.class.getClassLoader().getResource(resource);
    return url != null;
  }

  private static void executeResource(
      com.smartbox.investory.testsupport.WorkerDatabase database,
      String resource,
      String containerPath) {
    try {
      PostgreSQLContainer<?> postgres =
          com.smartbox.investory.testsupport.SharedPostgres.container();
      postgres.copyFileToContainer(MountableFile.forClasspathResource(resource), containerPath);
      Container.ExecResult result =
          postgres.execInContainer(
              "psql",
              "-v",
              "ON_ERROR_STOP=1",
              "--username",
              database.username(),
              "--dbname",
              database.databaseName(),
              "--file",
              containerPath);

      if (result.getExitCode() != 0) {
        throw new IllegalStateException(
            "Failed to initialize fast test database from "
                + resource
                + ". stdout: "
                + result.getStdout()
                + "; stderr: "
                + result.getStderr());
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while initializing fast test database from " + resource, exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Cannot initialize fast test database from " + resource, exception);
    }
  }

  private static boolean schemaLoaded(com.smartbox.investory.testsupport.WorkerDatabase database) {
    // The fast snapshot deliberately excludes Flyway's history table.
    return relationExists(database, "investory.currencies");
  }

  private static boolean pocSnapshotLoaded(
      com.smartbox.investory.testsupport.WorkerDatabase database) {
    return relationExists(database, "investory.accounting_poc_profile");
  }

  private static boolean relationExists(
      com.smartbox.investory.testsupport.WorkerDatabase database, String relation) {
    try (var connection = database.openConnection();
        var statement = connection.createStatement();
        var result = statement.executeQuery("SELECT to_regclass('" + relation + "')")) {
      return result.next() && result.getString(1) != null;
    } catch (Exception ignored) {
      return false;
    }
  }
}
