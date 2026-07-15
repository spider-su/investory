package com.example.demo.infrastructure.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

  Optional<Asset> findBySymbol(String symbol);

  List<Asset> findAllByIbrkIgnoreCase(String ibrk);

  List<Asset> findAllBySymbolIn(Collection<String> symbols);

  List<Asset> findAllByTickerIn(Collection<String> tickers);
}

