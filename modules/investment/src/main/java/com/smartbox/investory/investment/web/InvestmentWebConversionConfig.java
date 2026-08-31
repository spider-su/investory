package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import com.smartbox.investory.investment.api.importing.ImportSource;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.PerformanceAggregation;
import com.smartbox.investory.investment.api.reporting.PerformanceMetric;
import com.smartbox.investory.investment.api.reporting.PerformanceStyle;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** HTTP conversion uses the same strict parser as JSON public contracts. */
@Configuration
public class InvestmentWebConversionConfig implements WebMvcConfigurer {
  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(String.class, DashboardPeriod.class, DashboardPeriod::fromUrlValue);
    registry.addConverter(
        String.class, PerformanceAggregation.class, PerformanceAggregation::fromWireValue);
    registry.addConverter(String.class, PerformanceMetric.class, PerformanceMetric::fromWireValue);
    registry.addConverter(String.class, PerformanceStyle.class, PerformanceStyle::fromWireValue);
    registry.addConverter(String.class, ImportBroker.class, ImportBroker::fromWireValue);
    registry.addConverter(String.class, ImportSource.class, ImportSource::fromWireValue);
  }
}
