package com.smartbox.investory.testsupport.happyinvestor;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import java.util.List;

/** Semantic handles for the complete reference investor; tests need not search collections. */
public record HappyInvestorContext(
    AccountEntity ibkrUsd,
    AccountEntity xtbUsd,
    AccountEntity xtbPln,
    AccountEntity xtbEur,
    AssetEntity aapl,
    AssetEntity msft,
    AssetEntity vwra,
    AssetEntity nvda,
    AssetEntity amzn,
    AssetEntity meta,
    AssetEntity realtyIncome,
    AssetEntity tsla,
    AssetEntity googl,
    AssetEntity pko,
    AssetEntity wig20Etf,
    AssetEntity spyBenchmark,
    AssetEntity treasury2026,
    AssetEntity treasury2033,
    AssetEntity natgas,
    List<PositionEntity> openPositions,
    List<PositionEntity> closedPositions,
    List<CashOperationEntity> ledger,
    HappyInvestorSimulationSpec simulation) {

  public List<AccountEntity> accounts() {
    return List.of(ibkrUsd, xtbUsd, xtbPln, xtbEur);
  }

  public List<AssetEntity> assets() {
    return List.of(
        aapl,
        msft,
        vwra,
        nvda,
        amzn,
        meta,
        realtyIncome,
        tsla,
        googl,
        pko,
        wig20Etf,
        spyBenchmark,
        treasury2026,
        treasury2033,
        natgas);
  }
}
