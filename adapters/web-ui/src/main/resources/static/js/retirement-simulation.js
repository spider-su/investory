import {readPageData} from './page-data.js';

// Simulation board presentation only. Financial values come from the server.
let simulationDestroy = () => {};
export function initSimulationCharts() {
(function () {
  document.querySelectorAll('[data-simulation-disclosure]').forEach((item) => {
    item.addEventListener('toggle', () => item.classList.toggle('is-open', item.open));
  });

  const canvas = document.getElementById('simulation-chart');
  const liquidCanvas = document.getElementById('liquid-capital-chart');
  const switcher = document.querySelector('[data-simulation-chart-mode]');
  const panels = [...document.querySelectorAll('[data-chart-panel]')];
  const chartState = readPageData('simulation-page-data');
  const chartData = chartState.chartData || {};
  const points = chartData.points || [];
  if ((!canvas && !liquidCanvas) || !window.Chart || points.length === 0) return;

  const token = (name, fallback) =>
    getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
  const themeColors = () => ({
    text: token('--iv-chart-axis', token('--iv-text-muted', '#6b7488')),
    grid: token('--iv-chart-grid', 'rgba(31, 31, 31, .10)'),
    spending: token('--iv-negative', '#db2031'),
    income: token('--iv-positive', '#00916d'),
    gap: token('--iv-accent', '#376cd5'),
    bonds: token('--iv-asset-fixed-income', '#376cd5'),
    equities: token('--iv-asset-equity', '#376cd5')
  });
  let colors = themeColors();
  const currency = chartState.currency || 'PLN';
  const chartFont = getComputedStyle(document.body).fontFamily || 'system-ui, sans-serif';
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
      ctx.font = `700 11px ${chartFont}`;
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
      legend: {position: 'top', labels: {usePointStyle: true, color: colors.text, boxWidth: 8, font: {family: chartFont, size: 12}}},
      tooltip: {callbacks: {label: tooltipLabel}}
    },
    scales: {
      x: {ticks: {color: colors.text, maxTicksLimit: 8, font: {family: chartFont, size: 12}}, grid: {color: colors.grid}},
      y: {beginAtZero: true, ticks: {color: colors.text, font: {family: chartFont, size: 12}, callback: value => compactMoney(value)}, grid: {color: context => context.tick.value === 0 ? colors.text : colors.grid}}
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
  let chart;
  let liquidChart;

  const applyChartTheme = chartInstance => {
    if (!chartInstance) return;

    colors = themeColors();
    chartInstance.options.plugins.legend.labels.color = colors.text;
    chartInstance.options.scales.x.ticks.color = colors.text;
    chartInstance.options.scales.x.grid.color = colors.grid;
    chartInstance.options.scales.y.ticks.color = colors.text;
    chartInstance.options.scales.y.grid.color =
      context => context.tick.value === 0 ? colors.text : colors.grid;

    chartInstance.data.datasets.forEach(dataset => {
      const color = {
        'Spending': colors.spending,
        'Income': colors.income,
        'Gap / surplus': colors.gap,
        'Bonds': colors.bonds,
        'Equities': colors.equities
      }[dataset.label];
      if (!color) return;
      dataset.borderColor = color;
      dataset.backgroundColor = color;
    });

    chartInstance.update('none');
  };

  const onThemeChange = () => {
    applyChartTheme(chart);
    applyChartTheme(liquidChart);
  };
  const onResize = () => { chart?.resize(); liquidChart?.resize(); };
  window.addEventListener('investory:themechange', onThemeChange);
  const render = (mode) => {
    if (mode === 'LIQUID_CAPITAL' && liquidCanvas && !liquidChart) {
      liquidChart = new Chart(liquidCanvas, {type: 'line', data: liquidCapitalData(), options: {...commonOptions, elements: {...commonOptions.elements, line: {tension: .2, borderWidth: 2}, point: commonOptions.elements.point}}});
    } else if (mode === 'CASH_FLOW' && canvas && !chart) {
      chart = new Chart(canvas, {type: 'line', data: cashFlowData(), options: commonOptions, plugins: [lifecycleMarkerPlugin]});
    }
    (mode === 'LIQUID_CAPITAL' ? liquidChart : chart)?.resize();
  };
  const selectMode = (mode) => {
    panels.forEach(panel => {
      const active = panel.dataset.chartPanel === mode;
      panel.hidden = !active;
    });
    switcher?.querySelectorAll('[data-chart-mode]').forEach(button => {
      const active = button.dataset.chartMode === mode;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-selected', String(active));
    });
    render(mode);
  };
  switcher?.querySelectorAll('[data-chart-mode]').forEach(button => button.addEventListener('click', () => selectMode(button.dataset.chartMode)));
  window.addEventListener('resize', onResize);
  selectMode('LIQUID_CAPITAL');
  simulationDestroy = () => {
    window.removeEventListener('investory:themechange', onThemeChange);
    window.removeEventListener('resize', onResize);
    chart?.destroy();
    liquidChart?.destroy();
    simulationDestroy = () => {};
  };
})();
}
export function destroySimulationCharts() { simulationDestroy(); }
