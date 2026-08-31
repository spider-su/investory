package com.smartbox.investory.ui.presentation;

/** Common view data passed to the planning header fragment. */
public record PlanningHeaderView(
    String activePage,
    Object portfolioId,
    String summaryLabel,
    String kpi1Label,
    Object kpi1Value,
    Object kpi1Meta,
    String kpi2Label,
    Object kpi2Value,
    Object kpi2Meta,
    String kpi3Label,
    Object kpi3Value,
    Object kpi3Meta,
    Object currency,
    String actionMode,
    Object contextPlanId,
    Object contextScenario) {
  public static PlanningHeaderView of(
      String activePage,
      Object portfolioId,
      String summaryLabel,
      String kpi1Label,
      Object kpi1Value,
      Object kpi1Meta,
      String kpi2Label,
      Object kpi2Value,
      Object kpi2Meta,
      String kpi3Label,
      Object kpi3Value,
      Object kpi3Meta,
      Object currency,
      String actionMode,
      Object contextPlanId,
      Object contextScenario) {
    return new PlanningHeaderView(
        activePage,
        portfolioId,
        summaryLabel,
        kpi1Label,
        kpi1Value,
        kpi1Meta,
        kpi2Label,
        kpi2Value,
        kpi2Meta,
        kpi3Label,
        kpi3Value,
        kpi3Meta,
        currency,
        actionMode,
        contextPlanId,
        contextScenario);
  }
}
