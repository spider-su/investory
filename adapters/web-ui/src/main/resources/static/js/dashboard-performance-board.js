export function initPerformanceBoard({
        Chart, selectedDashboardPeriod, portfolioId, baseCurrency, amountFormatter, percentFormatter, chartTheme, renderTable, enableKeyboardChart, gridColor, tickColor, percentValue}) {
const performanceBoardEl = document.getElementById('performance-board-chart');
let performanceBoardChart = null;
const performanceBoardState = { metric: 'return', style: 'line' };
const performanceBoardAccountPalette = ['#5b4bff', '#38bdf8', '#f59e0b', '#f43f5e', '#a855f7', '#14b8a6', '#f97316', '#ec4899'];
const performanceBoardBenchmarkColor = '#16a34a';

function performanceBoardSelectedIds() {
    return Array.from(document.querySelectorAll('.js-performance-board-account:checked')).map(input => Number(input.value));
}

function performanceBoardUpdateAccountLabel() {
    const inputs = Array.from(document.querySelectorAll('.js-performance-board-account'));
    const selected = inputs.filter(input => input.checked).length;
    const label = document.getElementById('performance-board-account-label');
    if (label) label.textContent = selected === 0 || selected === inputs.length ? 'All accounts · ' + inputs.length : selected + ' of ' + inputs.length;
}

function performanceBoardDataset(label, values, color, type, stacked) {
    const bars = type === 'bar';
    return { label, data: values, borderColor: color, backgroundColor: bars ? color : color + '22', fill: !bars && label === 'Portfolio', tension: .25, pointRadius: 0, borderWidth: bars ? 0 : 2, borderRadius: bars ? 5 : 0, grouped: bars, skipNull: bars, barPercentage: bars ? 1 : undefined, categoryPercentage: bars ? .72 : undefined, stack: stacked ? 'accounts' : undefined };
}

function performanceBoardAccountColor(series, visibleIndex, accounts) {
    const accountIndex = (accounts || []).findIndex(account => Number(account.id) === Number(series.accountId));
    const paletteIndex = accountIndex >= 0 ? accountIndex : visibleIndex;
    return performanceBoardAccountPalette[paletteIndex % performanceBoardAccountPalette.length];
}

function performanceBoardVisibleSeries(view, selectedIds) {
    const series = view.series || [];
    if (selectedIds.length === 0) return series;
    const selectedNames = new Set((view.accounts || []).filter(account => account.selected).map(account => account.name));
    return series.filter(row => row.label === 'Portfolio' || selectedNames.has(row.label));
}

async function performanceBoardView(selectedIds) {
    const params = new URLSearchParams({
        aggregation: document.getElementById('performance-board-aggregation')?.value || 'monthly',
        metric: performanceBoardState.metric === 'pl' ? 'profit' : performanceBoardState.metric,
        style: performanceBoardState.style,
        period: selectedDashboardPeriod,
        portfolioId
    });
    if (selectedIds.length > 0) params.set('accountIds', selectedIds.join(','));
    const response = await fetch('/api/v1/investment/performance/board?' + params.toString(), { headers: { Accept: 'application/json' } });
    if (!response.ok) throw new Error('Performance board request failed');
    return response.json();
}

function writePerformanceKpis(view) {
    const kpis = view.kpis || {};
    const write = (id, value, suffix, money) => {
        const el = document.getElementById(id); if (!el) return;
        if (value == null) { el.textContent = '—'; return; }
        el.textContent = (value >= 0 ? '+' : '') + (money ? amountFormatter.format(Math.round(value)) : percentFormatter.format(Number(value))) + suffix;
        el.classList.toggle('iv-pos', value >= 0); el.classList.toggle('iv-neg', value < 0);
    };
    write('performance-board-return', kpis.portfolioReturn, '%', false);
    write('performance-board-pl', kpis.portfolioProfitLoss, '', true);
    write('performance-board-spy', kpis.benchmarkReturn, '%', false);
    write('performance-board-excess', kpis.excessReturn, ' pp', false);
    const showBenchmark = performanceBoardState.metric === 'return' && document.getElementById('performance-board-show-spy')?.checked;
    ['performance-board-spy-kpi', 'performance-board-excess-kpi'].forEach(id => { const el = document.getElementById(id); if (el) el.style.display = showBenchmark ? '' : 'none'; });
    const best = document.getElementById('performance-board-best');
    const worst = document.getElementById('performance-board-worst');
    if (best) best.textContent = kpis.bestPeriod && kpis.bestValue != null ? kpis.bestPeriod + ' · ' + (kpis.bestValue >= 0 ? '+' : '') + percentFormatter.format(Number(kpis.bestValue)) : '—';
    if (worst) worst.textContent = kpis.worstPeriod && kpis.worstValue != null ? kpis.worstPeriod + ' · ' + (kpis.worstValue >= 0 ? '+' : '') + percentFormatter.format(Number(kpis.worstValue)) : '—';
}

async function renderPerformanceBoard() {
    if (!performanceBoardEl) return;
    performanceBoardUpdateAccountLabel();
    const empty = document.getElementById('performance-board-empty');
    const content = document.getElementById('performance-board-content');
    try {
        const selectedIds = performanceBoardSelectedIds();
        const view = await performanceBoardView(selectedIds);
        if (selectedIds.length > 0) (view.accounts || []).forEach(account => { const input = document.querySelector('.js-performance-board-account[value="' + account.id + '"]'); if (input) input.checked = account.selected; });
        const labels = view.labels || [];
        const bars = performanceBoardState.style === 'bars';
        const visibleSeries = performanceBoardVisibleSeries(view, selectedIds);
        const datasets = visibleSeries.map((series, index) => performanceBoardDataset(series.label, series.values || [], performanceBoardAccountColor(series, index, view.accounts), bars ? 'bar' : 'line', bars && performanceBoardState.metric === 'pl' && visibleSeries.length > 1));
        if (performanceBoardState.metric === 'return' && document.getElementById('performance-board-show-spy')?.checked && (view.benchmarkValues || []).some(value => value != null)) datasets.push(performanceBoardDataset('S&P 500', view.benchmarkValues, performanceBoardBenchmarkColor, bars ? 'bar' : 'line', false));
        if (!view.available || !datasets.length) { if (empty) empty.style.display = ''; if (content) content.style.display = 'none'; return; }
        if (empty) empty.style.display = 'none'; if (content) content.style.display = '';
        writePerformanceKpis(view);
        document.getElementById('performance-scope-aggregation').textContent = (document.getElementById('performance-board-aggregation')?.value || 'monthly').replace(/^./, value => value.toUpperCase());
        const unit = performanceBoardState.metric === 'return' ? '%' : baseCurrency;
        if (!performanceBoardChart) {
            performanceBoardChart = new Chart(performanceBoardEl.getContext('2d'), { type: bars ? 'bar' : 'line', data: { labels, datasets }, options: { responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false }, scales: { x: { stacked: bars && performanceBoardState.metric === 'pl', grid: { display: false }, ticks: { color: tickColor, maxRotation: 0, autoSkip: true } }, y: { stacked: bars && performanceBoardState.metric === 'pl', grid: { color: gridColor, drawBorder: false }, ticks: { color: tickColor, callback: value => performanceBoardState.metric === 'return' ? percentValue(value) : amountFormatter.format(Math.round(value)) } } }, plugins: { legend: { display: true, position: 'top', align: 'end', labels: { usePointStyle: true, pointStyle: 'circle', boxWidth: 8, boxHeight: 8, color: chartTheme().legend, font: {size: 12} } }, tooltip: { callbacks: { label: context => context.dataset.label + ': ' + (Number(context.parsed.y) >= 0 ? '+' : '') + (unit === '%' ? percentValue(context.parsed.y) : amountFormatter.format(Math.round(context.parsed.y)) + ' ' + unit) } } } } });
            enableKeyboardChart(performanceBoardChart, 'performance-board-chart', () => {});
        } else if (performanceBoardChart.config.type !== (bars ? 'bar' : 'line')) { performanceBoardChart.destroy(); performanceBoardChart = null; return renderPerformanceBoard(); } else { performanceBoardChart.data.labels = labels; performanceBoardChart.data.datasets = datasets; performanceBoardChart.update(); }
        renderTable('performance-board-table', ['Period', ...datasets.map(dataset => dataset.label)], labels.map((label, index) => [label, ...datasets.map(dataset => { const value = dataset.data[index]; return value == null ? '—' : (Number(value) >= 0 ? '+' : '') + (unit === '%' ? percentValue(value) : amountFormatter.format(Math.round(value)) + ' ' + unit); })]));
    } catch (error) { if (empty) empty.style.display = ''; if (content) content.style.display = 'none'; }
}

