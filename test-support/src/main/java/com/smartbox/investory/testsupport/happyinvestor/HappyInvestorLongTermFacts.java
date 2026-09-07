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
  public static final long INTEREST_BEARING_RESERVE_ID = 9406L;
  public static final LocalDate ACQUISITION_DATE = LocalDate.of(2024, 8, 1);

  /** Persisted active asset values at the canonical boundary. */
  public static final BigDecimal REAL_ESTATE_TOTAL = new BigDecimal("900000");

  public static final BigDecimal BOND_TOTAL = new BigDecimal("10000");
  public static final BigDecimal PLAIN_CASH_RESERVE_PRINCIPAL = new BigDecimal("25000");
  public static final BigDecimal CASH_RESERVE_TOTAL = new BigDecimal("50000");
  public static final BigDecimal PERSONAL_ASSET_TOTAL = new BigDecimal("10000");

  /** Includes Personal Asset because it is part of net worth and allocation, but not yield. */
  public static final BigDecimal LONG_TERM_TOTAL = new BigDecimal("970000");

  /** Collected during calendar 2025: 3,200 x 12 + 2,800 x 6 + 3,000 x 6. */
  public static final BigDecimal RENTAL_CALENDAR_2025_GROSS = new BigDecimal("73200");

  /** Boundary-date annualized economics at 2025-12-31: (3,200 + 3,000) x 12. */
  public static final BigDecimal RENTAL_BOUNDARY_DATE_GROSS_ANNUAL = new BigDecimal("74400");

  public static final BigDecimal APARTMENT_A_ANNUAL_TAX_BASE = new BigDecimal("3200");
  public static final BigDecimal APARTMENT_B_ANNUAL_TAX_BASE = new BigDecimal("3000");

  /** Apartment A: annual base 3,200 x 8.5%. */
  public static final BigDecimal APARTMENT_A_RENTAL_TAX_ANNUAL = new BigDecimal("272.00");

  /** Persisted boundary-date flow, supported by the two canonical annual tax bases above. */
  public static final BigDecimal RENTAL_BOUNDARY_DATE_TAX_ANNUAL = new BigDecimal("527.00");

  public static final BigDecimal RENTAL_BOUNDARY_DATE_NET_ANNUAL = new BigDecimal("73873.00");

  /** Boundary-date gross income: rental 74,400 + treasury 462.50 + term cash 1,000. */
  public static final BigDecimal AGGREGATE_GROSS_ANNUAL = new BigDecimal("75862.50");

  /** Rental tax 527 + 19% tax on treasury and interest-bearing cash income. */
  public static final BigDecimal AGGREGATE_TAX_ANNUAL = new BigDecimal("804.875");

  public static final BigDecimal AGGREGATE_NET_ANNUAL = new BigDecimal("75057.625");
  public static final BigDecimal RENTAL_TAX_RATE = new BigDecimal("0.085");
  public static final BigDecimal TREASURY_PRINCIPAL = new BigDecimal("10000");
  public static final BigDecimal TREASURY_ANNUAL_RATE = new BigDecimal("0.04625");
  public static final LocalDate TREASURY_ACQUISITION_DATE = LocalDate.of(2024, 7, 31);
  public static final LocalDate TREASURY_MATURITY_DATE = LocalDate.of(2026, 2, 28);
  public static final BigDecimal INTEREST_BEARING_RESERVE_PRINCIPAL = new BigDecimal("25000");
  public static final BigDecimal INTEREST_BEARING_RESERVE_ANNUAL_RATE = new BigDecimal("0.04");
  public static final LocalDate INTEREST_BEARING_RESERVE_MATURITY_DATE = LocalDate.of(2027, 8, 1);

  private HappyInvestorLongTermFacts() {}
}
