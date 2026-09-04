package com.smartbox.investory.testsupport.happyinvestor;

import static com.smartbox.investory.testsupport.portfolio.PortfolioBuilders.account;
import static com.smartbox.investory.testsupport.portfolio.PortfolioBuilders.asset;
import static com.smartbox.investory.testsupport.portfolio.PortfolioBuilders.cashOperation;
import static com.smartbox.investory.testsupport.portfolio.PortfolioBuilders.openPosition;

import com.smartbox.investory.investment.ledger.cash.CashOperationType;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.position.PositionSettlementModel;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AccountDefinition;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AssetDefinition;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Factory for the deterministic, migration-aligned Happy Investor ledger subset. */
public final class HappyInvestorScenario {
  private static final AccountDefinition IBKR =
      new AccountDefinition(
          HappyInvestorTestData.IBKR_USD_ACCOUNT_ID,
          "IBKR USD investment account",
          CurrencyType.USD,
          "IBKR");
  private static final AccountDefinition XTB_USD =
      new AccountDefinition(
          HappyInvestorTestData.XTB_USD_ACCOUNT_ID,
          "XTB USD investment account",
          CurrencyType.USD,
          "XTB");
  private static final AccountDefinition XTB_PLN =
      new AccountDefinition(
          HappyInvestorTestData.XTB_PLN_ACCOUNT_ID,
          "XTB PLN investment account",
          CurrencyType.PLN,
          "XTB");
  private static final AccountDefinition XTB_EUR =
      new AccountDefinition(
          HappyInvestorTestData.XTB_EUR_ACCOUNT_ID,
          "XTB EUR cash-only account",
          CurrencyType.EUR,
          "XTB");

  private HappyInvestorScenario() {}

