package com.smartbox.investory.rag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.api.reporting.model.OverviewView;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PortfolioProfitabilityAnswerTest {
  @Test
  void interpretsActualFollowUpWithoutRelyingOnThePreviousAnswerText() {
    var answerer = new PortfolioProfitabilityAnswer(mock(InvestmentDashboardApi.class));
    assertTrue(answerer.supports("is it profitable for all time I have it"));
    assertTrue(answerer.supports("Has my portfolio made money since inception?"));
    assertTrue(answerer.supports("What is my overall profit?"));
    assertFalse(answerer.supports("WHAT is my portfolio value now"));
    assertFalse(answerer.supports("Should I buy more stocks?"));
  }

  @Test
  void takesAllTimeProfitFromPortfolioScopedMaxDashboard() {
    var api = mock(InvestmentDashboardApi.class);
    var page = mock(InvestmentDashboardApi.DashboardPageView.class);
    var overview = mock(OverviewView.class);
    when(api.loadDashboard(any())).thenReturn(page);
    when(page.overview()).thenReturn(overview);
    when(overview.totalProfit()).thenReturn(new BigDecimal("123.45"));
    when(overview.baseCurrency()).thenReturn(CurrencyType.PLN);

    var answer = new PortfolioProfitabilityAnswer(api).answer(21);
    var query = ArgumentCaptor.forClass(InvestmentDashboardApi.DashboardQuery.class);
    verify(api).loadDashboard(query.capture());
    assertEquals(21L, query.getValue().portfolioId());
    assertEquals(DashboardPeriod.MAX, query.getValue().period());
    assertEquals("For the selected portfolio, the all-time investment result (MAX reporting period) is +123.45 PLN. The portfolio is profitable over that period. This is investment profit/loss, not the current portfolio value. [live:investment-performance]", answer.answer());
    assertEquals(java.util.List.of("live:investment-performance"), answer.sources());
  }

  @Test
  void negativeAndUnavailableResultsDoNotBecomeInventedProfit() {
    var api = mock(InvestmentDashboardApi.class);
    var page = mock(InvestmentDashboardApi.DashboardPageView.class);
    var overview = mock(OverviewView.class);
    when(api.loadDashboard(any())).thenReturn(page);
    when(page.overview()).thenReturn(overview);
    when(overview.totalProfit()).thenReturn(new BigDecimal("-85.25"), (BigDecimal) null);
    when(overview.baseCurrency()).thenReturn(CurrencyType.PLN);
    var reader = new PortfolioProfitabilityAnswer(api);
    assertTrue(reader.answer(21).answer().contains("at a loss"));
    assertTrue(reader.answer(21).sources().isEmpty());
  }
}
