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
  const currency = data.currency || 'PLN';
  const format = value => value === null || !Number.isFinite(value)
    ? 'Unavailable'
    : new Intl.NumberFormat('en-GB', {maximumFractionDigits: 0, style: 'currency', currency}).format(value);
  const rate = value => value === null ? 'Unavailable' : new Intl.NumberFormat('en-GB', {minimumFractionDigits: 2, maximumFractionDigits: 3}).format(value) + ' ' + currency;
  const set = (id, value) => {const el = document.getElementById(id); if (el) el.textContent = value;};
  set('fl-income', format(income === null ? null : income / 12) + ' / month');
  set('fl-expenses', format(expenses === null ? null : expenses / 12) + ' / month');
  set('fl-income-rate', income === null ? 'No planning income' : rate(income / (365.2425 * 24 * 60)) + ' / min');
  set('fl-expense-rate', expenses === null ? 'No planning expenses' : rate(expenses / (365.2425 * 24 * 60)) + ' / min');
  const net = income === null || expenses === null ? null : (income - expenses) / 12;
  set('fl-net', (net !== null && net > 0 ? '+' : '') + format(net) + ' / month');
  const flowPanel = document.querySelector('.fl-center');
  if (flowPanel) flowPanel.dataset.flow = net === null || net === 0 ? 'neutral' : net < 0 ? 'negative' : 'positive';
  set('fl-coverage', income === null || expenses === null ? 'Planning data unavailable' : expenses === 0 ? 'No planned spending' : Math.round(income / expenses * 100) + '% income coverage');
  const coverage = income === null || expenses === null || expenses <= 0 ? null : Math.max(0, income / expenses * 100);
  const coverageBar = document.getElementById('fl-coverage-bar');
  const coverageFill = document.getElementById('fl-coverage-fill');
  if (coverageBar) { coverageBar.setAttribute('aria-valuenow', String(Math.round(Math.min(100, coverage ?? 0)))); coverageBar.setAttribute('aria-valuetext', coverage === null ? 'Unavailable' : Math.round(coverage) + '% income coverage'); }
  if (coverageFill) coverageFill.style.width = Math.min(100, coverage ?? 0) + '%';
  const annualGap = income === null || expenses === null ? null : Math.max(0, expenses - income);
  set('fl-annual-gap', annualGap === null ? 'Annual gap unavailable' : annualGap > 0 ? format(annualGap) + ' projected annual gap' : 'No projected annual gap');
  const liquid = numeric(data.liquidAssets);
  set('fl-liquid', format(liquid));
  const runway = document.getElementById('fl-runway');
  if (runway) {
    const months = net !== null && net < 0 && liquid !== null && liquid >= 0 ? liquid / -net : null;
    runway.textContent = months === null ? (net !== null && net >= 0 ? 'No projected monthly deficit' : 'Reserve runway unavailable') : 'Illustrative liquid-asset runway: ' + new Intl.NumberFormat('en-GB', { maximumFractionDigits: 1 }).format(months) + ' months';
  }
  set('fl-illiquid', format(numeric(data.illiquidAssets)));
  set('fl-worth', format(numeric(data.netWorth)));
  set('fl-period', (data.year || 'Current') + ' · full-year plan as monthly averages');
  const full = document.getElementById('fl-fullscreen');
  full?.addEventListener('click', () => {
    const app = document.querySelector('.fl-app');
    if (!document.fullscreenElement) { const result = app?.requestFullscreen?.(); result?.catch?.(() => undefined); }
    else { const result = document.exitFullscreen?.(); result?.catch?.(() => undefined); }
  });
})();
