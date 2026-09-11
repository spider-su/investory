package com.smartbox.investory.poc.accounting;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountingFactRepository {
  private final JdbcTemplate jdbcTemplate;

  public List<AccountingFact> findAll() {
    return jdbcTemplate.query(
        """
        SELECT id,
               fact_date,
               fact_type,
               reference,
               counterparty_alias,
               currency,
               amount,
               tax_rate,
               note
          FROM investory.accounting_poc_fact
         ORDER BY fact_date DESC NULLS LAST, id DESC
        """,
        (rs, rowNum) ->
            new AccountingFact(
                rs.getLong("id"),
                rs.getObject("fact_date", java.time.LocalDate.class),
                rs.getString("fact_type"),
                rs.getString("reference"),
                rs.getString("counterparty_alias"),
                rs.getString("currency"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("tax_rate"),
                rs.getString("note")));
  }
}
