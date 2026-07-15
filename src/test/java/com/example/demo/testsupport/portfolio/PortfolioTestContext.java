package com.example.demo.testsupport.portfolio;

import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.CurrencyRate;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.account.Account;
import com.example.demo.infrastructure.repository.imports.ImportHistory;
import java.util.List;

public record PortfolioTestContext(
    Accounts accounts,
    Assets assets,
    Operations operations,
    Positions positions,
    FxRates fxRates,
    Imports imports,
    Expected expected) {

  public record Accounts(Account ibkrUsd, Account xtbEur, Account polishBondsPln, Account cryptoUsd) {}

  public record Assets(
      Asset aapl,
      Asset msft,
      Asset tsla,
      Asset spy,
      Asset sieDe,
      Asset pkoWa,
      Asset iwdaAs,
      Asset btc,
      Asset eth) {
    public Assets withAapl(Asset replacement) {
      return new Assets(replacement, msft, tsla, spy, sieDe, pkoWa, iwdaAs, btc, eth);
    }
  }

  public record Operations(
      CashOperation initialUsdDeposit,
      CashOperation initialEurDeposit,
      CashOperation initialPlnDeposit,
      CashOperation aaplDividend,
      CashOperation aaplWithholdingTax,
      CashOperation transferOut,
      CashOperation transferIn,
      List<CashOperation> all) {}

  public record Positions(
      OpenedPosition aaplOpen,
      OpenedPosition aaplSecondLot,
      ClosedPosition aaplPartialSale,
      List<OpenedPosition> open,
      List<ClosedPosition> closed) {}

  public record FxRates(CurrencyRate eurUsd, CurrencyRate plnUsd, List<CurrencyRate> all) {}

  public record Imports(ImportHistory firstImport, ImportHistory duplicateImport) {}

  public record Expected(
      PortfolioExpected.CashBalance cash,
      PortfolioExpected.Position position,
      PortfolioExpected.Dividend dividend,
      PortfolioExpected.Valuation valuation,
      PortfolioExpected.Transfer transfer,
      PortfolioExpected.MultiCurrencyValue multiCurrency,
      PortfolioExpected.DuplicateImport duplicateImport) {}
}
