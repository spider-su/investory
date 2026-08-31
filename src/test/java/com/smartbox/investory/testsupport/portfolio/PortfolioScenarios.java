package com.smartbox.investory.testsupport.portfolio;

import static com.smartbox.investory.testsupport.portfolio.PortfolioBuilders.account;
import static com.smartbox.investory.testsupport.portfolio.PortfolioBuilders.asset;
import static com.smartbox.investory.testsupport.portfolio.PortfolioBuilders.cashOperation;
import static com.smartbox.investory.testsupport.portfolio.PortfolioBuilders.closedPosition;
import static com.smartbox.investory.testsupport.portfolio.PortfolioBuilders.fxRate;
import static com.smartbox.investory.testsupport.portfolio.PortfolioBuilders.importHistory;
import static com.smartbox.investory.testsupport.portfolio.PortfolioBuilders.openPosition;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_DIVIDEND_GROSS;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_FIRST_BUY_COMMISSION;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_FIRST_BUY_DATE;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_FIRST_BUY_PRICE;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_FIRST_BUY_QUANTITY;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_PARTIAL_SALE_COMMISSION;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_PARTIAL_SALE_DATE;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_PARTIAL_SALE_PRICE;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_PARTIAL_SALE_QUANTITY;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_PRICES_USD;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_SECOND_BUY_COMMISSION;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_SECOND_BUY_DATE;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_SECOND_BUY_PRICE;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_SECOND_BUY_QUANTITY;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AAPL_WITHHOLDING_TAX;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.BTC;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.CRYPTO_USD;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.DEFAULT_EUR_DEPOSIT;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.DEFAULT_PLN_DEPOSIT;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.DEFAULT_USD_DEPOSIT;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.ETH;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.EUR_USD;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.IBKR_USD;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.IWDA_AS;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.JANUARY_DEPOSIT_DATE;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.JANUARY_MONTH_END;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.MSFT;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.PERIOD_START;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.PKO_WA;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.PLN_USD;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.POLISH_BONDS_PLN;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.SIE_DE;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.SPY;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.TSLA;
import static com.smartbox.investory.testsupport.portfolio.PortfolioTestData.XTB_EUR;

import com.smartbox.investory.infrastructure.BrokerType;
import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.infrastructure.repository.Asset;
import com.smartbox.investory.infrastructure.repository.CashOperation;
import com.smartbox.investory.infrastructure.repository.ClosedPosition;
import com.smartbox.investory.infrastructure.repository.CurrencyRate;
import com.smartbox.investory.infrastructure.repository.OpenedPosition;
import com.smartbox.investory.infrastructure.repository.imports.ImportHistory;
import java.util.List;

public final class PortfolioScenarios {

  private static final double INTERNAL_TRANSFER_AMOUNT = 5_000.00;

  private PortfolioScenarios() {}

  /** Creates deterministic accounts and assets with no operations or positions. */
  public static PortfolioTestContext createEmptyPortfolio() {
    return context(
        accounts(),
        assets(),
        operations(null, null, null, null, null, null, null, List.of()),
        positions(null, null, null, List.of(), List.of()),
        fxRates(null, null, List.of()),
        imports(null, null),
        expected(
            null, null, null, new PortfolioExpected.Valuation(0.0, 0.0, 0.0), null, null, null));
  }

  /** Creates one funded USD account and no positions. */
  public static PortfolioTestContext createFundedPortfolio() {
    CashOperation deposit =
        cashOperation()
            .forAccount(IBKR_USD)
            .deposit(DEFAULT_USD_DEPOSIT, CurrencyType.USD)
            .on(JANUARY_DEPOSIT_DATE)
            .comment("Electronic Fund Transfer")
            .build();

    return context(
        accounts(),
        assets(),
        operations(deposit, null, null, null, null, null, null, List.of(deposit)),
        positions(null, null, null, List.of(), List.of()),
        fxRates(null, null, List.of()),
        imports(null, null),
        expected(
            new PortfolioExpected.CashBalance(0.0, DEFAULT_USD_DEPOSIT, DEFAULT_USD_DEPOSIT),
            null,
            null,
            new PortfolioExpected.Valuation(DEFAULT_USD_DEPOSIT, 0.0, DEFAULT_USD_DEPOSIT),
            null,
            null,
            null));
  }

