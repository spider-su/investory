package com.smartbox.investory.ryczalt.reference;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only access to persisted Ryczalt obligation reference values. */
@Component
public class RyczaltObligationReferenceReader {
  private final JdbcTemplate jdbc;

  public RyczaltObligationReferenceReader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ReferenceObligation> find(long profileId, YearMonth month) {
    return jdbc.query(
        """
        SELECT obligation_type, expected_amount
        FROM investory.ryczalt_obligation_reference
        WHERE profile_id = ?
          AND tax_period = ?
        ORDER BY id
        """,
        (rs, rowNum) ->
            new ReferenceObligation(
                rs.getString("obligation_type"), rs.getBigDecimal("expected_amount")),
        profileId,
        month.atDay(1));
  }

  public record ReferenceObligation(String type, BigDecimal expected) {}
}
