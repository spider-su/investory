package com.smartbox.investory.investment.performance;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
public class PortfolioProperties {

  @Value("${app.portfolio.data-quality-issues-enabled:false}")
  private boolean dataQualityIssuesEnabled;

  @Value("${app.portfolio.dashboard-enrichment-enabled:false}")
  private boolean dashboardEnrichmentEnabled;
}
