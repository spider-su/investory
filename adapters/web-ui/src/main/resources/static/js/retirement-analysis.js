let retirementAnalysisDestroy = () => {};

export function initRetirementAnalysis() {
(function () {
  const state = window.retirementAnalysis || {};
  const charts = state.charts || {};
  const selected = state.selectedScenario || "BASE";
  const token = (name, fallback) =>
    getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
  const chartFont = getComputedStyle(document.body).fontFamily || "system-ui, sans-serif";
  const themeColors = () => ({
    BASE: token("--iv-blue", "#376cd5"),
    CONSERVATIVE: token("--iv-negative", "#db2031"),
    OPTIMISTIC: token("--iv-positive", "#00916d"),
    CUSTOM: token("--iv-asset-other", "#8b5cf6"),
    text: token("--iv-chart-axis", token("--iv-text-muted", "#6b7488")),
    grid: token("--iv-chart-grid", "rgba(31, 31, 31, .10)")
  });
  let colors = themeColors();
  const compact = value => {
    const amount = Number(value);
    const absolute = Math.abs(amount);
    if (absolute >= 1000000) return `${(amount / 1000000).toFixed(absolute >= 10000000 ? 0 : 1).replace(/\.0$/, "")}M`;
    if (absolute >= 1000) return `${(amount / 1000).toFixed(absolute >= 100000 ? 0 : 1).replace(/\.0$/, "")}K`;
    return new Intl.NumberFormat("en-US", {maximumFractionDigits: 0}).format(amount);
  };
  const money = value => value == null ? "—" : `${state.currency || "PLN"} ${compact(value)}`;
  const balances = charts.balances || {};
  const labels = (balances.BASE || []).map(point => point.year);
  const lineDatasets = property => Object.keys(balances).map(scenario => ({
    label: scenario.charAt(0) + scenario.slice(1).toLowerCase(),
    data: (balances[scenario] || []).map(point => point[property] == null ? null : point[property]),
    borderColor: colors[scenario] || "#adb5bd",
    borderDash: scenario === "CONSERVATIVE" ? [7, 4] : scenario === "OPTIMISTIC" ? [2, 4] : scenario === "CUSTOM" ? [5, 2] : [],
    borderWidth: scenario === "BASE" ? 3 : 2,
    pointRadius: 0,
    tension: .2
  }));
  const options = {responsive: true, interaction: {mode: "index", intersect: false}, plugins: {legend: {position: "top", labels: {usePointStyle: true, color: colors.text, font: {family: chartFont, size: 12}}}, tooltip: {callbacks: {label: c => `${c.dataset.label}: ${money(c.parsed.y)}`}}}, scales: {x: {ticks: {color: colors.text, maxTicksLimit: 8, font: {family: chartFont, size: 12}}, grid: {color: colors.grid}}, y: {beginAtZero: true, ticks: {color: colors.text, font: {family: chartFont, size: 12}, callback: value => compact(value)}, grid: {color: colors.grid}}}};
  const portfolio = document.getElementById("analysis-portfolio");
  const portfolioChart = portfolio
    ? new Chart(portfolio, {type: "line", data: {labels, datasets: lineDatasets("liquidAssets")}, options})
    : null;
  const funding = (charts.funding || {})[selected] || [];
  const cashFlow = document.getElementById("analysis-cash-flow");
  const cashFlowChart = cashFlow ? new Chart(cashFlow, {type: "line", data: {labels: funding.map(point => point.year), datasets: [
    {label: "Income", data: funding.map(point => point.passiveIncome == null ? null : point.passiveIncome), borderColor: token("--iv-positive", "#00916d"), pointRadius: 0},
    {label: "Spending", data: funding.map(point => point.plannedSpending == null ? null : point.plannedSpending), borderColor: token("--iv-text-muted", "rgba(31,31,31,.65)"), pointRadius: 0},
    {label: "Funding need", data: funding.map(point => point.requiredPortfolioFunding == null ? null : point.requiredPortfolioFunding), borderColor: token("--iv-accent", "#376cd5"), borderWidth: 3, pointRadius: 0},
    {label: "Unfunded", data: funding.map(point => point.unfundedAmount == null ? null : point.unfundedAmount), borderColor: token("--iv-warning", "#d37100"), borderWidth: 3, pointRadius: 0}
  ]}, options}) : null;

  const applyChartTheme = chart => {
    if (!chart) return;

    colors = themeColors();
    chart.options.plugins.legend.labels.color = colors.text;
    chart.options.scales.x.ticks.color = colors.text;
    chart.options.scales.x.grid.color = colors.grid;
    chart.options.scales.y.ticks.color = colors.text;
    chart.options.scales.y.grid.color = colors.grid;

    chart.data.datasets.forEach(dataset => {
      const color = {
        Base: colors.BASE,
        Conservative: colors.CONSERVATIVE,
        Optimistic: colors.OPTIMISTIC,
        Custom: colors.CUSTOM,
        Income: token("--iv-positive", "#00916d"),
        Spending: token("--iv-text-muted", "rgba(31,31,31,.65)"),
        "Funding need": token("--iv-accent", "#376cd5"),
        Unfunded: token("--iv-warning", "#d37100")
      }[dataset.label];
      if (color) dataset.borderColor = color;
    });

    chart.update("none");
  };

  const onThemeChange = () => {
    applyChartTheme(portfolioChart);
    applyChartTheme(cashFlowChart);
  };
  window.addEventListener("investory:themechange", onThemeChange);
  const selectTab = tab => {
    document.querySelectorAll("[data-analysis-tab]").forEach(item => {
      const active = item === tab;
      item.classList.toggle("active", active);
      item.setAttribute("aria-selected", active ? "true" : "false");
    });
    document.querySelectorAll("[data-analysis-panel]").forEach(panel => {
      const active = panel.dataset.analysisPanel === tab.dataset.analysisTab;
      panel.hidden = !active;
      panel.setAttribute("aria-hidden", active ? "false" : "true");
    });
  };
  document.querySelectorAll("[data-analysis-tab]").forEach(tab => {
    tab.addEventListener("click", () => selectTab(tab));
    tab.addEventListener("keydown", event => {
      if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
        const tabs = [...document.querySelectorAll("[data-analysis-tab]")];
        const step = event.key === "ArrowRight" ? 1 : -1;
        tabs[(tabs.indexOf(tab) + step + tabs.length) % tabs.length].focus();
      }
      if (event.key === "Enter" || event.key === " ") { event.preventDefault(); selectTab(tab); }
    });
  });
  retirementAnalysisDestroy = () => {
    window.removeEventListener("investory:themechange", onThemeChange);
    portfolioChart?.destroy();
    cashFlowChart?.destroy();
    retirementAnalysisDestroy = () => {};
  };
})();
}

export function destroyRetirementAnalysis() {
  retirementAnalysisDestroy();
}