  /** Deposit 100,000 USD and buy 100 AAPL at 180 with a 5 USD commission. */
  public static PortfolioTestContext createLongPositionScenario() {
    PortfolioTestContext funded = createFundedPortfolio();
    double monthEndPrice = AAPL_PRICES_USD.get(JANUARY_MONTH_END);
    Asset aapl =
        asset(AAPL).withLatestPrice(monthEndPrice, monthEndPrice, JANUARY_MONTH_END).build();
    CashOperation deposit = funded.operations().initialUsdDeposit();
    OpenedPosition position =
        openPosition(AAPL)
            .forAccount(IBKR_USD)
            .quantity(AAPL_FIRST_BUY_QUANTITY)
            .price(AAPL_FIRST_BUY_PRICE)
            .commission(AAPL_FIRST_BUY_COMMISSION)
            .marketPrice(monthEndPrice)
            .on(AAPL_FIRST_BUY_DATE)
            .build();

    double grossCost = AAPL_FIRST_BUY_QUANTITY * AAPL_FIRST_BUY_PRICE;
    double endingCash = DEFAULT_USD_DEPOSIT - grossCost + AAPL_FIRST_BUY_COMMISSION;
    double marketValue = AAPL_FIRST_BUY_QUANTITY * monthEndPrice;

    return context(
        funded.accounts(),
        funded.assets().withAapl(aapl),
        operations(deposit, null, null, null, null, null, null, List.of(deposit)),
        positions(position, null, null, List.of(position), List.of()),
        funded.fxRates(),
        funded.imports(),
        expected(
            new PortfolioExpected.CashBalance(0.0, endingCash, DEFAULT_USD_DEPOSIT),
            new PortfolioExpected.Position(
                AAPL_FIRST_BUY_QUANTITY,
                monthEndPrice,
                marketValue,
                grossCost,
                marketValue - grossCost),
            null,
            new PortfolioExpected.Valuation(endingCash, marketValue, endingCash + marketValue),
            null,
            null,
            null));
  }

  /** Adds a second AAPL purchase lot to the long-position scenario. */
  public static PortfolioTestContext createMultipleLotsScenario() {
    PortfolioTestContext base = createLongPositionScenario();
    double monthEndPrice = AAPL_PRICES_USD.get(PortfolioTestData.FEBRUARY_MONTH_END);
    OpenedPosition secondLot =
        openPosition(AAPL)
            .forAccount(IBKR_USD)
            .quantity(AAPL_SECOND_BUY_QUANTITY)
            .price(AAPL_SECOND_BUY_PRICE)
            .commission(AAPL_SECOND_BUY_COMMISSION)
            .marketPrice(monthEndPrice)
            .on(AAPL_SECOND_BUY_DATE)
            .build();

    double grossCost =
        AAPL_FIRST_BUY_QUANTITY * AAPL_FIRST_BUY_PRICE
            + AAPL_SECOND_BUY_QUANTITY * AAPL_SECOND_BUY_PRICE;
    double quantity = AAPL_FIRST_BUY_QUANTITY + AAPL_SECOND_BUY_QUANTITY;
    double fees = Math.abs(AAPL_FIRST_BUY_COMMISSION) + Math.abs(AAPL_SECOND_BUY_COMMISSION);
    double endingCash = DEFAULT_USD_DEPOSIT - grossCost - fees;
    double marketValue = quantity * monthEndPrice;

    return context(
        base.accounts(),
        base.assets()
            .withAapl(
                asset(AAPL)
                    .withLatestPrice(
                        monthEndPrice, monthEndPrice, PortfolioTestData.FEBRUARY_MONTH_END)
                    .build()),
        base.operations(),
        positions(
            base.positions().aaplOpen(),
            secondLot,
            null,
            List.of(base.positions().aaplOpen(), secondLot),
            List.of()),
        base.fxRates(),
        base.imports(),
        expected(
            new PortfolioExpected.CashBalance(0.0, endingCash, DEFAULT_USD_DEPOSIT),
            new PortfolioExpected.Position(
                quantity, monthEndPrice, marketValue, grossCost, marketValue - grossCost),
            null,
            new PortfolioExpected.Valuation(endingCash, marketValue, endingCash + marketValue),
            null,
            null,
            null));
  }

