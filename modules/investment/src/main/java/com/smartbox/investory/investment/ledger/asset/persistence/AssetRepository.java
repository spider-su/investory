package com.smartbox.investory.investment.ledger.asset.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository extends JpaRepository<AssetEntity, Long> {

  Optional<AssetEntity> findBySymbol(String symbol);

  default List<AssetEntity> findAllByIbrkIgnoreCase(String ibrk) {
    return findAllByIbkrIgnoreCase(ibrk);
  }

  List<AssetEntity> findAllByIbkrIgnoreCase(String ibkr);

  List<AssetEntity> findAllBySymbolIn(Collection<String> symbols);

  List<AssetEntity> findAllByTickerIn(Collection<String> tickers);
}
