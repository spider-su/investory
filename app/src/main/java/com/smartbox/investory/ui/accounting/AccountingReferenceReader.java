package com.smartbox.investory.ui.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads independent legacy reference values for the native Ryczalt comparison view. */
@Component
final class AccountingReferenceReader {
  private final JdbcTemplate jdbc;

  AccountingReferenceReader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Reference read(long profileId, YearMonth month) {
    LocalDate period = month.atDay(1);
    var rows =
        jdbc.query(
            """
            select revenue, expenses, output_vat, deductible_input_vat, vat_payable,
                   document_count, bank_count, filing_status
              from investory.accounting_reference_month
             where profile_id=? and tax_period=?
            """,
            (rs, row) ->
                new Reference(
                    rs.getBigDecimal("revenue"),
                    rs.getBigDecimal("expenses"),
                    rs.getBigDecimal("output_vat"),
                    rs.getBigDecimal("deductible_input_vat"),
                    rs.getBigDecimal("vat_payable"),
                    rs.getInt("document_count"),
                    rs.getInt("bank_count"),
                    rs.getString("filing_status")),
            profileId,
            period);
    if (rows.isEmpty()) return null;
    Map<String, BigDecimal> obligations =
        jdbc
            .query(
                "select obligation_type, expected_amount from investory.accounting_reference_obligation where profile_id=? and tax_period=?",
                (rs, row) -> Map.entry(rs.getString(1), rs.getBigDecimal(2)),
                profileId,
                period)
            .stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    return rows.getFirst().withObligations(obligations);
  }

  record Reference(
      BigDecimal revenue,
      BigDecimal expenses,
      BigDecimal outputVat,
      BigDecimal deductibleInputVat,
      BigDecimal vatPayable,
      int documentCount,
      int bankCount,
      String filingStatus,
      BigDecimal ryczalt,
      BigDecimal zus) {
    Reference(
        BigDecimal revenue,
        BigDecimal expenses,
        BigDecimal outputVat,
        BigDecimal deductibleInputVat,
        BigDecimal vatPayable,
        int documentCount,
        int bankCount,
        String filingStatus) {
      this(
          revenue,
          expenses,
          outputVat,
          deductibleInputVat,
          vatPayable,
          documentCount,
          bankCount,
          filingStatus,
          null,
          null);
    }

    Reference withObligations(Map<String, BigDecimal> obligations) {
      return new Reference(
          revenue,
          expenses,
          outputVat,
          deductibleInputVat,
          obligations.getOrDefault("VAT", vatPayable),
          documentCount,
          bankCount,
          filingStatus,
          obligations.get("RYCZALT"),
          obligations.get("ZUS"));
    }
  }
}
