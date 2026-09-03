import {enableKeyboardChart, effectiveAccountIds, renderTable, selectedAccountValueAccountIds, selectedBenchmarkAccountIds, showChartUnavailable} from './dashboard-chart-shared.js';

export function initBenchmarkAndAccountValue({
        Chart, benchEl, benchLabels, accountValueEl, amountFormatter, percentFormatter, baseCurrency, portfolioId, signedBaseFormatter, accountValueYears, chartTheme, signedValue, signedPercentValue, gridColor, tickColor, percentValue}) {
let benchmarkChart = null;
let accountValueChart = null;
let accountValueRequest = 0;

if (benchEl) {
    benchmarkChart = new Chart(benchEl.getContext("2d"), {
        type: 'line',
        data: {
            labels: benchLabels,
            datasets: [
                {
                    label: 'Portfolio',
                    data: [],
                    borderColor: '#4f46e5',
                    backgroundColor: 'rgba(79, 70, 229, .12)',
                    fill: true,
                    tension: 0.25,
                    pointRadius: 0,
                    borderWidth: 2
                },
                {
                    label: 'S&P 500',
                    data: [],
                    borderColor: '#16a34a',
                    backgroundColor: 'rgba(22, 163, 74, .08)',
                    fill: true,
                    tension: 0.25,
                    pointRadius: 0,
                    borderWidth: 2
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            scales: {
                x: {
                    grid: { display: false },
                    ticks: { color: tickColor, maxRotation: 0, autoSkip: true }
                },
                y: {
                    grid: { color: gridColor, drawBorder: false },
                    ticks: {
                        color: tickColor,
                        callback: function (value) {
                            return percentValue(value);
                        }
                    }
                }
            },
            plugins: {
                legend: {
                    display: true,
                    position: 'top',
                    align: 'end',
                    labels: { usePointStyle: true, pointStyle: 'circle', boxWidth: 8, boxHeight: 8, color: '#6b7488' }
                },
                tooltip: {
                    backgroundColor: '#1e2536',
                    padding: 12,
                    cornerRadius: 10,
                    callbacks: {
                        label: function (context) {
                            const absoluteData = context.dataset.absoluteData || [];
                            const absoluteValue = absoluteData[context.dataIndex] || 0;
                            return context.dataset.label + ': '
                                + percentValue(context.parsed.y) + ' ('
                                + signedBaseFormatter.format(Math.round(absoluteValue)) + ' ' + baseCurrency + ')';
                        }
                    }
                }
            }
        }
    });
}

const transactionEventMarkerPlugin = {
    id: 'transactionEventMarkers',
    afterDatasetsDraw(chart, args, options) {
        if (!options || !Array.isArray(options.events)) return;
        const meta = chart.getDatasetMeta(0);
        if (!meta?.data?.length) return;
        const colors = { deposit: '#2563eb', dividend: '#16a34a', trade: '#7c3aed' };
        options.events.forEach(event => {
            const point = meta.data[event.index];
            if (!point) return;
            const ctx = chart.ctx;
            ctx.save();
            ctx.beginPath();
            ctx.arc(point.x, point.y, 5, 0, Math.PI * 2);
            ctx.fillStyle = colors[event.type] || colors.trade;
            ctx.fill();
            ctx.lineWidth = 2;
            ctx.strokeStyle = chartTheme().surface;
            ctx.stroke();
            ctx.restore();
        });
    }
};
if (!window.__investoryTransactionEventMarkersRegistered) {
    Chart.register(transactionEventMarkerPlugin);
    window.__investoryTransactionEventMarkersRegistered = true;
}

if (accountValueEl) {
    accountValueChart = new Chart(accountValueEl.getContext("2d"), {
        type: 'line',
        data: {
            labels: [],
            datasets: []
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            scales: {
                x: {
                    grid: { display: false },
                    ticks: { color: tickColor, maxRotation: 0, autoSkip: true }
                },
                y: {
                    grid: { color: gridColor, drawBorder: false },
                    ticks: {
                        color: tickColor,
                        callback: function (value) {
                            return amountFormatter.format(Math.round(value));
                        }
                    }
                }
            },
            plugins: {
                legend: {
                    display: true,
                    position: 'top',
                    align: 'end',
                    labels: { usePointStyle: true, pointStyle: 'circle', boxWidth: 8, boxHeight: 8, color: '#6b7488' }
                },
                tooltip: {
                    backgroundColor: '#1e2536',
                    padding: 12,
                    cornerRadius: 10,
                    callbacks: {
                        label: function (context) {
                            return context.dataset.label + ': '
                                + amountFormatter.format(Math.round(context.parsed.y || 0));
                        }
                    }
                },
                transactionEventMarkers: { events: [] }
            }
        }
    });
}

function setBenchmarkValue(id, value, suffix, signed) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.toggle('iv-pos', value >= 0);
    el.classList.toggle('iv-neg', value < 0);
    el.textContent = (signed && value >= 0 ? '+' : '') + percentFormatter.format(value) + suffix;
}

async function updateBenchmarkSelection() {
    if (!benchmarkChart) return;
    const ids = [...selectedBenchmarkAccountIds()].join(',');
    const params = new URLSearchParams({ accountIds: ids, aggregation: 'monthly', metric: 'return', style: 'line', portfolioId });
    try {
        const response = await fetch('/api/v1/investment/performance/board?' + params.toString(), { headers: { Accept: 'application/json' } });
        if (!response.ok) throw new Error('HTTP ' + response.status);
        const view = await response.json();
        const available = Boolean(view.available);
        document.getElementById('benchmark-empty').style.display = available ? 'none' : '';
        document.getElementById('benchmark-content').style.display = available ? '' : 'none';
        const portfolio = view.series?.[0]?.values || [];
        const benchmark = view.benchmarkValues || [];
        benchmarkChart.data.labels = view.labels || [];
        benchmarkChart.data.datasets[0].data = portfolio;
        benchmarkChart.data.datasets[0].absoluteData = portfolio;
        benchmarkChart.data.datasets[1].data = benchmark;
        benchmarkChart.data.datasets[1].absoluteData = benchmark;
        benchmarkChart.update();
        const last = values => values.length && values.at(-1) != null ? Number(values.at(-1)) : null;
        const portfolioPct = last(portfolio), benchmarkPct = last(benchmark);
        const excess = view.excessValues || [];
        renderTable('benchmark-table', ['Period', 'Portfolio', 'S&P 500', 'Excess return'], (view.labels || []).map((label, i) => [label, portfolio[i] == null ? '—' : signedPercentValue(portfolio[i]), benchmark[i] == null ? '—' : signedPercentValue(benchmark[i]), excess[i] == null ? '—' : signedPercentValue(excess[i]).replace('%', ' pp')]));
        if (portfolioPct != null) setBenchmarkValue('benchmark-you', portfolioPct, ' %', false);
        if (benchmarkPct != null) setBenchmarkValue('benchmark-spy', benchmarkPct, ' %', false);
        if (view.kpis?.excessReturn != null) setBenchmarkValue('benchmark-alpha', view.kpis.excessReturn, ' pp', true);
    } catch (error) {
        showChartUnavailable(
            document.getElementById('benchmark-empty'),
            document.getElementById('benchmark-content'),
            'Benchmark data could not be loaded.');
    }
}

function selectedAccountValueYear() {
    const yearSelect = document.getElementById('account-value-year');
    const selectedYear = yearSelect ? Number(yearSelect.value) : null;
    return accountValueYears.find(year => Number(year.year) === selectedYear) || accountValueYears[0];
}

function selectedAccountValueMode() {
    return document.getElementById('account-value-mode')?.value === 'percent' ? 'percent' : 'absolute';
}

function accountValueSeriesData(series, mode) {
    return mode === 'percent' ? (series.profitPctValues || []) : (series.profitValues || []);
}

function accountValueAxisLabel(value, mode) {
    return mode === 'percent'
        ? percentFormatter.format((Number(value) || 0) / 100)
        : amountFormatter.format(Math.round(Number(value) || 0));
}

function updateAccountValueHint(mode) {
    const hintEl = document.getElementById('account-value-hint');
    if (!hintEl) return;
    hintEl.textContent = mode === 'percent'
        ? 'Calendar year · external cash flows excluded · cumulative daily return · %'
        : 'Calendar year · external cash flows excluded · cumulative daily profit/loss · ' + baseCurrency;
}

function updateAccountValueChart(selectedIds) {
    if (!accountValueChart) return;
    const year = selectedAccountValueYear();
    const mode = selectedAccountValueMode();
    const emptyEl = document.getElementById('account-value-empty');
    const contentEl = document.getElementById('account-value-content');
    if (!year || !year.accountSeries || year.accountSeries.length === 0) {
        if (emptyEl) emptyEl.style.display = '';
        if (contentEl) contentEl.style.display = 'none';
        return;
    }

    const loadedIds = new Set(year.accountSeries.map(series => Number(series.id)));
    const requestedAccountsLoaded = selectedIds.size === loadedIds.size
        && [...selectedIds].every(id => loadedIds.has(id));
    if (!requestedAccountsLoaded) {
        const requestId = ++accountValueRequest;
        const ids = [...selectedIds].join(',');
            fetch('/api/v1/investment/performance/account-values?accountIds=' + encodeURIComponent(ids) + '&portfolioId=' + encodeURIComponent(portfolioId))
            .then(response => { if (!response.ok) throw new Error('HTTP ' + response.status); return response.json(); })
            .then(view => {
                if (requestId !== accountValueRequest) return;
                accountValueYears.splice(0, accountValueYears.length, ...(view.years || []));
                updateAccountValueChart(selectedIds);
            })
            .catch(() => showChartUnavailable(
                emptyEl,
                contentEl,
                'Account value data could not be loaded.'));
        return;
    }

    const effectiveIds = effectiveAccountIds(selectedIds, year.accountSeries.map(series => Number(series.id)));
    const selectedSeries = year.accountSeries.filter(series => effectiveIds.has(Number(series.id)));
    const total = mode === 'percent' ? (year.totalProfitPctValues || []) : (year.totalProfitValues || []);
    const hasValues = total.some(value => Math.abs(value) > 1) || selectedSeries.length > 0;
    if (emptyEl) emptyEl.style.display = hasValues ? 'none' : '';
    if (contentEl) contentEl.style.display = hasValues ? '' : 'none';
    updateAccountValueHint(mode);
    const palette = ['#4f46e5', '#16a34a', '#f59e0b', '#dc2626', '#0891b2', '#7c3aed', '#0f766e', '#be123c'];
    accountValueChart.data.labels = year.labels || [];
    accountValueChart.data.datasets = [
        {
            label: 'Portfolio',
            data: total,
            borderColor: chartTheme().strong,
            backgroundColor: chartTheme().strongFill,
            fill: true,
            tension: 0.25,
            pointRadius: 0,
            borderWidth: 3
        },
        ...selectedSeries.map((series, index) => ({
            label: series.name,
            data: accountValueSeriesData(series, mode),
            borderColor: palette[index % palette.length],
            backgroundColor: 'transparent',
            fill: false,
            tension: 0.25,
            pointRadius: 0,
            borderWidth: 1.8
        }))
    ];
    accountValueChart.options.plugins.transactionEventMarkers.events = [];
    accountValueChart.options.scales.y.ticks.callback = function (value) {
        return accountValueAxisLabel(value, mode);
    };
    accountValueChart.options.plugins.tooltip.callbacks.label = function (context) {
        return context.dataset.label + ': ' + accountValueAxisLabel(context.parsed.y || 0, mode);
    };
    accountValueChart.options.onClick = async function (event, elements) {
        if (!elements.length) return;
        const date = accountValueChart.data.labels[elements[0].index];
        const ids = [...effectiveIds].join(',');
        const panel = document.getElementById('daily-attribution-panel');
        panel.textContent = 'Loading daily attribution…';
        document.getElementById('daily-attribution-details').open = true;
        try {
            const response = await fetch('/api/v1/investment/performance/daily-attribution?date=' + encodeURIComponent(date) + '&accountIds=' + encodeURIComponent(ids) + '&portfolioId=' + encodeURIComponent(portfolioId));
            if (!response.ok) throw new Error('HTTP ' + response.status);
            const a = await response.json();
            const money = value => signedBaseFormatter.format(Math.round(Number(value || 0))) + ' ' + baseCurrency;
            panel.replaceChildren();
            const appendLine = (tag, text) => {
                const element = document.createElement(tag);
                element.textContent = text;
                panel.appendChild(element);
            };
            appendLine('strong', a.date + ' · Daily profit/loss ' + money(a.dailyProfit));
            appendLine('div', 'Daily return: ' + percentValue(a.dailyReturnPct));
            appendLine('div', 'Opening equity: ' + money(a.openingEquity));
            appendLine('div', 'Closing equity: ' + money(a.closingEquity));
            appendLine('div', 'Deposits: ' + money(a.deposits) + ' · Withdrawals: -' + money(a.withdrawals));
            appendLine('div', 'Dividends: ' + money(a.dividends) + ' · Interest: ' + money(a.interest));
            appendLine('div', 'Fees: ' + money(a.fees) + ' · Taxes: ' + money(a.taxes));
            appendLine('div', 'Combined market/FX movement: ' + money(a.unresolvedResidual));
            appendLine('strong', 'Accounts');
            (a.accounts || []).forEach(row => appendLine('div', row.accountId + ': ' + money(row.dailyProfit) + ' · flow ' + money(row.deposits - row.withdrawals)));
            const note = document.createElement('p');
            note.className = 'iv-muted';
            note.textContent = a.attributionNote || '';
            panel.appendChild(note);
        } catch (error) { panel.textContent = 'Daily attribution unavailable.'; }
    };
    accountValueChart.update();
    renderTable('account-value-table', ['Date', mode === 'percent' ? 'Portfolio return' : 'Portfolio profit/loss · ' + baseCurrency], (year.labels || []).map((label, i) => [label, signedValue(total[i]) + (mode === 'percent' ? ' %' : ' ' + baseCurrency)]));
}

document.querySelectorAll('.js-benchmark-account')
    .forEach(input => input.addEventListener('change', updateBenchmarkSelection));
document.getElementById('benchmark-show-spy')?.addEventListener('change', event => {
    if (!benchmarkChart?.data?.datasets?.[1]) return;
    benchmarkChart.data.datasets[1].hidden = !event.target.checked;
    benchmarkChart.update();
});
document.querySelectorAll('.js-account-value-account')
    .forEach(input => input.addEventListener('change', () =>
        updateAccountValueChart(selectedAccountValueAccountIds())));
document.getElementById('account-value-mode')?.addEventListener('change', () =>
    updateAccountValueChart(selectedAccountValueAccountIds()));
document.getElementById('account-value-year')?.addEventListener('change', () =>
    updateAccountValueChart(selectedAccountValueAccountIds()));
document.getElementById('benchmark-check-all')?.addEventListener('click', () => {
    document.querySelectorAll('.js-benchmark-account').forEach(input => input.checked = true);
    updateBenchmarkSelection();
});
document.getElementById('benchmark-uncheck-all')?.addEventListener('click', () => {
    document.querySelectorAll('.js-benchmark-account').forEach(input => input.checked = false);
    updateBenchmarkSelection();
});
document.getElementById('account-value-check-all')?.addEventListener('click', () => {
    document.querySelectorAll('.js-account-value-account').forEach(input => input.checked = true);
    updateAccountValueChart(selectedAccountValueAccountIds());
});
document.getElementById('account-value-uncheck-all')?.addEventListener('click', () => {
    document.querySelectorAll('.js-account-value-account').forEach(input => input.checked = false);
    updateAccountValueChart(selectedAccountValueAccountIds());
});
updateBenchmarkSelection();
updateAccountValueChart(selectedAccountValueAccountIds());
enableKeyboardChart(benchmarkChart, 'benchmark-chart', () => {});
enableKeyboardChart(accountValueChart, 'account-value-chart', index => accountValueChart.options.onClick?.({}, [{index}]));


    return {benchmarkChart, accountValueChart, updateAccountValueChart};
}




