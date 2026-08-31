let activeDashboardCharts = [];

export function initDashboardCharts() {
/* Dashboard chart and performance runtime. Server data comes from dashboard.html. */
/* global Chart */
    const monthlyPerformanceData = window.investoryDashboardData.monthlyPerformance;
    // Do not rely on JSON object iteration order. The chart must always run oldest to newest.
    const monthlyLabels = Object.keys(monthlyPerformanceData).sort((a, b) => a.localeCompare(b));
    const monthlyData = monthlyLabels.map(label => Number(monthlyPerformanceData[label] || 0));

    const monthlyOperationsCount = window.investoryDashboardData.monthlyOperationsCount;
    const monthlyCounts = monthlyLabels.map(label => monthlyOperationsCount[label] || 0);

    const monthlyCashflowData = window.investoryDashboardData.monthlyCashflow;
    const monthlyCashflow = monthlyLabels.map(label => monthlyCashflowData[label] || 0);
    const monthlyAttributions = window.investoryDashboardData.monthlyAttributions || {};

    const amountFormatter = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
    const percentFormatter = new Intl.NumberFormat('en-US', {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1
    });
    const baseCurrency = window.investoryDashboardData.baseCurrency;
    const portfolioId = new URLSearchParams(window.location.search).get('portfolioId') || '1';
    const signedBaseFormatter = new Intl.NumberFormat('en-US', {
        maximumFractionDigits: 0,
        signDisplay: 'always'
    });

    const operationsCountPlugin = {
        id: 'operationsCountLabels',
        afterDatasetsDraw(chart) {
            const ctx = chart.ctx;
            const meta = chart.getDatasetMeta(0);
            const theme = chartTheme();
            ctx.save();
            ctx.textAlign = 'center';
            meta.data.forEach((bar, index) => {
                const count = chart._periodCounts?.[index] || 0;
                if (!count) {
                    return;
                }
                const value = Number(chart.data.datasets[0].data[index] || 0);
                const profitText = amountFormatter.format(Math.round(value));
                const cashflowValue = chart._periodFlows?.[index] || 0;
                const cashflowText = signedBaseFormatter.format(Math.round(cashflowValue)) + ' ' + baseCurrency;
                const accountText = count + (count === 1 ? ' account' : ' accounts');
                const detailText = Math.abs(cashflowValue) >= 1
                    ? accountText + ' · flow ' + cashflowText
                    : accountText;
                const positive = value >= 0;
                const top = Math.min(bar.y, bar.base);
                const bottom = Math.max(bar.y, bar.base);
                if (positive) {
                    const profitY = Math.max(top - 6, chart.chartArea.top + 14);
                    const detailY = Math.max(profitY - 16, chart.chartArea.top + 4);
                    ctx.textBaseline = 'bottom';
                    ctx.font = '700 12px Inter, sans-serif';
                    ctx.fillStyle = theme.strong;
                    ctx.fillText(profitText, bar.x, profitY);
                    ctx.font = '600 10px Inter, sans-serif';
                    ctx.fillStyle = theme.soft;
                    ctx.fillText(detailText, bar.x, detailY);
                } else {
                    const profitY = Math.min(bottom + 6, chart.chartArea.bottom - 28);
                    const detailY = Math.min(profitY + 16, chart.chartArea.bottom - 8);
                    ctx.textBaseline = 'top';
                    ctx.font = '700 12px Inter, sans-serif';
                    ctx.fillStyle = theme.strong;
                    ctx.fillText(profitText, bar.x, profitY);
                    ctx.font = '600 10px Inter, sans-serif';
                    ctx.fillStyle = theme.soft;
                    ctx.fillText(detailText, bar.x, detailY);
                }
            });
            ctx.restore();
        }
    };

    function cssThemeColor(name, fallback) {
        const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return value || fallback;
    }

    function chartTheme() {
        return {
            grid: cssThemeColor('--iv-chart-grid', 'rgba(148, 160, 184, .18)'),
            soft: cssThemeColor('--iv-text-soft', '#94a0b8'),
            legend: cssThemeColor('--iv-text-muted', '#6b7488'),
            strong: cssThemeColor('--iv-text', '#1e2536'),
            strongFill: cssThemeColor('--iv-chart-strong-fill', 'rgba(30, 37, 54, .08)'),
            tooltip: cssThemeColor('--iv-chart-tooltip', '#1e2536'),
            tooltipText: cssThemeColor('--iv-chart-tooltip-text', '#ffffff'),
            surface: cssThemeColor('--iv-surface', '#ffffff')
        };
    }

    let currentChartTheme = chartTheme();
    let gridColor = currentChartTheme.grid;
    let tickColor = currentChartTheme.legend;
    Chart.defaults.font.family = "Inter, system-ui, sans-serif";
    Chart.defaults.font.size = 12;
    Chart.defaults.color = tickColor;
    Chart.defaults.borderColor = gridColor;

    const benchLabels = window.investoryDashboardData.benchmarkLabels || [];
    const benchAccountSeries = window.investoryDashboardData.benchmarkAccountSeries || [];
    const benchBenchmarkReturnCurve = window.investoryDashboardData.benchmarkReturnCurve || [];
    const selectedDashboardPeriod = window.investoryDashboardData.selectedDashboardPeriod || 'YTD';
    const accountValueYears = window.investoryDashboardData.accountValueYears || [];
    const benchEl = document.getElementById("benchmark-chart");
    const accountValueEl = document.getElementById("account-value-chart");
    let benchmarkChart = null;
    let accountValueChart = null;
    let accountValueRequest = 0;
    const signedValue = value => (Number(value) >= 0 ? '+' : '') + amountFormatter.format(Number(value || 0));
    const percentValue = value => percentFormatter.format(Number(value || 0)) + '%';
    const signedPercentValue = value => (Number(value) >= 0 ? '+' : '') + percentValue(value);
    function renderTable(id, headers, rows) {
        const el = document.getElementById(id); if (!el) return;
        const table = document.createElement('table');
        table.className = 'iv-table iv-chart-table__table';
        const thead = table.createTHead();
        const headerRow = thead.insertRow();
        headers.forEach(header => {
            const cell = document.createElement('th');
            cell.textContent = header;
            headerRow.appendChild(cell);
        });
        const tbody = table.createTBody();
        rows.forEach(row => {
            const tableRow = tbody.insertRow();
            row.forEach(value => {
                const cell = tableRow.insertCell();
                cell.textContent = value;
            });
        });
        el.replaceChildren(table);
    }
    function enableKeyboardChart(chart, canvasId, activate) {
        const canvas = document.getElementById(canvasId); if (!canvas) return;
        let index = 0;
        canvas.addEventListener('keydown', event => {
            if (!chart?.data?.labels?.length) return;
            if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
                event.preventDefault();
                index = (index + (event.key === 'ArrowRight' ? 1 : -1) + chart.data.labels.length) % chart.data.labels.length;
                chart.setActiveElements([{datasetIndex: 0, index}]); chart.tooltip.setActiveElements([{datasetIndex: 0, index}]); chart.update();
            } else if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault(); activate(index);
            }
        });
    }
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

    function showChartUnavailable(emptyEl, contentEl, message) {
        if (emptyEl) {
            emptyEl.textContent = message || 'This chart is temporarily unavailable.';
            emptyEl.style.display = '';
        }
        if (contentEl) contentEl.style.display = 'none';
    }

    function showChartErrorNear(chartElement, message) {
        if (!chartElement?.parentElement) return;
        let error = chartElement.parentElement.querySelector('.iv-chart-load-error');
        if (!error) {
            error = document.createElement('p');
            error.className = 'iv-chart-load-error text-secondary';
            error.setAttribute('role', 'status');
            chartElement.insertAdjacentElement('afterend', error);
        }
        error.textContent = message;
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

    function selectedBenchmarkAccountIds() {
        return new Set(Array.from(document.querySelectorAll('.js-benchmark-account:checked'))
            .map(input => Number(input.value)));
    }

    function selectedAccountValueAccountIds() {
        return new Set(Array.from(document.querySelectorAll('.js-account-value-account:checked'))
            .map(input => Number(input.value)));
    }

    function selectedMonthlyAccountIds() {
        return new Set(Array.from(document.querySelectorAll('.js-monthly-account:checked'))
            .map(input => Number(input.value)));
    }

    function effectiveAccountIds(selectedIds, allIds) {
        return selectedIds.size === allIds.length ? new Set(allIds) : selectedIds;
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
                const response = await fetch('/api/v1/investment/performance/daily-attribution?date=' + encodeURIComponent(date) + '&accountIds=' + encodeURIComponent(ids));
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

    const monthlyPerformanceEl = document.getElementById("monthly-performance-chart");
    const ctxMonthly = monthlyPerformanceEl
        ? monthlyPerformanceEl.getContext("2d")
        : null;
    function groupedMonthlyData(mode) {
        const buckets = new Map();
        monthlyLabels.forEach((label, index) => {
            const year = label.substring(0, 4);
            const month = Number(label.substring(5, 7));
            const key = mode === 'annual' ? year : mode === 'quarterly' ? year + '-Q' + Math.ceil(month / 3) : label;
            const attribution = monthlyAttributions[label];
            const rows = attribution?.accounts || [];
            const value = Number(monthlyData[index] || 0);
            const flow = Number(monthlyCashflow[index] || 0);
            const bucket = buckets.get(key) || { value: 0, count: 0, flow: 0, sourceLabels: [] };
            bucket.value += value;
            bucket.count += Number(monthlyCounts[index] || 0);
            bucket.flow += flow;
            bucket.sourceLabels.push(label);
            buckets.set(key, bucket);
        });
        const ordered = [...buckets.entries()].sort(([a], [b]) => a.localeCompare(b));
        return { labels: ordered.map(([label]) => label), values: ordered.map(([, value]) => value) };
    }
    let monthlyChart;
    function renderMonthlyChart(mode, remoteView = null) {
        if (!ctxMonthly) return;
        const allMonthlyIds = [...new Set(Object.values(monthlyAttributions).flatMap(a => (a.accounts || []).map(row => Number(row.accountId))))];
        const selectedMonthlyIds = effectiveAccountIds(selectedMonthlyAccountIds(), allMonthlyIds);
        const partial = selectedMonthlyIds.size !== allMonthlyIds.length;
        if (partial && !remoteView) {
                fetch('/api/v1/investment/performance/monthly?accountIds=' + encodeURIComponent([...selectedMonthlyIds].join(',')) + '&aggregation=' + encodeURIComponent(mode) + '&portfolioId=' + encodeURIComponent(portfolioId))
                .then(response => { if (!response.ok) throw new Error('HTTP ' + response.status); return response.json(); })
                .then(view => renderMonthlyChart(mode, view))
                .catch(() => showChartErrorNear(
                    monthlyPerformanceEl,
                    'Monthly performance data could not be loaded.'));
            return;
        }
        const grouped = remoteView
            ? { labels: remoteView.labels || [], values: (remoteView.series?.[0]?.values || []).map(value => ({ value, count: 0, flow: 0, sourceLabels: [] })) }
            : groupedMonthlyData(mode);
        const values = grouped.values.map(row => row.value);
        const counts = grouped.values.map(row => row.count);
        const flows = grouped.values.map(row => row.flow);
        if (monthlyChart) {
            monthlyChart.data.labels = grouped.labels;
            monthlyChart.data.datasets[0].data = values;
            monthlyChart.data.datasets[0].backgroundColor = values.map(val => val >= 0 ? '#16a34a' : '#dc2626');
            monthlyChart.data.datasets[0].hoverBackgroundColor = values.map(val => val >= 0 ? '#15803d' : '#b91c1c');
            monthlyChart._periodCounts = counts;
            monthlyChart._periodFlows = flows;
            monthlyChart._periodSourceLabels = grouped.values.map(row => row.sourceLabels);
            monthlyChart._selectedAccountIds = partial ? selectedMonthlyIds : null;
            monthlyChart.update();
            renderTable('monthly-table', ['Period', 'Profit/Loss · ' + baseCurrency], grouped.labels.map((label, i) => [label, signedValue(values[i]) + ' ' + baseCurrency]));
            return;
        }
        monthlyChart = new Chart(ctxMonthly, {
        type: 'bar',
        data: {
            labels: grouped.labels,
            datasets: [{
                label: 'Profit/Loss · ' + baseCurrency,
                data: values,
                backgroundColor: values.map(val => val >= 0 ? '#16a34a' : '#dc2626'),
                hoverBackgroundColor: values.map(val => val >= 0 ? '#15803d' : '#b91c1c'),
                borderRadius: 6,
                maxBarThickness: 46,
                barPercentage: 0.7,
                categoryPercentage: 0.7
            }]
        },
            options: {
            responsive: true,
            maintainAspectRatio: false,
            layout: {
                padding: {
                    top: 40,
                    bottom: 40
                }
            },
            scales: {
                x: {
                    grid: { display: false },
                    ticks: { color: tickColor, maxRotation: 0, autoSkip: true }
                },
                y: {
                    beginAtZero: true,
                    grid: { color: gridColor, drawBorder: false },
                    ticks: { color: tickColor }
                }
            },
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    backgroundColor: '#1e2536',
                    padding: 12,
                    cornerRadius: 10,
                    titleFont: { weight: '700' },
                    callbacks: {
                        label: function (context) {
                            return 'Profit/Loss: ' + signedBaseFormatter.format(Math.round(context.parsed.y)) + ' ' + baseCurrency;
                        },
                        afterLabel: function (context) {
                            const count = monthlyChart?._periodCounts?.[context.dataIndex] || 0;
                            const cashflow = signedBaseFormatter.format(Math.round(monthlyChart?._periodFlows?.[context.dataIndex] || 0)) + ' ' + baseCurrency;
                            return 'Accounts: ' + count + '\nExternal flow: ' + cashflow;
                        }
                    }
                }
            }
        },
                plugins: [operationsCountPlugin]
        });
        monthlyChart._periodCounts = counts;
        monthlyChart._periodFlows = flows;
        monthlyChart._periodSourceLabels = grouped.values.map(row => row.sourceLabels);
        monthlyChart._selectedAccountIds = partial ? selectedMonthlyIds : null;
        monthlyChart.update();
        renderTable('monthly-table', ['Period', 'Profit/Loss · ' + baseCurrency], grouped.labels.map((label, i) => [label, signedValue(values[i]) + ' ' + baseCurrency]));
        monthlyChart.options.onClick = function (event, elements) {
            if (!elements.length) return;
            const label = monthlyChart.data.labels[elements[0].index];
            const sourceLabels = monthlyChart._periodSourceLabels?.[elements[0].index] || [label];
            const attributions = sourceLabels.map(sourceLabel => monthlyAttributions[sourceLabel]).filter(Boolean);
            if (!attributions.length) return;
            const money = value => signedBaseFormatter.format(Math.round(Number(value || 0))) + ' ' + baseCurrency;
            const selectedAccountIds = monthlyChart._selectedAccountIds;
            const filteredAttributions = attributions.map(attribution => ({
                ...attribution,
                accounts: (attribution.accounts || []).filter(row =>
                    selectedAccountIds === null || selectedAccountIds.has(Number(row.accountId)))
            }));
            const sum = field => filteredAttributions.reduce((total, attribution) => total + Number(attribution[field] || 0), 0);
            const accountRows = new Map();
            filteredAttributions.forEach(attribution => (attribution.accounts || []).forEach(row => {
                const account = accountRows.get(row.accountId) || { profit: 0, flow: 0 };
                account.profit += Number(row.monthlyProfit || 0);
                account.flow += Number(row.netFlow || 0);
                accountRows.set(row.accountId, account);
            }));
            const panel = document.getElementById('monthly-attribution-panel');
            panel.replaceChildren();
            const appendLine = (tag, text) => {
                const element = document.createElement(tag);
                element.textContent = text;
                panel.appendChild(element);
            };
            appendLine('strong', label + ' · ' + money(monthlyChart.data.datasets[0].data[elements[0].index]));
            if (selectedAccountIds === null) {
                appendLine('div', 'Opening equity: ' + money(filteredAttributions[0].openingEquity));
                appendLine('div', 'Closing equity: ' + money(filteredAttributions[filteredAttributions.length - 1].closingEquity));
                appendLine('div', 'External deposits: ' + money(sum('deposits')));
                appendLine('div', 'External withdrawals: -' + money(sum('withdrawals')));
                appendLine('div', 'Market and FX movement: ' + money(sum('marketAndFxMovement')));
                appendLine('div', 'Realized trading result: ' + money(sum('realizedTradingResult')));
                appendLine('div', 'Dividends: ' + money(sum('dividends')));
                appendLine('div', 'Cash interest: ' + money(sum('cashInterest')));
                appendLine('div', 'Fees: ' + money(sum('fees')));
                appendLine('div', 'Taxes: ' + money(sum('taxes')));
                appendLine('div', 'Unresolved residual: ' + money(sum('unresolvedResidual')));
            }
            appendLine('strong', 'By account');
            accountRows.forEach((account, accountId) => appendLine('div', accountId + ': ' + money(account.profit) + ' · flow ' + money(account.flow)));
            document.getElementById('monthly-attribution-details').open = true;
        };
    }
    renderMonthlyChart('monthly');
    enableKeyboardChart(monthlyChart, 'monthly-performance-chart', () => {});
    document.getElementById('monthly-granularity')?.addEventListener('change', event => renderMonthlyChart(event.target.value));
    document.querySelectorAll('.js-monthly-account').forEach(input => input.addEventListener('change', () => renderMonthlyChart(document.getElementById('monthly-granularity').value)));
    document.getElementById('monthly-check-all')?.addEventListener('click', () => {
        document.querySelectorAll('.js-monthly-account').forEach(input => input.checked = true);
        renderMonthlyChart(document.getElementById('monthly-granularity').value);
    });
    document.getElementById('monthly-uncheck-all')?.addEventListener('click', () => {
        document.querySelectorAll('.js-monthly-account').forEach(input => input.checked = false);
        renderMonthlyChart(document.getElementById('monthly-granularity').value);
    });

    function setPerformanceMode(mode) {
        const overview = mode === 'overview';
        const accounts = mode === 'accounts';
        const benchmark = document.getElementById('benchmark-content');
        const accountPanel = document.getElementById('performance-accounts-panel');
        const attributionPanel = document.getElementById('performance-attribution-panel');
        if (benchmark) benchmark.style.display = overview ? '' : 'none';
        if (accountPanel) accountPanel.style.display = accounts ? '' : 'none';
        if (attributionPanel) attributionPanel.style.display = overview || mode === 'attribution' ? '' : 'none';
        document.getElementById('performance-display-field')?.style.setProperty('display', accounts ? '' : 'none');
        document.querySelectorAll('.js-performance-mode').forEach(button => {
            const active = button.dataset.mode === mode;
            button.classList.toggle('btn-primary', active);
            button.classList.toggle('btn-outline-secondary', !active);
            button.setAttribute('aria-pressed', String(active));
        });
        const metric = document.getElementById('performance-metric')?.value || 'both';
        const returnVisible = overview && metric !== 'pl';
        const plVisible = (overview && metric !== 'return') || mode === 'attribution';
        if (benchmark) benchmark.style.display = returnVisible ? '' : 'none';
        if (attributionPanel) attributionPanel.style.display = plVisible ? '' : 'none';
        if (mode === 'accounts') updateAccountValueChart(selectedAccountValueAccountIds());
        document.getElementById('performance-scope-aggregation').textContent = document.getElementById('monthly-granularity')?.value || 'Monthly';
    }
    document.querySelectorAll('.js-performance-mode').forEach(button => button.addEventListener('click', () => setPerformanceMode(button.dataset.mode)));
    document.getElementById('performance-metric')?.addEventListener('change', () => setPerformanceMode(document.querySelector('.js-performance-mode.btn-primary')?.dataset.mode || 'overview'));
    document.getElementById('monthly-granularity')?.addEventListener('change', () => setPerformanceMode(document.querySelector('.js-performance-mode.btn-primary')?.dataset.mode || 'overview'));
    setPerformanceMode('overview');

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
                performanceBoardChart = new Chart(performanceBoardEl.getContext('2d'), { type: bars ? 'bar' : 'line', data: { labels, datasets }, options: { responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false }, scales: { x: { stacked: bars && performanceBoardState.metric === 'pl', grid: { display: false }, ticks: { color: tickColor, maxRotation: 0, autoSkip: true } }, y: { stacked: bars && performanceBoardState.metric === 'pl', grid: { color: gridColor, drawBorder: false }, ticks: { color: tickColor, callback: value => performanceBoardState.metric === 'return' ? percentValue(value) : amountFormatter.format(Math.round(value)) } } }, plugins: { legend: { display: true, position: 'top', align: 'end', labels: { usePointStyle: true, pointStyle: 'circle', boxWidth: 8, boxHeight: 8, color: currentChartTheme.legend, font: {size: 12} } }, tooltip: { callbacks: { label: context => context.dataset.label + ': ' + (Number(context.parsed.y) >= 0 ? '+' : '') + (unit === '%' ? percentValue(context.parsed.y) : amountFormatter.format(Math.round(context.parsed.y)) + ' ' + unit) } } } } });
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

    function applyChartTheme() {
        currentChartTheme = chartTheme();
        gridColor = currentChartTheme.grid;
        tickColor = currentChartTheme.legend;
        Chart.defaults.color = tickColor;
        Chart.defaults.borderColor = gridColor;

        if (accountValueChart?.data?.datasets?.[0]?.label === 'Portfolio') {
            accountValueChart.data.datasets[0].borderColor = currentChartTheme.strong;
            accountValueChart.data.datasets[0].backgroundColor = currentChartTheme.strongFill;
        }

        [benchmarkChart, accountValueChart, monthlyChart, performanceBoardChart].forEach(chart => {
            if (!chart) return;

            ['x', 'y'].forEach(axisName => {
                const axis = chart.options.scales?.[axisName];
                if (!axis) return;
                if (axis.ticks) axis.ticks.color = tickColor;
                if (axis.grid && axis.grid.display !== false) axis.grid.color = gridColor;
            });

            const legendLabels = chart.options.plugins?.legend?.labels;
            if (legendLabels) legendLabels.color = currentChartTheme.legend;

            const tooltip = chart.options.plugins?.tooltip;
            if (tooltip) {
                tooltip.backgroundColor = currentChartTheme.tooltip;
                tooltip.titleColor = currentChartTheme.tooltipText;
                tooltip.bodyColor = currentChartTheme.tooltipText;
            }

            chart.update('none');
        });
    }

    applyChartTheme();
    window.addEventListener('investory:themechange', applyChartTheme);
    activeDashboardCharts = [benchmarkChart, accountValueChart, monthlyChart, performanceBoardChart].filter(Boolean);
    window.InvestoryDashboardCharts = {
        destroy() {
            window.removeEventListener('investory:themechange', applyChartTheme);
            activeDashboardCharts.forEach(chart => chart?.destroy?.());
            activeDashboardCharts = [];
        }
    };
}

