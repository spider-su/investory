package com.smartbox.investory.testsupport.happyinvestor;

import java.nio.charset.StandardCharsets;

/** Offline broker-source subset used by the F1 boundary of the Happy Investor path. */
public final class HappyInvestorImportFacts {
  public static final String FILE_NAME = "U17959259.TRANSACTIONS.HAPPY-INVESTOR.csv";
  public static final long PORTFOLIO_ID = 1L;
  public static final long ACCOUNT_ID = HappyInvestorTestData.IBKR_USD_ACCOUNT_ID;
  public static final String STATEMENT =
      String.join(
          "\n",
          "Transaction History,Header,Date,AccountEntity,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount,Commission,Net Amount,Currency",
          "Transaction History,Data,2024-07-31,Happy Investor,External funding,Deposit,-,-,-,-,100000,0,100000,USD",
          "Transaction History,Data,2024-08-08,Happy Investor,Buy AAPL,Buy,AAPL.US,100,180,USD,-18000,-1,-18001,USD",
          "Transaction History,Data,2024-08-08,Happy Investor,Buy VWRA,Buy,VWRA.UK,20,120,USD,-2400,-1,-2401,USD");
  public static final Expected EXPECTED = new Expected(100000, 2, 20, 1, "USD");

  private HappyInvestorImportFacts() {}

  public static byte[] bytes() {
    return STATEMENT.getBytes(StandardCharsets.UTF_8);
  }

  public record Expected(
      double deposit,
      int importedBuyCount,
      double vwraQuantity,
      double commissionPerTrade,
      String currency) {}
}