  /** Adds a partial AAPL sale closed-position fixture with FIFO expected values. */
  public static PortfolioTestContext createPartialSaleScenario() {
    PortfolioTestContext lots = createMultipleLotsScenario();
    double realizedProfit =
        AAPL_PARTIAL_SALE_QUANTITY * (AAPL_PARTIAL_SALE_PRICE - AAPL_FIRST_BUY_PRICE)
            + AAPL_PARTIAL_SALE_COMMISSION;
    ClosedPosition sale =
        closedPosition(AAPL)
            .profit(realizedProfit)
            .commission(AAPL_PARTIAL_SALE_COMMISSION)
            .closeOn(AAPL_PARTIAL_SALE_DATE)
            .build();
    sale.setVolume(AAPL_PARTIAL_SALE_QUANTITY);
    sale.setOpenPrice(AAPL_FIRST_BUY_PRICE);
    sale.setClosePrice(AAPL_PARTIAL_SALE_PRICE);
    sale.setPurchaseValue(AAPL_PARTIAL_SALE_QUANTITY * AAPL_FIRST_BUY_PRICE);
    sale.setSaleValue(AAPL_PARTIAL_SALE_QUANTITY * AAPL_PARTIAL_SALE_PRICE);

    double remainingQuantity =
        AAPL_FIRST_BUY_QUANTITY + AAPL_SECOND_BUY_QUANTITY - AAPL_PARTIAL_SALE_QUANTITY;

    return context(
        lots.accounts(),
        lots.assets(),
        lots.operations(),
        positions(
            lots.positions().aaplOpen(),
            lots.positions().aaplSecondLot(),
            sale,
            lots.positions().open(),
            List.of(sale)),
        lots.fxRates(),
        lots.imports(),
        expected(
            lots.expected().cash(),
            new PortfolioExpected.Position(
                remainingQuantity,
                AAPL_PARTIAL_SALE_PRICE,
                remainingQuantity * AAPL_PARTIAL_SALE_PRICE,
                lots.expected().position().costBasis() - sale.getPurchaseValue(),
                remainingQuantity * AAPL_PARTIAL_SALE_PRICE
                    - (lots.expected().position().costBasis() - sale.getPurchaseValue())),
            null,
            lots.expected().valuation(),
            null,
            null,
            null));
  }

  /** Creates dividend and withholding-tax cash events separate from trading events. */
  public static PortfolioTestContext createDividendScenario() {
    PortfolioTestContext longPosition = createLongPositionScenario();
    CashOperation dividend =
        cashOperation()
            .forAccount(IBKR_USD)
            .dividend(AAPL, AAPL_DIVIDEND_GROSS)
            .on(PortfolioTestData.MID_YEAR)
            .build();
    CashOperation tax =
        cashOperation()
            .forAccount(IBKR_USD)
            .withholdingTax(AAPL, Math.abs(AAPL_WITHHOLDING_TAX))
            .on(PortfolioTestData.MID_YEAR)
            .build();

    return context(
        longPosition.accounts(),
        longPosition.assets(),
        operations(
            longPosition.operations().initialUsdDeposit(),
            null,
            null,
            dividend,
            tax,
            null,
            null,
            List.of(longPosition.operations().initialUsdDeposit(), dividend, tax)),
        longPosition.positions(),
        longPosition.fxRates(),
        longPosition.imports(),
        expected(
            longPosition.expected().cash(),
            longPosition.expected().position(),
            new PortfolioExpected.Dividend(
                AAPL_DIVIDEND_GROSS,
                Math.abs(AAPL_WITHHOLDING_TAX),
                AAPL_DIVIDEND_GROSS + AAPL_WITHHOLDING_TAX,
                0.0),
            longPosition.expected().valuation(),
            null,
            null,
            null));
  }