  public static HappyInvestorContext create() {
    var ibkr = account(IBKR).build();
    var xtbUsd = account(XTB_USD).build();
    var xtbPln = account(XTB_PLN).build();
    var xtbEur = account(XTB_EUR).build();
    xtbEur.setCashOnly(true);

    var aapl = asset(stock("AAPL.US", "AAPL", CurrencyType.USD)).build();
    var msft = asset(stock("MSFT.US", "MSFT", CurrencyType.USD)).build();
    var vwra = asset(stock("VWRA.UK", "VWRA", CurrencyType.USD)).build();
    var nvda = asset(stock("NVDA.US", "NVDA", CurrencyType.USD)).build();
    var amzn = asset(stock("AMZN.US", "AMZN", CurrencyType.USD)).build();
    var meta = asset(stock("META.US", "META", CurrencyType.USD)).build();
    var o = asset(stock("O.US", "O", CurrencyType.USD)).build();
    var tsla = asset(stock("TSLA.US", "TSLA", CurrencyType.USD)).build();
    var googl = asset(stock("GOOGL.US", "GOOGL", CurrencyType.USD)).build();
    var pko = asset(equity("PKO.PL", "PKO", CurrencyType.PLN)).build();
    var wig =
        asset(etf(HappyInvestorTestData.WIG20_ETF_SYMBOL, "ETFBW20TR", CurrencyType.PLN)).build();
    var spy = asset(stock("SPY.US", "SPY", CurrencyType.USD)).build();
    var treasury2026 =
        asset(HappyInvestorTestData.TREASURY_2026)
            .withName("United States Treasury 4 5/8 02/28/26")
            .build();
    var treasury2033 =
        asset(HappyInvestorTestData.TREASURY_2033)
            .withName("United States Treasury 4 3/8 07/31/33")
            .build();
    var natgas = asset(commodity(HappyInvestorTestData.NATGAS_SYMBOL)).build();

    List<CashOperationEntity> ledger = new ArrayList<>();
    addFunding(ledger, IBKR, 100_000, CurrencyType.USD, 3_000);
    addFunding(ledger, XTB_USD, 4_000, CurrencyType.USD, 1_000);
    addFunding(ledger, XTB_PLN, 4_000, CurrencyType.PLN, 1_000);
    addFunding(ledger, XTB_EUR, 8_000, CurrencyType.EUR, 2_000);
    double usdFromEur = HappyInvestorTestData.EUR_USD_TRANSFER_AMOUNT.doubleValue();
    double plnFromEur = HappyInvestorTestData.EUR_PLN_TRANSFER_AMOUNT.doubleValue();
    transfer(
        ledger,
        XTB_EUR.id(),
        XTB_USD.id(),
        4_000,
        usdFromEur,
        CurrencyType.EUR,
        CurrencyType.USD,
        "EUR-USD-2024-07-31");
    transfer(
        ledger,
        XTB_EUR.id(),
        XTB_PLN.id(),
        4_000,
        plnFromEur,
        CurrencyType.EUR,
        CurrencyType.PLN,
        "EUR-PLN-2024-07-31");
    transfer(
        ledger,
        XTB_PLN.id(),
        XTB_USD.id(),
        500,
        HappyInvestorTestData.PLN_USD_TRANSFER_AMOUNT.doubleValue(),
        CurrencyType.PLN,
        CurrencyType.USD,
        "PLN-USD-2025-03");
    transfer(
        ledger,
        XTB_USD.id(),
        XTB_PLN.id(),
        500,
        500 * 3.9993,
        CurrencyType.USD,
        CurrencyType.PLN,
        "USD-PLN-2025-03");
    addCanonicalIncome(ledger);

    List<PositionEntity> open =
        List.of(
            openPosition(definition("AAPL.US", "AAPL", CurrencyType.USD))
                .forAccount(IBKR.id())
                .quantity(100)
                .price(180)
                .commission(-1)
                .on(LocalDate.of(2024, 8, 8))
                .build(),
            openPosition(definition("AAPL.US", "AAPL", CurrencyType.USD))
                .forAccount(IBKR.id())
                .quantity(50)
                .price(200)
                .commission(-1)
                .on(LocalDate.of(2025, 2, 12))
                .build(),
            openPosition(definition("VWRA.UK", "VWRA", CurrencyType.USD))
                .forAccount(IBKR.id())
                .quantity(20)
                .price(120)
                .commission(-1)
                .build(),
            openPosition(definition("VWRA.UK", "VWRA", CurrencyType.USD))
                .forAccount(XTB_PLN.id())
                .quantity(10)
                .price(130)
                .commission(0)
                .build(),
            openPosition(definition("NVDA.US", "NVDA", CurrencyType.USD))
                .forAccount(XTB_USD.id())
                .quantity(10)
                .price(100)
                .commission(0)
                .build(),
            openPosition(definition("TSLA.US", "TSLA", CurrencyType.USD))
                .forAccount(XTB_USD.id())
                .quantity(1)
                .price(200)
                .commission(0)
                .build(),
            openPosition(definition("GOOGL.US", "GOOGL", CurrencyType.USD))
                .forAccount(XTB_PLN.id())
                .quantity(5)
                .price(150)
                .commission(0)
                .build(),
            openPosition(definition("MSFT.US", "MSFT", CurrencyType.USD))
                .forAccount(IBKR.id())
                .quantity(10)
                .price(100)
                .commission(-1)
                .build());

    // NATGAS is a closed RESULT_ONLY CFD: the net result and swap live on the position, the
    // realized
    // trade cash (CLOSE_TRADE + ROLLOVER) and the SWAP fee live on the ledger. Mirrors the golden
    // import and happyinvestor-broker.sql position 7110.
    PositionEntity natgasCfd =
        openPosition(commodity(HappyInvestorTestData.NATGAS_SYMBOL))
            .forAccount(XTB_USD.id())
            .quantity(0.01)
            .price(2.946)
            .commission(0)
            .profit(HappyInvestorTestData.NATGAS_NET_RESULT.doubleValue())
            .swap(HappyInvestorTestData.NATGAS_SWAP.doubleValue())
            .settlementModel(PositionSettlementModel.RESULT_ONLY)
            .on(HappyInvestorTestData.NATGAS_CLOSE_DATE)
            .closeOn(HappyInvestorTestData.NATGAS_CLOSE_DATE)
            .build();
    addNatgasSettlement(ledger);

    return new HappyInvestorContext(
        ibkr,
        xtbUsd,
        xtbPln,
        xtbEur,
        aapl,
        msft,
        vwra,
        nvda,
        amzn,
        meta,
        o,
        tsla,
        googl,
        pko,
        wig,
        spy,
        treasury2026,
        treasury2033,
        natgas,
        open,
        List.of(natgasCfd),
        ledger,
        HappyInvestorSimulationSpec.defaults());
  }

  private static AssetDefinition stock(String symbol, String ticker, CurrencyType currency) {
    return new AssetDefinition(
        symbol,
        ticker,
        ticker,
        symbol,
        currency == CurrencyType.PLN ? "Poland" : "United States",
        currency,
        "EQUITY");
  }

  private static AssetDefinition equity(String symbol, String ticker, CurrencyType currency) {
    return stock(symbol, ticker, currency);
  }

