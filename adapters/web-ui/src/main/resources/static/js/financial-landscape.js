(() => {
  'use strict';
  const data = window.financialLandscape || {};
  const numeric = value => {
    if (value === null || value === undefined || value === '') return null;
    const result = Number(value);
    return Number.isFinite(result) ? result : null;
  };
  const income = numeric(data.annualIncome);
  const expenses = numeric(data.annualExpenses);
  const cashIncome = numeric(data.annualCashIncome);
  const marketResult = numeric(data.annualMarketResult);
  const currency = data.currency || 'PLN';
  // Mirrors FinancialPresentation.compactMoneyTrimmed(): up to 3 display digits,
  // K/M suffixes, HALF_UP rounding and a rounded 1000K -> 1.00M rollover.
  const compactNumber = value => {
    const abs = Math.abs(value);
    if (abs < 1000) return new Intl.NumberFormat('en-US', {maximumFractionDigits: 0}).format(Math.round(abs));
    const render = (divisor, suffix) => {
      const scaled = abs / divisor;
      const digits = scaled < 10 ? 2 : 1;
      const rounded = Math.round((scaled + Number.EPSILON) * 10 ** digits) / 10 ** digits;
      return {rounded, text: String(rounded) + suffix};
    };
    if (abs >= 1000000) return render(1000000, 'M').text;
    const thousands = render(1000, 'K');
    return thousands.rounded >= 1000 ? render(1000000, 'M').text : thousands.text;
  };
  const format = value => {
    if (value === null || !Number.isFinite(value)) return 'Unavailable';
    const sign = value < 0 ? '−' : '';
    return sign + compactNumber(value) + ' ' + currency;
  };
  const compact = !!document.querySelector('.fl-compact');
  const compactValue = value => value === null || !Number.isFinite(value) ? '—' : (value < 0 ? '−' : '') + compactNumber(value);
  const rate = value => value === null ? 'Unavailable' : new Intl.NumberFormat('en-GB', {minimumFractionDigits: 2, maximumFractionDigits: 3}).format(value) + ' ' + currency;
  const set = (id, value) => {const el = document.getElementById(id); if (el) el.textContent = value;};
  set('fl-income', compact ? compactValue(income === null ? null : income / 12) : format(income === null ? null : income / 12) + ' / month');
  set('fl-cash-income', cashIncome === null ? 'Unavailable' : format(cashIncome / 12) + ' / mo');
  set('fl-market-result', marketResult === null ? 'Unavailable' : format(marketResult / 12) + ' / mo');
  set('fl-expenses', compact ? compactValue(expenses === null ? null : expenses / 12) : format(expenses === null ? null : expenses / 12) + ' / month');
  set('fl-income-rate', income === null ? 'No planning income' : rate(income / (365.2425 * 24 * 60)) + ' / min');
  set('fl-expense-rate', expenses === null ? 'No planning expenses' : rate(expenses / (365.2425 * 24 * 60)) + ' / min');
  // Market-inclusive economic balance is distinct from spendable planning cash.
  const net = income === null || expenses === null ? null : (income - expenses) / 12;
  const cashNet = cashIncome === null || expenses === null ? null : (cashIncome - expenses) / 12;
  set('fl-net', net === null ? 'Unavailable' : (net > 0 ? '+' : '') + format(net) + ' / month');
  set('fl-cash-net', cashNet === null ? 'Cash funding unavailable' : 'Cash funding: ' + (cashNet > 0 ? '+' : '') + format(cashNet) + ' / month');
  set('fl-base', cashIncome === null ? 'Unavailable' : format(cashIncome / 12) + ' / mo');
  set('fl-spread', marketResult === null ? 'Unavailable' : format(marketResult / 12) + ' / mo');
  set('fl-consumption-total', expenses === null ? 'Unavailable' : format(expenses / 12) + ' / month');
  const tolerance = expenses !== null && expenses > 0 ? Math.abs(income - expenses) / expenses : null;
  const flowState = net === null ? 'unknown' : tolerance !== null && tolerance <= 0.02 ? 'neutral' : net < 0 ? 'negative' : net > 0 ? 'positive' : 'neutral';
  const flowPanel = document.querySelector('.fl-center, .fl-compact-net');
  if (flowPanel) flowPanel.dataset.flow = flowState;
  set('fl-coverage', cashIncome === null || expenses === null ? 'Cash planning data unavailable' : expenses === 0 ? 'No planned spending' : Math.round(cashIncome / expenses * 100) + '% cash income coverage');
  const coverage = cashIncome === null || expenses === null || expenses <= 0 ? null : Math.max(0, cashIncome / expenses * 100);
  const coverageBar = document.getElementById('fl-coverage-bar');
  const coverageFill = document.getElementById('fl-coverage-fill');
  if (coverageBar) { coverageBar.setAttribute('aria-valuenow', String(Math.round(Math.min(100, coverage ?? 0)))); coverageBar.setAttribute('aria-valuetext', coverage === null ? 'Unavailable' : Math.round(coverage) + '% cash income coverage'); }
  if (coverageFill) coverageFill.style.width = Math.min(100, coverage ?? 0) + '%';
  const annualGap = numeric(data.annualGap);
  set('fl-annual-gap', annualGap === null ? 'Annual gap unavailable' : annualGap > 0 ? format(annualGap) + ' annual cash funding gap' : 'No annual cash funding gap');
  const liquid = numeric(data.liquidAssets);
  set('fl-liquid', format(liquid));
  const reserve = numeric(data.reserve);
  set('fl-cash-reserve', format(reserve));
  const runway = document.getElementById('fl-runway');
  if (runway) {
    const months = cashNet !== null && cashNet < 0 && reserve !== null && reserve >= 0 ? reserve / -cashNet : null;
    runway.textContent = months === null ? (cashNet !== null && cashNet >= 0 ? 'No projected cash funding deficit' : 'Cash-reserve runway unavailable') : 'Illustrative cash-reserve runway: ' + new Intl.NumberFormat('en-GB', { maximumFractionDigits: 1 }).format(months) + ' months';
  }
  set('fl-illiquid', format(numeric(data.illiquidAssets)));
  set('fl-worth', format(numeric(data.netWorth)));
  set('fl-period', (data.year || 'Current') + ' · full-year plan as monthly averages');
  // Visual encoding is normalized to the portfolio's own total assets, not screen pixels.
  // It is illustrative: no displayed or persisted monetary value is changed.
  const illiquid = numeric(data.illiquidAssets);
  const assetsTotal = Math.max(0, liquid ?? 0) + Math.max(0, illiquid ?? 0);
  const liquidShare = assetsTotal > 0 ? Math.max(0, liquid ?? 0) / assetsTotal : 0;
  const illiquidShare = assetsTotal > 0 ? Math.max(0, illiquid ?? 0) / assetsTotal : 0;
  const root = document.querySelector('.fl-scene, .fl-compact');
  if (root) {
    root.style.setProperty('--fl-dune-scale', String(0.45 + 0.55 * Math.sqrt(liquidShare)));
    root.style.setProperty('--fl-mountain-scale', String(0.55 + 0.45 * Math.sqrt(illiquidShare)));
    const ratio = income !== null && expenses !== null && Math.max(income, expenses) > 0
      ? Math.min(1, Math.max(0, income) / Math.max(Math.max(0, income), Math.max(0, expenses)))
      : 0.5;
    root.style.setProperty('--fl-stream-opacity', String(0.3 + 0.7 * ratio));
    root.style.setProperty('--fl-stream-duration', (2.2 - ratio * 1.25).toFixed(2) + 's');
    root.dataset.flow = flowState;
    const cashRatio = income !== null && income > 0 && cashIncome !== null ? Math.min(1, Math.max(0, cashIncome / income)) : 0;
    root.style.setProperty('--fl-cash-share', (cashRatio * 100).toFixed(2) + '%');
    const financing = numeric(data.annualGap);
    const saving = numeric(data.annualSurplus);
    root.dataset.reserveDirection = financing !== null && financing > 0 ? 'draw' : saving !== null && saving > 0 ? 'save' : cashNet !== null && cashNet < 0 ? 'draw' : cashNet !== null && cashNet > 0 ? 'save' : 'still';
  }
  const consumptionBar = document.getElementById('fl-consumption-bar');
  const consumptionLegend = document.getElementById('fl-consumption-legend');
  if (consumptionBar && consumptionLegend) {
    const groups = Array.isArray(data.expenseGroups) ? data.expenseGroups.filter(g => numeric(g.monthlyAmount) > 0) : [];
    const sum = groups.reduce((total, g) => total + Number(g.monthlyAmount), 0);
    const colors = ['#efb75e', '#5ba7f0', '#ec896e', '#67c39a', '#aa8de1', '#dba9c5', '#7fbdc6'];
    for (const [index, group] of groups.entries()) {
      const amount = Number(group.monthlyAmount);
      const share = sum > 0 ? amount / sum : 0;
      const color = colors[index % colors.length];
      const segment = document.createElement('div');
      segment.className = 'fl-consumption-segment';
      segment.style.backgroundColor = color;
      segment.style.flexGrow = String(amount);
      segment.setAttribute('title', String(group.name) + ': ' + format(amount) + ' / month');
      segment.textContent = Math.round(share * 100) >= 8 ? Math.round(share * 100) + '%' : '';
      consumptionBar.append(segment);
      const item = document.createElement('div');
      item.className = 'fl-consumption-item';
      const label = document.createElement('span');
      label.className = 'fl-consumption-name';
      const dot = document.createElement('i');
      dot.style.backgroundColor = color;
      label.append(dot, document.createTextNode(String(group.name)));
      const value = document.createElement('strong');
      value.textContent = format(amount);
      const percent = document.createElement('small');
      percent.textContent = share > 0 && share < .005 ? '<1%' : Math.round(share * 100) + '%';
      item.append(label, value, percent);
      consumptionLegend.append(item);
    }
    if (!groups.length) {
      const empty = document.createElement('p');
      empty.textContent = 'Consumption categories are not available for this plan.';
      consumptionLegend.append(empty);
    }
  }
  const full = document.getElementById('fl-fullscreen');
  full?.addEventListener('click', () => {
    const app = document.querySelector('.fl-app');
    if (!document.fullscreenElement) { const result = app?.requestFullscreen?.(); result?.catch?.(() => undefined); }
    else { const result = document.exitFullscreen?.(); result?.catch?.(() => undefined); }
  });
})();
