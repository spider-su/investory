package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Explicit non-investment input and expected-output facts for the Happy Investor story. */
public final class HappyInvestorLongTermFacts {
  public static final long CASH_RESERVE_ID = 9401L;
  public static final long APARTMENT_A_ID = 9402L;
  public static final long APARTMENT_B_ID = 9403L;
  public static final long FAMILY_CAR_ID = 9404L;
  public static final LocalDate ACQUISITION_DATE = LocalDate.of(2024, 8, 1);
  public static final BigDecimal LONG_TERM_TOTAL = new BigDecimal("1020000");
  public static final BigDecimal LONG_TERM_CAPITAL_EXCLUDING_RESERVE = new BigDecimal("970000");
  public static final BigDecimal RENTAL_TOTAL_GROSS_ANNUAL = new BigDecimal("74400");

  /** Apartment A only: 3,200 x 12 x 8.5%. */
  public static final BigDecimal APARTMENT_A_RENTAL_TAX_ANNUAL = new BigDecimal("3264.00");

  public static final BigDecimal RENTAL_TOTAL_TAX_ANNUAL = new BigDecimal("6324.00");
  public static final BigDecimal RENTAL_TOTAL_NET_ANNUAL = new BigDecimal("68076.00");
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
