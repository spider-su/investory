package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Exports the canonical monthly JDG accounting view used by the Accounting POC. */
@Service
@RequiredArgsConstructor
public class AccountingJdgExporter {
  private static final String HEADER =
      "period,has_uop,domestic_revenue_net_pln,foreign_booked_revenue_pln,total_revenue_pln,"
          + "ryczalt_tax_pln,output_vat_pln,deductible_input_vat_pln,vat_payable_pln,"
          + "social_zus_pln,health_zus_pln,total_zus_pln";
  private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

  private final AccountingFactService factService;

  public byte[] exportCsv() {
    StringBuilder csv = new StringBuilder(HEADER).append('\n');
    List<LocalDate> periods = factService.availablePeriods();
    for (LocalDate period : periods) {
      AccountingMonthSnapshot snapshot = factService.snapshot(period);
      csv.append(row(snapshot)).append('\n');
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String row(AccountingMonthSnapshot snapshot) {
    return String.join(
        ",",
        csv(snapshot.period().format(MONTH_FORMAT)),
        csv(snapshot.zus().hasUop()),
        csv(snapshot.domesticRevenueNetPln()),
        csv(snapshot.foreignBookedRevenuePln()),
        csv(snapshot.totalBookedRevenuePln()),
        csv(snapshot.ryczalt().calculatedTax()),
        csv(snapshot.vat().outputVatAfterSalesCorrection()),
        csv(snapshot.vat().deductibleInputVat()),
        csv(snapshot.vat().calculatedVat()),
        csv(snapshot.zus().socialZus()),
        csv(snapshot.zus().healthZus()),
        csv(snapshot.zus().totalZus()));
  }

  private String csv(Object value) {
    if (value == null) {
      return "";
    }
    String text = value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
    return text.contains(",") || text.contains("\"") || text.contains("\n")
        ? '"' + text.replace("\"", "\"\"") + '"'
        : text;
  }
}
