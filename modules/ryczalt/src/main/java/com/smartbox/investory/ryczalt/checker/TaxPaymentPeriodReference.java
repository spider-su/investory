package com.smartbox.investory.ryczalt.checker;

import com.smartbox.investory.ryczalt.domain.Transaction;
import java.time.YearMonth;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prevents bank payments with an explicit tax-period marker from settling another period. */
public final class TaxPaymentPeriodReference {
  private static final Pattern TAX_OFFICE_PERIOD =
      Pattern.compile("/OKR/(\\d{2})M(\\d{2})(?:/|\\b)", Pattern.CASE_INSENSITIVE);
  private static final Pattern ZUS_PERIOD = Pattern.compile("^\\s*(\\d{2})M(\\d{2})\\b");

  private TaxPaymentPeriodReference() {}

  public static boolean matches(YearMonth obligationPeriod, Transaction transaction) {
    Matcher taxOffice = TAX_OFFICE_PERIOD.matcher(value(transaction.description()));
    if (taxOffice.find()) return samePeriod(obligationPeriod, taxOffice);

    if (value(transaction.counterparty()).toUpperCase(Locale.ROOT).contains("ZUS")) {
      Matcher zus = ZUS_PERIOD.matcher(value(transaction.description()));
      if (zus.find()) return samePeriod(obligationPeriod, zus);
    }
    return true;
  }

  private static boolean samePeriod(YearMonth expected, Matcher marker) {
    try {
      return expected.equals(
          YearMonth.of(
              2000 + Integer.parseInt(marker.group(1)), Integer.parseInt(marker.group(2))));
    } catch (RuntimeException invalidMarker) {
      return false;
    }
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}
