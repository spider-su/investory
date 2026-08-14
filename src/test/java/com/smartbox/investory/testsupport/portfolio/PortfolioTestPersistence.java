package com.smartbox.investory.testsupport.portfolio;

import com.smartbox.investory.infrastructure.repository.AssetRepository;
import com.smartbox.investory.infrastructure.repository.CashOperationRepository;
import com.smartbox.investory.infrastructure.repository.ClosedPositionRepository;
import com.smartbox.investory.infrastructure.repository.CurrencyRateRepository;
import com.smartbox.investory.infrastructure.repository.OpenedPositionRepository;
import com.smartbox.investory.infrastructure.repository.account.AccountRepository;

/** Small persistence facade for repository/integration tests using PortfolioScenarios. */
public final class PortfolioTestPersistence {

  private final AccountRepository accountRepository;
  private final AssetRepository assetRepository;
  private final CashOperationRepository cashOperationRepository;
  private final OpenedPositionRepository openedPositionRepository;
  private final ClosedPositionRepository closedPositionRepository;
  private final CurrencyRateRepository currencyRateRepository;

  public PortfolioTestPersistence(
      AccountRepository accountRepository,
      AssetRepository assetRepository,
      CashOperationRepository cashOperationRepository,
      OpenedPositionRepository openedPositionRepository,
      ClosedPositionRepository closedPositionRepository,
      CurrencyRateRepository currencyRateRepository) {
    this.accountRepository = accountRepository;
    this.assetRepository = assetRepository;
    this.cashOperationRepository = cashOperationRepository;
    this.openedPositionRepository = openedPositionRepository;
    this.closedPositionRepository = closedPositionRepository;
    this.currencyRateRepository = currencyRateRepository;
  }

  public PortfolioTestContext persist(PortfolioTestContext context) {
    accountRepository.saveAll(
        java.util.stream.Stream.of(
                context.accounts().ibkrUsd(),
                context.accounts().xtbEur(),
                context.accounts().polishBondsPln(),
                context.accounts().cryptoUsd())
            .filter(java.util.Objects::nonNull)
            .toList());
    assetRepository.saveAll(
        java.util.stream.Stream.of(
                context.assets().aapl(),
                context.assets().msft(),
                context.assets().tsla(),
                context.assets().spy(),
                context.assets().sieDe(),
                context.assets().pkoWa(),
                context.assets().iwdaAs(),
                context.assets().btc(),
                context.assets().eth())
            .filter(java.util.Objects::nonNull)
            .toList());
    cashOperationRepository.saveAll(context.operations().all());
    openedPositionRepository.saveAll(context.positions().open());
    closedPositionRepository.saveAll(context.positions().closed());
    currencyRateRepository.saveAll(context.fxRates().all());
    return context;
  }
}
