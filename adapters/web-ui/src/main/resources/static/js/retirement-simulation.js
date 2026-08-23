// Simulation board presentation only. Financial values come from the server.
(function () {
  document.querySelectorAll('[data-simulation-disclosure]').forEach((item) => {
    item.addEventListener('toggle', () => item.classList.toggle('is-open', item.open));
  });

  const canvas = document.getElementById('simulation-chart');
  const buttons = [...document.querySelectorAll('[data-simulation-chart-mode]')];
  const chartState = window.retirementSimulation || {};
  const chartData = chartState.chartData || {};
  const points = chartData.points || [];
  if (!canvas || !window.Chart || points.length === 0) return;

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
  const lifecycleMarkerPlugin = {
    id: 'simulationLifecycleMarkers',
    afterDraw(chart) {
      const markers = [
        {label: 'Retirement', year: chartData.retirementYear},
        {label: 'Pension', year: chartData.pensionStartYear}
      ].filter(marker => marker.year != null);
      if (!markers.length || !chart.chartArea) return;
      const xScale = chart.scales.x;
      const {top, bottom} = chart.chartArea;
      const ctx = chart.ctx;
      ctx.save();
      ctx.font = '700 10px system-ui, sans-serif';
      ctx.textAlign = 'center';
      markers.forEach(marker => {
        const index = labels.indexOf(marker.year);
        if (index < 0) return;
        const x = xScale.getPixelForValue(index);
        ctx.strokeStyle = colors.text;
        ctx.globalAlpha = .65;
        ctx.setLineDash([4, 4]);
        ctx.beginPath();
        ctx.moveTo(x, top);
        ctx.lineTo(x, bottom);
        ctx.stroke();
        ctx.setLineDash([]);
        ctx.globalAlpha = 1;
        ctx.fillStyle = colors.text;
        ctx.fillText(`${marker.label} · ${marker.year}`, x, Math.max(12, top - 5));
      });
      ctx.restore();
    }
  };
  Chart.register(lifecycleMarkerPlugin);

  const gapSemanticLabel = (value) => value < 0 ? 'Funding gap' : value > 0 ? 'Surplus' : 'Gap / surplus';
  const tooltipLabel = (context) => {
    const sourceValues = context.dataset.semanticValues;
    const value = sourceValues ? sourceValues[context.dataIndex] : context.parsed.y;
    if (value == null) return `${context.dataset.label}: —`;
    const label = sourceValues ? gapSemanticLabel(Number(value)) : context.dataset.label;
    return `${label}: ${tooltipMoney(Math.abs(Number(value)))}`;
  };
  const commonOptions = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {mode: 'index', intersect: false},
    plugins: {
      legend: {position: 'top', labels: {usePointStyle: true, color: colors.text, boxWidth: 8}},
      tooltip: {callbacks: {label: tooltipLabel}}
    },
    scales: {
      x: {ticks: {color: colors.text, maxTicksLimit: 8}, grid: {color: colors.grid}},
      y: {beginAtZero: true, ticks: {color: colors.text, callback: value => compactMoney(value)}, grid: {color: context => context.tick.value === 0 ? colors.text : colors.grid}}
    },
    elements: {line: {tension: .2, borderWidth: 2}, point: {radius: 0, hitRadius: 8, hoverRadius: 4}}
  };
  const line = (label, values, color, options = {}) => ({
    label,
    data: values,
    borderColor: color,
    backgroundColor: color,
    ...options
  });
  const cashFlowData = () => {
    const signedGap = points.map(point => point.gapOrSurplus);
    const gap = line('Gap / surplus', signedGap.map(value => value == null ? null : Math.abs(Number(value))), colors.gap, {borderWidth: 3, borderDash: [5, 3]});
    gap.semanticValues = signedGap;
    return {
      labels,
      datasets: [
        line('Spending', points.map(point => point.spending), colors.spending),
        line('Income', points.map(point => point.income), colors.income),
        gap
      ]
    };
  };
  const liquidCapitalData = () => ({
    labels,
    datasets: [
      line('Bonds', points.map(point => point.bondsEnd), colors.bonds),
      line('Equities', points.map(point => point.equitiesEnd), colors.equities)
    ]
  });
  const modes = {
    CASH_FLOW: {
      title: 'Spending & income',
      help: 'Annual spending, available income and resulting surplus or deficit.',
      aria: 'Spending, income, and gap or surplus by year',
      data: cashFlowData
    },
    LIQUID_CAPITAL: {
      title: 'Liquid capital',
      help: 'Expected end-of-year liquid investment capital.',
      aria: 'Expected end-of-year bonds and equities by year',
      data: liquidCapitalData
    }
  };
  const storageKey = 'investory.simulation.chartMode';
  const readMode = () => {
    try {
      const value = window.localStorage.getItem(storageKey);
      return modes[value] ? value : 'CASH_FLOW';
    } catch (ignored) {
      return 'CASH_FLOW';
    }
  };
  const saveMode = (mode) => {
    try { window.localStorage.setItem(storageKey, mode); } catch (ignored) { /* storage is optional */ }
  };
  let chart;
  const title = document.getElementById('simulation-chart-title');
  const help = document.getElementById('simulation-chart-help');
  const render = (mode) => {
    const selected = modes[mode] ? mode : 'CASH_FLOW';
    const definition = modes[selected];
    if (chart) chart.destroy();
    chart = new Chart(canvas, {type: 'line', data: definition.data(), options: commonOptions});
    canvas.setAttribute('aria-label', definition.aria);
    if (title) title.textContent = definition.title;
    if (help) help.textContent = definition.help;
    buttons.forEach(button => {
      const active = button.dataset.simulationChartMode === selected;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-selected', String(active));
    });
  };
  buttons.forEach(button => button.addEventListener('click', () => {
    const mode = button.dataset.simulationChartMode;
    saveMode(mode);
    render(mode);
  }));
  render(readMode());
})();
