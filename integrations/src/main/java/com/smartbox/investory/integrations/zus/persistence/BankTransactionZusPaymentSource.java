package com.smartbox.investory.integrations.zus.persistence;

import com.smartbox.investory.integrations.zus.ZusTransactionClassifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads canonical bank transactions without exposing their persistence shape to ZusClient. */
@Component
public class BankTransactionZusPaymentSource {
  private static final long POC_PROFILE_ID = 1L;
  private final JdbcTemplate jdbcTemplate;

  public BankTransactionZusPaymentSource(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<BankTransaction> findOutgoingZusTransactions(
      Long profileId, LocalDate from, LocalDate to) {
    if (profileId == null || profileId.longValue() != POC_PROFILE_ID) return List.of();
    if (from == null || to == null || !from.isBefore(to)) return List.of();
    return jdbcTemplate
        .query(
            """
        SELECT id, booking_date, amount, currency, reference, counterparty_alias, note
          FROM investory.accounting_poc_bank_transaction
         WHERE booking_date >= ? AND booking_date < ?
           AND amount < 0
           AND UPPER(currency) = 'PLN'
         ORDER BY booking_date, id
        """,
            (rs, rowNum) ->
                new BankTransaction(
                    rs.getLong("id"),
                    rs.getObject("booking_date", LocalDate.class),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency"),
                    rs.getString("reference"),
                    rs.getString("counterparty_alias"),
                    rs.getString("note")),
            from,
            to)
        .stream()
        .filter(
            transaction -> ZusTransactionClassifier.isZusCounterparty(transaction.counterparty()))
        .toList();
  }

  public record BankTransaction(
      long id,
      LocalDate paymentDate,
      BigDecimal amount,
      String currency,
      String reference,
      String counterparty,
      String note) {}
}
