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
  const reserve = numeric(data.reserve);
  const runway = document.getElementById('fl-runway');
  if (runway) {
    const months = cashNet !== null && cashNet < 0 && reserve !== null && reserve >= 0 ? reserve / -cashNet : null;
    runway.textContent = months === null ? (cashNet !== null && cashNet >= 0 ? 'No projected cash funding deficit' : 'Cash-reserve runway unavailable') : 'Illustrative cash-reserve runway: ' + new Intl.NumberFormat('en-GB', { maximumFractionDigits: 1 }).format(months) + ' months';
  }
  // Scenic measurement guides reuse the same source amounts as the asset footer.
  const terrainValue = value => value === null ? '—' : compactNumber(value) + ' ' + currency;
  set('fl-mountain-value', terrainValue(numeric(data.illiquidAssets)));
  set('fl-dune-value', terrainValue(liquid));
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
  // The Full scene has four stable budget destinations. Category names come
  // from the existing simulation CRUD; no totals or domain categories are changed.
  // Unknown future names stay visible in a separate catch-all destination.
  const spendingField = document.getElementById('fl-spending-field');
  if (spendingField) {
    const groups = Array.isArray(data.expenseGroups) ? data.expenseGroups
      .filter(g => g && typeof g.name === 'string' && g.name.trim() && numeric(g.monthlyAmount) > 0)
      .map(g => ({name: g.name.trim(), amount: Number(g.monthlyAmount)})) : [];
    const definitions = [
      {name: 'Essential living', icon: 'home', color: '#E8BE67',
        names: ['grocery and restaurants', 'car', 'media lokietka']},
      {name: 'Education', icon: 'school', color: '#9BB8FF',
        names: ['school', 'kindergarden', 'kindergarten']},
      {name: 'Personal', icon: 'person', color: '#8ED8C3',
        names: ['olga', 'alex', 'online, shopping, gifts', 'other']},
      {name: 'Annual extras', icon: 'travel', color: '#E9A98C',
        names: ['annual extras']}
    ];
    const assigned = new Set();
    const destinations = definitions.map(definition => {
      const details = groups.filter((group, index) => {
        if (definition.names.includes(group.name.toLocaleLowerCase('en-US'))) {
          assigned.add(index);
          return true;
        }
        return false;
      });
      return {...definition, details, amount: details.reduce((sum, group) => sum + group.amount, 0)};
    }).filter(group => group.amount > 0);
    const unmatched = groups.filter((group, index) => !assigned.has(index));
    if (unmatched.length) {
      destinations.push({name: 'Other spending', icon: 'other', color: '#C9B6DC',
        details: unmatched, amount: unmatched.reduce((sum, group) => sum + group.amount, 0)});
    }
    const total = destinations.reduce((sum, group) => sum + group.amount, 0);
    // Largest-remainder rounding adds to 100% for the visible groups rather
    // than independently rounding a 34.52/22.62/... split to 101%.
    const fractions = destinations.map(group => total > 0 ? group.amount * 100 / total : 0);
    const rounded = fractions.map(Math.floor);
    const remainderOrder = fractions.map((fraction, index) => ({index, remainder: fraction - rounded[index]}))
      .sort((a, b) => b.remainder - a.remainder || a.index - b.index);
    const missing = Math.max(0, 100 - rounded.reduce((sum, percentage) => sum + percentage, 0));
    for (const entry of remainderOrder.slice(0, missing)) rounded[entry.index]++;

    const NS = 'http://www.w3.org/2000/svg';
    const iconPaths = {
      home: ['M3 10 12 3l9 7v11H3V10Z', 'M9 21v-8h6v8'],
      school: ['M4 19V5a2 2 0 0 1 2-2h5v17H6a2 2 0 0 0-2 2Z', 'M20 19V5a2 2 0 0 0-2-2h-5v17h5a2 2 0 0 1 2 2Z'],
      person: ['M20 21a8 8 0 0 0-16 0', 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8'],
      travel: ['M12 2l2.4 6.6L21 11l-6.6 2.4L12 20l-2.4-6.6L3 11l6.6-2.4Z'],
      other: ['M5 12h.01', 'M12 12h.01', 'M19 12h.01']
    };
    const makeIcon = type => {
      const svg = document.createElementNS(NS, 'svg');
      svg.setAttribute('viewBox', '0 0 24 24');
      svg.setAttribute('fill', 'none');
      svg.setAttribute('stroke', 'currentColor');
      svg.setAttribute('stroke-width', '1.8');
      svg.setAttribute('stroke-linecap', 'round');
      svg.setAttribute('stroke-linejoin', 'round');
      svg.setAttribute('aria-hidden', 'true');
      for (const d of iconPaths[type]) {
        const path = document.createElementNS(NS, 'path');
        path.setAttribute('d', d);
        svg.append(path);
      }
      return svg;
    };
    spendingField.replaceChildren();
    spendingField.style.setProperty('--fl-spend-count', String(destinations.length));
    for (const [index, group] of destinations.entries()) {
      const share = total > 0 ? group.amount / total : 0;
      const percentage = share > 0 && share < .005 ? '<1%' : rounded[index] + '%';
      const hasDetails = group.details.length > 1;
      const node = document.createElement(hasDetails ? 'details' : 'div');
      node.className = 'fl-spend-node';
      node.dataset.share = String(share);
      node.dataset.accent = group.color;
      node.style.setProperty('--fl-accent', group.color);
      node.style.setProperty('--fl-pile-size', String(0.52 + .48 * Math.sqrt(share)));
      const content = document.createElement(hasDetails ? 'summary' : 'div');
      content.className = 'fl-spend-content';
      const pile = document.createElement('span');
      pile.className = 'fl-spend-pile';
      pile.setAttribute('aria-hidden', 'true');
      const icon = document.createElement('span');
      icon.className = 'fl-spend-icon';
      icon.append(makeIcon(group.icon));
      const label = document.createElement('span');
      label.className = 'fl-spend-label';
      label.textContent = group.name;
      const amount = document.createElement('strong');
      amount.className = 'fl-spend-value';
      amount.textContent = format(group.amount);
      const perMonth = document.createElement('span');
      perMonth.className = 'fl-spend-period';
      perMonth.textContent = '/ month';
      const percent = document.createElement('span');
      percent.className = 'fl-spend-percent';
      percent.textContent = percentage;
      content.append(pile, icon, label, amount, perMonth, percent);
      node.append(content);
      if (hasDetails) {
        const breakdown = document.createElement('ul');
        breakdown.className = 'fl-spend-breakdown';
        breakdown.setAttribute('aria-label', group.name + ' budget categories');
        for (const detail of group.details) {
          const line = document.createElement('li');
          const name = document.createElement('span');
          name.textContent = detail.name;
          const value = document.createElement('strong');
          value.textContent = format(detail.amount);
          line.append(name, value);
          breakdown.append(line);
        }
        node.append(breakdown);
        // Mouse hover previews the breakdown; touch and keyboard retain
        // native <details>/<summary> click, Space and Enter behavior.
        node.addEventListener('pointerenter', event => {
          if (event.pointerType === 'mouse') node.open = true;
        });
        node.addEventListener('pointerleave', event => {
          if (event.pointerType === 'mouse' && !node.matches(':focus-within')) node.open = false;
        });
        node.title = 'Hover or select to see included categories';
      } else {
        node.title = group.name + ': ' + format(group.amount) + ' / month · ' + percentage;
      }
      spendingField.append(node);
    }
    if (!destinations.length) {
      const empty = document.createElement('p');
      empty.className = 'fl-spending-empty';
      empty.textContent = 'Spending categories are unavailable for this plan.';
      spendingField.append(empty);
    }
  }
  const full = document.getElementById('fl-fullscreen');
  full?.addEventListener('click', () => {
    const app = document.querySelector('.fl-app');
    if (!document.fullscreenElement) { const result = app?.requestFullscreen?.(); result?.catch?.(() => undefined); }
    else { const result = document.exitFullscreen?.(); result?.catch?.(() => undefined); }
  });
})();
