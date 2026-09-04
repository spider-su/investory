package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Explicit non-investment input and expected-output facts for the Happy Investor story. */
public final class HappyInvestorLongTermFacts {
  public static final long CASH_RESERVE_ID = 9401L;
  public static final long APARTMENT_A_ID = 9402L;
  public static final long APARTMENT_B_ID = 9403L;
  public static final long FAMILY_CAR_ID = 9404L;
  public static final long TREASURY_ID = 9405L;
  public static final long RESERVE_DEPOSIT_ID = 9406L;
  public static final LocalDate ACQUISITION_DATE = LocalDate.of(2024, 8, 1);

  /** Financial total; the notes-only Family Car is intentionally excluded. */
  public static final BigDecimal LONG_TERM_TOTAL = new BigDecimal("1010000");

  public static final BigDecimal LONG_TERM_CAPITAL_EXCLUDING_RESERVE = new BigDecimal("960000");

  /** Collected during calendar 2025: 3,200 x 12 + 2,800 x 6 + 3,000 x 6. */
  public static final BigDecimal RENTAL_CALENDAR_2025_GROSS = new BigDecimal("73200");

  /** Boundary-date annualized economics at 2025-12-31: (3,200 + 3,000) x 12. */
  public static final BigDecimal RENTAL_BOUNDARY_DATE_GROSS_ANNUAL = new BigDecimal("74400");

  public static final BigDecimal APARTMENT_A_MONTHLY_TAX_BASE = new BigDecimal("3200");
  public static final BigDecimal APARTMENT_B_MONTHLY_TAX_BASE = new BigDecimal("3000");

  /** Apartment A only: 3,200 x 12 x 8.5%. */
  public static final BigDecimal APARTMENT_A_RENTAL_TAX_ANNUAL = new BigDecimal("3264.00");

  /** Persisted boundary-date flow, supported by the two canonical monthly tax bases above. */
  public static final BigDecimal RENTAL_BOUNDARY_DATE_TAX_ANNUAL = new BigDecimal("6324.00");

  public static final BigDecimal RENTAL_BOUNDARY_DATE_NET_ANNUAL = new BigDecimal("68076.00");
  public static final BigDecimal AGGREGATE_GROSS_ANNUAL = new BigDecimal("78112.50");
  public static final BigDecimal AGGREGATE_TAX_ANNUAL = new BigDecimal("6791.875");
  public static final BigDecimal AGGREGATE_NET_ANNUAL = new BigDecimal("71320.625");
  public static final BigDecimal RENTAL_TAX_RATE = new BigDecimal("0.085");
  public static final BigDecimal TREASURY_PRINCIPAL = new BigDecimal("10000");
  public static final BigDecimal TREASURY_ANNUAL_RATE = new BigDecimal("0.04625");
  public static final LocalDate TREASURY_ACQUISITION_DATE = LocalDate.of(2024, 7, 31);
  public static final LocalDate TREASURY_MATURITY_DATE = LocalDate.of(2026, 2, 28);
  public static final BigDecimal RESERVE_DEPOSIT_PRINCIPAL = new BigDecimal("50000");
  public static final BigDecimal RESERVE_DEPOSIT_ANNUAL_RATE = new BigDecimal("0.04");
  public static final LocalDate RESERVE_DEPOSIT_MATURITY_DATE = LocalDate.of(2027, 8, 1);

  private HappyInvestorLongTermFacts() {}
}
