package com.smartbox.investory.investment.infrastructure.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioKpiSummaryRepository;
import com.smartbox.investory.investment.performance.PortfolioMetricsService;
import com.smartbox.investory.investment.reporting.PortfolioPerformanceQuery;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BrokeragePortfolioReadServiceTest {
  @Mock PortfolioMetricsService portfolioMetricsService;
  @Mock PortfolioPerformanceQuery performanceQuery;
  @Mock PortfolioKpiSummaryRepository portfolioKpis;
  @Mock PortfolioAssetAllocationRepository portfolioAllocations;
  @InjectMocks BrokeragePortfolioReadService service;

  @Test
  void readsCurrentSnapshotFromOnePortfolioProjection() {
    ZonedDateTime updatedAt = ZonedDateTime.of(2026, 8, 28, 12, 0, 0, 0, ZoneOffset.UTC);
    PortfolioKpiSummaryEntity kpi =
        new PortfolioKpiSummaryEntity(
            7L,
            "Retirement",
            CurrencyType.PLN,
            1000,
            900,
            200,
            800,
            1000,
            30,
            40,
            50,
            60,
            updatedAt);
    PortfolioAssetAllocationEntity allocation =
        new PortfolioAssetAllocationEntity(
            7L,
            CurrencyType.PLN,
            99L,
            "VWCE",
            BigDecimal.ONE,
            new BigDecimal("800"),
            CurrencyType.PLN,
            new BigDecimal("700"),
            new BigDecimal("800"),
            new BigDecimal("100"),
            updatedAt);
    when(portfolioKpis.findById(7L)).thenReturn(Optional.of(kpi));
    when(portfolioAllocations.findAllByPortfolioId(7L)).thenReturn(List.of(allocation));

    var snapshot = service.currentSnapshot(7L);

    assertThat(snapshot.baseCurrency()).isEqualTo(CurrencyType.PLN);
    assertThat(snapshot.balance()).isEqualByComparingTo("1000");
    assertThat(snapshot.cash()).isEqualByComparingTo("200");
    assertThat(snapshot.dividends()).isEqualByComparingTo("50");
    assertThat(snapshot.interest()).isEqualByComparingTo("60");
    assertThat(snapshot.openPositions())
        .singleElement()
        .satisfies(
            position -> {
              assertThat(position.symbol()).isEqualTo("VWCE");
              assertThat(position.value()).isEqualByComparingTo("800");
            });
    verify(portfolioAllocations).findAllByPortfolioId(7L);
  }

  @Test
  void rejectsUnknownPortfolioInsteadOfFallingBackToSharedFacts() {
    when(portfolioKpis.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.currentSnapshot(7L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown portfolio: 7");
  }
}
