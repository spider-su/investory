package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.HistoricalPortfolioActualsReader;
import com.smartbox.investory.investment.api.reporting.HistoricalPortfolioYear;
import com.smartbox.investory.retirement.api.model.PlanningMetric;
import com.smartbox.investory.retirement.api.model.PlanningMetricValue;
import com.smartbox.investory.retirement.planning.application.PlanningMetricDerivationService;
import com.smartbox.investory.retirement.planning.input.HistoricalLongTermAssetYearSource;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanningMetricDerivationServiceTest {
  private final HistoricalPortfolioActualsReader historicalPortfolio = mock();
  private final HistoricalLongTermAssetYearSource historicalLongTermAssets = mock();
  private final PlanningMetricDerivationService service =
      new PlanningMetricDerivationService(historicalPortfolio, historicalLongTermAssets);

  @Test
  void completeHistoricalMarketFactsBecomeAccountingDerivedPlanningMetrics() {
    when(historicalPortfolio.read(7L, 2025))
        .thenReturn(
            new HistoricalPortfolioYear(
                true,
                bd("900"),
                bd("1200"),
                bd("80"),
                bd("300"),
                bd("50"),
                bd("250"),
                bd("0"),
                bd("0.10")));

    Map<PlanningMetric, PlanningMetricValue> result = service.historicalMarket(7L, 2025);

    assertThat(result)
        .containsKeys(
            PlanningMetric.MARKET_ASSETS,
            PlanningMetric.MARKET_INCOME,
            PlanningMetric.MARKET_WITHDRAWAL,
            PlanningMetric.MARKET_RETURN);
    assertThat(result.get(PlanningMetric.MARKET_ASSETS).value()).isEqualByComparingTo("1200");
    assertThat(result.get(PlanningMetric.MARKET_RETURN).value()).isEqualByComparingTo("0.10");
  }

  @Test
  void incompleteHistoricalMarketFactsProduceNoSyntheticValues() {
    when(historicalPortfolio.read(7L, 2025)).thenReturn(HistoricalPortfolioYear.incomplete());

    assertThat(service.historicalMarket(7L, 2025)).isEmpty();
  }

  @Test
  void unavailableLongTermFactsRemainExplicitlyUnavailable() {
    when(historicalLongTermAssets.read(7L, 2025))
        .thenReturn(
            new HistoricalLongTermAssetYearSource.HistoricalLongTermAssetYear(
                true, bd("70"), false, null, true, bd("500"), false, null, false, null));

    var result = service.historicalLongTermAssets(7L, 2025);

    assertThat(result.get(PlanningMetric.RENTAL_INCOME).value()).isEqualByComparingTo("70");
    assertThat(result.get(PlanningMetric.REAL_ESTATE).value()).isNull();
    assertThat(result.get(PlanningMetric.REAL_ESTATE).source().name()).isEqualTo("UNAVAILABLE");
    assertThat(result.get(PlanningMetric.BOND_VALUE).value()).isEqualByComparingTo("500");
  }

  @Test
  void missingLongTermSourceReturnsEmptyFacts() {
    PlanningMetricDerivationService withoutLongTerm =
        new PlanningMetricDerivationService(historicalPortfolio, null);

    assertThat(withoutLongTerm.historicalLongTermAssets(7L, 2025)).isEmpty();
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