  private static AssetDefinition etf(String symbol, String ticker, CurrencyType currency) {
    return new AssetDefinition(
        symbol,
        ticker,
        ticker,
        symbol,
        currency == CurrencyType.PLN ? "Poland" : "United States",
        currency,
        "ETF");
  }

  private static AssetDefinition definition(String symbol, String ticker, CurrencyType currency) {
    return stock(symbol, ticker, currency);
  }

  private static AssetDefinition commodity(String symbol) {
    return new AssetDefinition(
        symbol, symbol, symbol, symbol, "United States", CurrencyType.USD, "COMMODITY");
  }

  private static void addFunding(
      List<CashOperationEntity> ledger,
      AccountDefinition account,
      double deposit,
      CurrencyType currency,
      double withdrawal) {
    ledger.add(
        cashOperation()
            .forAccount(account)
            .deposit(deposit, currency)
            .on(HappyInvestorTestData.HISTORY_START)
            .build());
    ledger.add(
        cashOperation()
            .forAccount(account)
            .withdrawal(withdrawal, currency)
            .on(HappyInvestorTestData.REFERENCE_DATE)
            .build());
  }

  /**
   * Canonical IBKR income and fee cash operations that mirror happyinvestor-broker.sql rows
   * 7017-7021: one trade commission, a dividend with its 19% withholding tax, and Treasury
   * free-funds interest with its 19% tax. Kept in sync with the FastDatabase snapshot so both
   * fixtures tell one story.
   */
  private static void addCanonicalIncome(List<CashOperationEntity> ledger) {
    ledger.add(
        cashOperation()
            .forAccount(IBKR)
            .fee(1, CurrencyType.USD, "IBKR trade commission")
            .on(LocalDate.of(2024, 8, 8))
            .build());
    ledger.add(
        cashOperation()
            .forAccount(IBKR)
            .deposit(120, CurrencyType.USD)
            .type(CashOperationType.DIVIDEND)
            .comment("Canonical dividend")
            .on(LocalDate.of(2025, 6, 30))
            .build());
    ledger.add(
        cashOperation()
            .forAccount(IBKR)
            .withdrawal(22.8, CurrencyType.USD)
            .type(CashOperationType.WITHHOLDING_TAX)
            .comment("Canonical dividend tax 19%")
            .on(LocalDate.of(2025, 6, 30))
            .build());
    ledger.add(
        cashOperation()
            .forAccount(IBKR)
            .deposit(231.25, CurrencyType.USD)
            .type(CashOperationType.FREE_FUNDS_INTEREST)
            .comment("Canonical Treasury interest")
            .on(LocalDate.of(2025, 2, 28))
            .build());
    ledger.add(
        cashOperation()
            .forAccount(IBKR)
            .withdrawal(43.9375, CurrencyType.USD)
            .type(CashOperationType.FREE_FUNDS_INTEREST_TAX)
            .comment("Canonical Treasury interest tax 19%")
            .on(LocalDate.of(2025, 2, 28))
            .build());
  }

  private static void addNatgasSettlement(List<CashOperationEntity> ledger) {
    ledger.add(
        cashOperation()
            .forAccount(XTB_USD)
            .type(CashOperationType.CLOSE_TRADE)
            .amount(HappyInvestorTestData.NATGAS_CLOSE_TRADE.doubleValue(), CurrencyType.USD)
            .symbol(HappyInvestorTestData.NATGAS_SYMBOL)
            .comment("NATGAS CFD 2040572606 close (gross 105.90 net of -86.10 rollover)")
            .on(HappyInvestorTestData.NATGAS_CLOSE_DATE)
            .build());
    ledger.add(
        cashOperation()
            .forAccount(XTB_USD)
            .type(CashOperationType.SWAP)
            .amount(HappyInvestorTestData.NATGAS_SWAP.doubleValue(), CurrencyType.USD)
            .symbol(HappyInvestorTestData.NATGAS_SYMBOL)
            .comment("NATGAS CFD 2040572606 swap")
            .on(HappyInvestorTestData.NATGAS_CLOSE_DATE)
            .build());
  }

  private static void transfer(
      List<CashOperationEntity> ledger,
      long from,
      long to,
      double out,
      double in,
      CurrencyType fromCurrency,
      CurrencyType toCurrency,
      String ref) {
    ledger.add(
        cashOperation()
            .forAccount(from)
            .transfer(-out, fromCurrency, ref)
            .on(HappyInvestorTestData.HISTORY_START)
            .build());
    ledger.add(
        cashOperation()
            .forAccount(to)
            .transfer(in, toCurrency, ref)
            .on(HappyInvestorTestData.HISTORY_START)
            .build());
  }
}
