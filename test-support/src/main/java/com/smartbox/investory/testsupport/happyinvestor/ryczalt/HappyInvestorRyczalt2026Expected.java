package com.smartbox.investory.testsupport.happyinvestor.ryczalt;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/** Independently stated operational expectations; never populated by production calculators. */
public final class HappyInvestorRyczalt2026Expected {
  private HappyInvestorRyczalt2026Expected() {}

  public static List<Month> months() {
    return List.of(
        month(
            "2026-01",
            "61771.23",
            "6714",
            "7383",
            "0",
            "498.35",
            "498.35",
            "LOW",
            "UOP_PRIMARY_INSURANCE"),
        month(
            "2026-02",
            "61849.12",
            "6707",
            "7372",
            "0",
            "830.58",
            "830.58",
            "MEDIUM",
            "UOP_PRIMARY_INSURANCE"),
        month(
            "2026-03",
            "65266.52",
            "7251",
            "7584",
            "1788.29",
            "830.58",
            "2618.87",
            "MEDIUM",
            "JDG_PRIMARY_INSURANCE"),
        month(
            "2026-04",
            "63561.25",
            "7028",
            "7380",
            "1788.29",
            "830.58",
            "2618.87",
            "MEDIUM",
            "JDG_PRIMARY_INSURANCE"),
        month(
            "2026-05",
            "61917.08",
            "6601",
            "7182",
            "1788.29",
            "830.58",
            "2618.87",
            "MEDIUM",
            "JDG_PRIMARY_INSURANCE"),
        month(
            "2026-06",
            "65310.80",
            "7293",
            "7550",
            "1788.29",
            "1495.04",
            "3283.33",
            "HIGH",
            "JDG_PRIMARY_INSURANCE"),
        month(
            "2026-07",
            "49158.87",
            "3592",
            "5611",
            "1788.29",
            "1495.04",
            "3283.33",
            "HIGH",
            "JDG_PRIMARY_INSURANCE"));
  }

  private static Month month(
      String month,
      String revenue,
      String vat,
      String ryczalt,
      String social,
      String health,
      String zus,
      String band,
      String reason) {
    return new Month(
        YearMonth.parse(month),
        new BigDecimal(revenue),
        new BigDecimal(vat),
        new BigDecimal(ryczalt),
        new BigDecimal(social),
        new BigDecimal(health),
        new BigDecimal(zus),
        band,
        reason);
  }

  public record Month(
      YearMonth month,
      BigDecimal revenue,
      BigDecimal vat,
      BigDecimal ryczalt,
      BigDecimal social,
      BigDecimal health,
      BigDecimal zus,
      String healthBand,
      String insuranceReason) {}
}
