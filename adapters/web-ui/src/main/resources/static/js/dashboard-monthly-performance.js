import {effectiveAccountIds, enableKeyboardChart, renderTable, selectedMonthlyAccountIds, showChartErrorNear} from './dashboard-chart-shared.js';

export function initMonthlyPerformance({
        Chart, monthlyLabels, monthlyData, monthlyCounts, monthlyCashflow, monthlyAttributions, monthlyPerformanceEl, amountFormatter, percentFormatter, baseCurrency, portfolioId, signedBaseFormatter, signedValue, chartTheme, gridColor, tickColor}) {
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


    return {monthlyChart};
}


