package com.smartbox.investory.investment.infrastructure.persistence.position;

import com.smartbox.investory.investment.infrastructure.persistence.ReadOnlyRepository;
import java.util.List;

public interface CurrentOpenPositionValueRepository
    extends ReadOnlyRepository<CurrentOpenPositionValueEntity, Long> {
  List<CurrentOpenPositionValueEntity> findAllByPortfolioId(Long portfolioId);
}
