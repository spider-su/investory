package com.smartbox.investory.integrations.zus;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.integrations.zus.persistence.BankTransactionZusPaymentSource;
import com.smartbox.investory.testsupport.accounting.AccountingDatabase;
import com.smartbox.investory.testsupport.accounting.AccountingDatabaseTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MockZusClientIT extends AccountingDatabaseTest {
  private static final String TEST_PREFIX = "ZUS-MOCK-TEST";

  private JdbcTemplate jdbcTemplate;
  private ZusClient zusClient;

  @BeforeEach
  void insertBankHistory() {
    jdbcTemplate =
        new JdbcTemplate(
            new DriverManagerDataSource(
                AccountingDatabase.pocJdbcUrl(),
                AccountingDatabase.pocUsername(),
                AccountingDatabase.pocPassword()));
    zusClient = new MockZusClient(new BankTransactionZusPaymentSource(jdbcTemplate));
    deleteTestRows();
    insert("2026-07-10", "ZUS", "-2543.39");
    insert("2026-07-11", "ALLEGRO", "-120.00");
    insert("2026-08-10", "ZUS", "-2543.39");
  }

  @AfterEach
  void deleteTestRows() {
    jdbcTemplate.update(
        "DELETE FROM investory.ryczalt_transaction WHERE reference LIKE ?", TEST_PREFIX + "%");
  }

  @Test
  void findsOnlyJulyOutgoingZusPaymentFromCanonicalBankHistory() {
    List<ZusPayment> payments =
        zusClient.findPayments(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

    assertThat(payments).hasSize(1);
    assertThat(payments.getFirst().paymentDate()).isEqualTo(LocalDate.of(2026, 7, 10));
    assertThat(payments.getFirst().amount()).isEqualByComparingTo(new BigDecimal("2543.39"));
    assertThat(payments.getFirst().currency()).isEqualTo("PLN");
    assertThat(payments.getFirst().status()).isEqualTo(ZusPaymentStatus.SETTLED);
    assertThat(payments.getFirst().externalId()).startsWith("BANK-TX-");
  }

  private void insert(String date, String counterparty, String amount) {
    jdbcTemplate.update(
        "INSERT INTO investory.ryczalt_period(profile_id, period_year, period_month, status) "
            + "VALUES (1, 2026, ?, 'OPEN') ON CONFLICT (profile_id, period_year, period_month) DO NOTHING",
        LocalDate.parse(date).getMonthValue());
    jdbcTemplate.update(
        """
        INSERT INTO investory.ryczalt_transaction
            (period_id, profile_id, booking_date, reference, counterparty, currency, amount, description)
        VALUES ((SELECT id FROM investory.ryczalt_period WHERE profile_id = 1 AND period_year = 2026 AND period_month = ?),
                1, ?, ?, ?, 'PLN', ?, 'mock ZUS integration test')
        """,
        LocalDate.parse(date).getMonthValue(),
        LocalDate.parse(date),
        TEST_PREFIX + "-" + date,
        counterparty,
        new BigDecimal(amount));
  }
}