document.querySelectorAll('.js-performance-board-metric').forEach(button => button.addEventListener('click', () => { performanceBoardState.metric = button.dataset.metric; document.querySelectorAll('.js-performance-board-metric').forEach(item => { const active = item === button; item.classList.toggle('btn-primary', active); item.classList.toggle('btn-outline-secondary', !active); }); renderPerformanceBoard(); }));
document.querySelectorAll('.js-performance-board-style').forEach(button => button.addEventListener('click', () => { performanceBoardState.style = button.dataset.style; document.querySelectorAll('.js-performance-board-style').forEach(item => { const active = item === button; item.classList.toggle('btn-primary', active); item.classList.toggle('btn-outline-secondary', !active); }); renderPerformanceBoard(); }));
document.querySelectorAll('.js-performance-board-account').forEach(input => input.addEventListener('change', renderPerformanceBoard));
document.getElementById('performance-board-aggregation')?.addEventListener('change', renderPerformanceBoard);
document.getElementById('performance-board-show-spy')?.addEventListener('change', renderPerformanceBoard);
document.getElementById('performance-board-check-all')?.addEventListener('click', () => { document.querySelectorAll('.js-performance-board-account').forEach(input => input.checked = true); renderPerformanceBoard(); });
document.getElementById('performance-board-uncheck-all')?.addEventListener('click', () => { document.querySelectorAll('.js-performance-board-account').forEach(input => input.checked = false); renderPerformanceBoard(); });
renderPerformanceBoard();


    return {getPerformanceBoardChart: () => performanceBoardChart};
}





