// Simulation board only owns presentation interactions. Projection values come from the server.
(function () {
  document.querySelectorAll('[data-simulation-disclosure]').forEach((item) => {
    item.addEventListener('toggle', () => item.classList.toggle('is-open', item.open));
  });

  const chartState = window.retirementSimulation || {};
  const chartData = chartState.chartData || {};
  const points = chartData.points || [];
  if (!window.Chart || points.length === 0) return;

  const css = getComputedStyle(document.documentElement);
  const token = (name, fallback) => css.getPropertyValue(name).trim() || fallback;
  const colors = {
    text: token('--iv-text-muted', '#6b7488'),
    grid: token('--iv-chart-grid', 'rgba(148, 160, 184, .18)'),
    spending: token('--iv-negative', '#dc2626'),
    income: token('--iv-positive', '#16a34a'),
    gap: token('--iv-primary', '#4f46e5'),
    bonds: token('--iv-asset-fixed-income', '#2563eb'),
    equities: token('--iv-asset-equity', '#4f46e5')
  };
  const currency = chartState.currency || 'PLN';

  // Presentation-only formatter matching UiPresentation.compactMoney().
  const compactMoney = (value) => {
    if (value == null || Number.isNaN(Number(value))) return '—';
    const amount = Number(value);
    const absolute = Math.abs(amount);
    if (absolute >= 1000000) return `${(amount / 1000000).toLocaleString('en-US', {maximumFractionDigits: 2})}M`;
    if (absolute >= 1000) return `${(amount / 1000).toLocaleString('en-US', {minimumFractionDigits: 1, maximumFractionDigits: 1})}K`;
    return amount.toLocaleString('en-US', {maximumFractionDigits: 0});
  };
  const tooltipMoney = (value) => `${compactMoney(value)} ${currency}`;
  const labels = points.map(point => point.year);
  const commonOptions = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {mode: 'index', intersect: false},
    plugins: {
      legend: {position: 'top', labels: {usePointStyle: true, color: colors.text, boxWidth: 8}},
      tooltip: {callbacks: {label: context => `${context.dataset.label}: ${tooltipMoney(context.parsed.y)}`}}
    },
    scales: {
      x: {ticks: {color: colors.text, maxTicksLimit: 8}, grid: {color: colors.grid}},
      y: {ticks: {color: colors.text, callback: value => compactMoney(value)}, grid: {color: context => context.tick.value === 0 ? colors.text : colors.grid}}
    },
    elements: {line: {tension: .2, borderWidth: 2}, point: {radius: 0, hitRadius: 8, hoverRadius: 4}}
  };
  const line = (label, key, color, options) => ({
    label,
    data: points.map(point => point[key]),
    borderColor: color,
    backgroundColor: color,
    ...options
  });

  const spendingIncome = document.getElementById('simulation-spending-income-chart');
  if (spendingIncome) {
    new Chart(spendingIncome, {
      type: 'line',
      data: {labels, datasets: [
        line('Spending', 'spending', colors.spending),
        line('Income', 'income', colors.income),
        line('Gap / surplus', 'gapOrSurplus', colors.gap, {borderWidth: 3, borderDash: [5, 3]})
      ]},
      options: commonOptions
    });
  }

  const liquidCapital = document.getElementById('simulation-liquid-capital-chart');
  if (liquidCapital) {
    new Chart(liquidCapital, {
      type: 'line',
      data: {labels, datasets: [
        line('Bonds', 'bondsEnd', colors.bonds),
        line('Equities', 'equitiesEnd', colors.equities)
      ]},
      options: commonOptions
    });
  }
})();
