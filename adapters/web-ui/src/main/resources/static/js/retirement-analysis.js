(function () {
  const state = window.retirementAnalysis || {};
  const charts = state.charts || {};
  const selected = state.selectedScenario || "BASE";
  const colors = {BASE: "#4dabf7", CONSERVATIVE: "#ff8787", OPTIMISTIC: "#69db7c", CUSTOM: "#ae7bff"};
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
  const options = {responsive: true, interaction: {mode: "index", intersect: false}, plugins: {legend: {position: "top"}, tooltip: {callbacks: {label: c => `${c.dataset.label}: ${money(c.parsed.y)}`}}}, scales: {x: {ticks: {maxTicksLimit: 8}}, y: {beginAtZero: true, ticks: {callback: value => compact(value)}}}};
  const portfolio = document.getElementById("analysis-portfolio");
  if (portfolio) new Chart(portfolio, {type: "line", data: {labels, datasets: lineDatasets("liquidAssets")}, options});
  const funding = (charts.funding || {})[selected] || [];
  const cashFlow = document.getElementById("analysis-cash-flow");
  if (cashFlow) new Chart(cashFlow, {type: "line", data: {labels: funding.map(point => point.year), datasets: [
    {label: "Income", data: funding.map(point => point.passiveIncome == null ? null : point.passiveIncome), borderColor: "#69db7c", pointRadius: 0},
    {label: "Spending", data: funding.map(point => point.plannedSpending == null ? null : point.plannedSpending), borderColor: "#6c757d", pointRadius: 0},
    {label: "Funding need", data: funding.map(point => point.requiredPortfolioFunding == null ? null : point.requiredPortfolioFunding), borderColor: "#206bc4", borderWidth: 3, pointRadius: 0},
    {label: "Unfunded", data: funding.map(point => point.unfundedAmount == null ? null : point.unfundedAmount), borderColor: "#ff922b", borderWidth: 3, pointRadius: 0}
  ]}, options});
  document.querySelectorAll("[data-analysis-tab]").forEach(tab => tab.addEventListener("click", () => {
    document.querySelectorAll("[data-analysis-tab]").forEach(item => {
      const active = item === tab;
      item.classList.toggle("active", active);
      item.setAttribute("aria-selected", active ? "true" : "false");
    });
    document.querySelectorAll("[data-analysis-panel]").forEach(panel => panel.hidden = panel.dataset.analysisPanel !== tab.dataset.analysisTab);
  }));
})();
