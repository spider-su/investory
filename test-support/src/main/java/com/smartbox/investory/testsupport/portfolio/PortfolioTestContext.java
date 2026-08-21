package com.smartbox.investory.testsupport.portfolio;

import com.smartbox.investory.investment.infrastructure.persistence.AssetEntity;
import com.smartbox.investory.investment.infrastructure.persistence.CashOperationEntity;
import com.smartbox.investory.investment.infrastructure.persistence.ClosedPosition;
import com.smartbox.investory.investment.infrastructure.persistence.CurrencyRateEntity;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPosition;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
import java.util.List;

public record PortfolioTestContext(
    Accounts accounts,
    Assets assets,
    Operations operations,
    Positions positions,
    FxRates fxRates,
    Imports imports,
    Expected expected) {

  public record Accounts(
      AccountEntity ibkrUsd, AccountEntity xtbEur, AccountEntity polishBondsPln, AccountEntity cryptoUsd) {}

  public record Assets(
      AssetEntity aapl,
      AssetEntity msft,
      AssetEntity tsla,
      AssetEntity spy,
      AssetEntity sieDe,
      AssetEntity pkoWa,
      AssetEntity iwdaAs,
      AssetEntity btc,
      AssetEntity eth) {
    public Assets withAapl(AssetEntity replacement) {
      return new Assets(replacement, msft, tsla, spy, sieDe, pkoWa, iwdaAs, btc, eth);
    }
  }

  public record Operations(
      CashOperationEntity initialUsdDeposit,
      CashOperationEntity initialEurDeposit,
      CashOperationEntity initialPlnDeposit,
      CashOperationEntity aaplDividend,
      CashOperationEntity aaplWithholdingTax,
      CashOperationEntity transferOut,
      CashOperationEntity transferIn,
      List<CashOperationEntity> all) {}

  public record Positions(
      OpenedPosition aaplOpen,
      OpenedPosition aaplSecondLot,
      ClosedPosition aaplPartialSale,
      List<OpenedPosition> open,
      List<ClosedPosition> closed) {}

  public record FxRates(CurrencyRateEntity eurUsd, CurrencyRateEntity plnUsd, List<CurrencyRateEntity> all) {}

  public record Imports(ImportHistoryEntity firstImport, ImportHistoryEntity duplicateImport) {}

  public record Expected(
      PortfolioExpected.CashBalance cash,
      PortfolioExpected.PositionEntity position,
      PortfolioExpected.Dividend dividend,
      PortfolioExpected.Valuation valuation,
      PortfolioExpected.Transfer transfer,
      PortfolioExpected.MultiCurrencyValue multiCurrency,
      PortfolioExpected.DuplicateImport duplicateImport) {}
}