  /** Creates a transfer pair that is neutral at portfolio level. */
  public static PortfolioTestContext createInternalCashTransferScenario() {
    PortfolioTestContext funded = createFundedPortfolio();
    String transferRef = "internal-transfer-2025-01";
    CashOperation out =
        cashOperation()
            .forAccount(IBKR_USD)
            .transfer(-INTERNAL_TRANSFER_AMOUNT, CurrencyType.USD, transferRef)
            .on(PortfolioTestData.MID_YEAR)
            .build();
    CashOperation in =
        cashOperation()
            .forAccount(CRYPTO_USD)
            .transfer(INTERNAL_TRANSFER_AMOUNT, CurrencyType.USD, transferRef)
            .on(PortfolioTestData.MID_YEAR)
            .build();

    return context(
        funded.accounts(),
        funded.assets(),
        operations(
            funded.operations().initialUsdDeposit(),
            null,
            null,
            null,
            null,
            out,
            in,
            List.of(funded.operations().initialUsdDeposit(), out, in)),
        funded.positions(),
        funded.fxRates(),
        funded.imports(),
        expected(
            funded.expected().cash(),
            null,
            null,
            funded.expected().valuation(),
            new PortfolioExpected.Transfer(
                -INTERNAL_TRANSFER_AMOUNT, INTERNAL_TRANSFER_AMOUNT, 0.0, 0.0),
            null,
            null));
  }

  /** Creates USD, EUR, and PLN accounts with deterministic month-start FX rows. */
  public static PortfolioTestContext createMultiCurrencyScenario() {
    CashOperation usdDeposit =
        cashOperation()
            .forAccount(IBKR_USD)
            .deposit(DEFAULT_USD_DEPOSIT, CurrencyType.USD)
            .on(JANUARY_DEPOSIT_DATE)
            .build();
    CashOperation eurDeposit =
        cashOperation()
            .forAccount(XTB_EUR)
            .deposit(DEFAULT_EUR_DEPOSIT, CurrencyType.EUR)
            .on(JANUARY_DEPOSIT_DATE)
            .build();
    CashOperation plnDeposit =
        cashOperation()
            .forAccount(POLISH_BONDS_PLN)
            .deposit(DEFAULT_PLN_DEPOSIT, CurrencyType.PLN)
            .on(JANUARY_DEPOSIT_DATE)
            .build();
    CurrencyRate eurUsd =
        fxRate()
            .on(PERIOD_START)
            .pair(CurrencyType.EUR, CurrencyType.USD)
            .rate(EUR_USD.get(PERIOD_START))
            .build();
    CurrencyRate plnUsd =
        fxRate()
            .on(PERIOD_START)
            .pair(CurrencyType.PLN, CurrencyType.USD)
            .rate(PLN_USD.get(PERIOD_START))
            .build();

    double eurConverted = DEFAULT_EUR_DEPOSIT * EUR_USD.get(PERIOD_START);
    return context(
        accounts(),
        assets(),
        operations(
            usdDeposit,
            eurDeposit,
            plnDeposit,
            null,
            null,
            null,
            null,
            List.of(usdDeposit, eurDeposit, plnDeposit)),
        positions(null, null, null, List.of(), List.of()),
        fxRates(eurUsd, plnUsd, List.of(eurUsd, plnUsd)),
        imports(null, null),
        expected(
            new PortfolioExpected.CashBalance(
                0.0,
                DEFAULT_USD_DEPOSIT
                    + eurConverted
                    + DEFAULT_PLN_DEPOSIT * PLN_USD.get(PERIOD_START),
                DEFAULT_USD_DEPOSIT
                    + eurConverted
                    + DEFAULT_PLN_DEPOSIT * PLN_USD.get(PERIOD_START)),
            null,
            null,
            null,
            null,
            new PortfolioExpected.MultiCurrencyValue(
                DEFAULT_EUR_DEPOSIT, EUR_USD.get(PERIOD_START), eurConverted, 0.0),
            null));
  }

  /** Creates two import batches with the same checksum for idempotency tests. */
  public static PortfolioTestContext createDuplicateImportScenario() {
    String checksum = "sha256-deterministic-statement";
    ImportHistory first =
        importHistory().id(77L).broker(BrokerType.IBKR).checksum(checksum).build();
    ImportHistory duplicate =
        importHistory().id(78L).broker(BrokerType.IBKR).checksum(checksum).build();

    return context(
        accounts(),
        assets(),
        operations(null, null, null, null, null, null, null, List.of()),
        positions(null, null, null, List.of(), List.of()),
        fxRates(null, null, List.of()),
        imports(first, duplicate),
        expected(
            null,
            null,
            null,
            null,
            null,
            null,
            new PortfolioExpected.DuplicateImport(checksum, 77L, true)));
  }

