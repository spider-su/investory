package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.PortfolioRelevance;

public interface PortfolioContextPort {

  PortfolioRelevance relevanceOf(String ticker);
}
