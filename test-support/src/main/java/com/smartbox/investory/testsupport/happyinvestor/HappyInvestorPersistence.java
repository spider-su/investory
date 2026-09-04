package com.smartbox.investory.testsupport.happyinvestor;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;

/** Persistence facade for integration tests that need the complete reference ledger. */
public final class HappyInvestorPersistence {
  private final AccountRepository accounts;
  private final AssetRepository assets;
  private final CashOperationRepository cashOperations;
  private final PositionRepository positions;

  public HappyInvestorPersistence(
      AccountRepository accounts,
      AssetRepository assets,
      CashOperationRepository cashOperations,
      PositionRepository positions) {
    this.accounts = accounts;
    this.assets = assets;
    this.cashOperations = cashOperations;
    this.positions = positions;
  }

  public HappyInvestorContext persist(HappyInvestorContext investor) {
    accounts.saveAll(investor.accounts());
    assets.saveAll(investor.assets());
    cashOperations.saveAll(investor.ledger());
    positions.saveAll(investor.openPositions());
    positions.saveAll(investor.closedPositions());
    return investor;
  }
}