  private static PortfolioTestContext.Accounts accounts() {
    return new PortfolioTestContext.Accounts(
        account(IBKR_USD).build(),
        account(XTB_EUR).build(),
        account(POLISH_BONDS_PLN).build(),
        account(CRYPTO_USD).build());
  }

  private static PortfolioTestContext.Assets assets() {
    return new PortfolioTestContext.Assets(
        asset(AAPL)
            .withLatestPrice(
                AAPL_PRICES_USD.get(JANUARY_MONTH_END),
                AAPL_PRICES_USD.get(JANUARY_MONTH_END),
                JANUARY_MONTH_END)
            .build(),
        asset(MSFT).withLatestPrice(425.0, 425.0, JANUARY_MONTH_END).build(),
        asset(TSLA).withLatestPrice(250.0, 250.0, JANUARY_MONTH_END).build(),
        asset(SPY).withLatestPrice(500.0, 500.0, JANUARY_MONTH_END).build(),
        asset(SIE_DE).withLatestPrice(170.0, 187.0, JANUARY_MONTH_END).build(),
        asset(PKO_WA).withLatestPrice(60.0, 15.0, JANUARY_MONTH_END).build(),
        asset(IWDA_AS).withLatestPrice(95.0, 104.5, JANUARY_MONTH_END).build(),
        asset(BTC).withLatestPrice(100_000.0, 100_000.0, JANUARY_MONTH_END).build(),
        asset(ETH).withLatestPrice(4_000.0, 4_000.0, JANUARY_MONTH_END).build());
  }

  private static PortfolioTestContext context(
      PortfolioTestContext.Accounts accounts,
      PortfolioTestContext.Assets assets,
      PortfolioTestContext.Operations operations,
      PortfolioTestContext.Positions positions,
      PortfolioTestContext.FxRates fxRates,
      PortfolioTestContext.Imports imports,
      PortfolioTestContext.Expected expected) {
    return new PortfolioTestContext(
        accounts, assets, operations, positions, fxRates, imports, expected);
  }

  private static PortfolioTestContext.Operations operations(
      CashOperation initialUsdDeposit,
      CashOperation initialEurDeposit,
      CashOperation initialPlnDeposit,
      CashOperation aaplDividend,
      CashOperation aaplWithholdingTax,
      CashOperation transferOut,
      CashOperation transferIn,
      List<CashOperation> all) {
    return new PortfolioTestContext.Operations(
        initialUsdDeposit,
        initialEurDeposit,
        initialPlnDeposit,
        aaplDividend,
        aaplWithholdingTax,
        transferOut,
        transferIn,
        all);
  }

  private static PortfolioTestContext.Positions positions(
      OpenedPosition aaplOpen,
      OpenedPosition aaplSecondLot,
      ClosedPosition aaplPartialSale,
      List<OpenedPosition> open,
      List<ClosedPosition> closed) {
    return new PortfolioTestContext.Positions(
        aaplOpen, aaplSecondLot, aaplPartialSale, open, closed);
  }

  private static PortfolioTestContext.FxRates fxRates(
      CurrencyRate eurUsd, CurrencyRate plnUsd, List<CurrencyRate> all) {
    return new PortfolioTestContext.FxRates(eurUsd, plnUsd, all);
  }

  private static PortfolioTestContext.Imports imports(
      ImportHistory first, ImportHistory duplicate) {
    return new PortfolioTestContext.Imports(first, duplicate);
  }

  private static PortfolioTestContext.Expected expected(
      PortfolioExpected.CashBalance cash,
      PortfolioExpected.Position position,
      PortfolioExpected.Dividend dividend,
      PortfolioExpected.Valuation valuation,
      PortfolioExpected.Transfer transfer,
      PortfolioExpected.MultiCurrencyValue multiCurrency,
      PortfolioExpected.DuplicateImport duplicateImport) {
    return new PortfolioTestContext.Expected(
        cash, position, dividend, valuation, transfer, multiCurrency, duplicateImport);
  }
}
