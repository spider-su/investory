/* Dashboard chart feature orchestrator. */
import {
    chartTheme,
    enableKeyboardChart,
    effectiveAccountIds,
    renderTable,
    selectedAccountValueAccountIds,
    selectedMonthlyAccountIds,
    selectedBenchmarkAccountIds,
    showChartErrorNear,
    showChartUnavailable
} from './dashboard-chart-shared.js';
import {initBenchmarkAndAccountValue} from './dashboard-benchmark-account-value.js';
import {initMonthlyPerformance} from './dashboard-monthly-performance.js';
import {initPerformanceMode} from './dashboard-performance-mode.js';
import {initPerformanceBoard} from './dashboard-performance-board.js';
import {readPageData} from './page-data.js';

let activeDashboardCharts = [];

export function initDashboardCharts() {
    /* global Chart */
    const data = readPageData('dashboard-page-data');
    const monthlyLabels = Object.keys(data.monthlyPerformance).sort((a, b) => a.localeCompare(b));
    const monthlyData = monthlyLabels.map(label => Number(data.monthlyPerformance[label] || 0));
    const monthlyCounts = monthlyLabels.map(label => data.monthlyOperationsCount[label] || 0);
    const monthlyCashflow = monthlyLabels.map(label => data.monthlyCashflow[label] || 0);
    const monthlyAttributions = data.monthlyAttributions || {};
    const amountFormatter = new Intl.NumberFormat('en-US', {maximumFractionDigits: 0});
    const percentFormatter = new Intl.NumberFormat('en-US', {minimumFractionDigits: 1, maximumFractionDigits: 1});
    const signedBaseFormatter = new Intl.NumberFormat('en-US', {maximumFractionDigits: 0, signDisplay: 'always'});
    const baseCurrency = data.baseCurrency;
    const portfolioId = data.portfolioId
        ?? window.location.pathname.match(/^\/portfolios\/([^/]+)/)?.[1]
        ?? new URLSearchParams(window.location.search).get('portfolioId');
    const signedValue = value => (Number(value) >= 0 ? '+' : '') + amountFormatter.format(Number(value || 0));
    const signedPercentValue = value => (Number(value) >= 0 ? '+' : '') + percentFormatter.format(Number(value || 0)) + '%';
    const percentValue = value => percentFormatter.format(Number(value || 0)) + '%';
    const theme = chartTheme();
    Chart.defaults.font.family = 'Inter, system-ui, sans-serif';
    Chart.defaults.font.size = 12;
    Chart.defaults.color = theme.legend;
    Chart.defaults.borderColor = theme.grid;

    const common = {
        Chart, amountFormatter, percentFormatter, baseCurrency, portfolioId, signedBaseFormatter,
        chartTheme, renderTable, enableKeyboardChart, signedValue, signedPercentValue,
        showChartUnavailable, showChartErrorNear, gridColor: theme.grid, tickColor: theme.legend,
        percentValue
    };
    const benchmark = initBenchmarkAndAccountValue({
        ...common,
        benchEl: document.getElementById('benchmark-chart'),
        benchLabels: data.benchmarkLabels || [],
        accountValueEl: document.getElementById('account-value-chart'),
        accountValueYears: data.accountValueYears || []
    });
    const monthly = initMonthlyPerformance({
        ...common,
        monthlyLabels, monthlyData, monthlyCounts, monthlyCashflow, monthlyAttributions,
        monthlyPerformanceEl: document.getElementById('monthly-performance-chart')
    });
    initPerformanceMode({updateAccountValueChart: benchmark.updateAccountValueChart, selectedAccountValueAccountIds});
    const board = initPerformanceBoard({
        ...common,
        selectedDashboardPeriod: data.selectedDashboardPeriod || 'YTD'
    });

    const charts = [benchmark.benchmarkChart, benchmark.accountValueChart, monthly.monthlyChart].filter(Boolean);
    function applyChartTheme() {
        const currentTheme = chartTheme();
        Chart.defaults.color = currentTheme.legend;
        Chart.defaults.borderColor = currentTheme.grid;
        if (benchmark.accountValueChart?.data?.datasets?.[0]?.label === 'Portfolio') {
            benchmark.accountValueChart.data.datasets[0].borderColor = currentTheme.strong;
            benchmark.accountValueChart.data.datasets[0].backgroundColor = currentTheme.strongFill;
        }
        [...charts, board.getPerformanceBoardChart()].forEach(chart => {
            if (!chart) return;
            ['x', 'y'].forEach(axisName => {
                const axis = chart.options.scales?.[axisName];
                if (!axis) return;
                if (axis.ticks) axis.ticks.color = currentTheme.legend;
                if (axis.grid && axis.grid.display !== false) axis.grid.color = currentTheme.grid;
            });
            const labels = chart.options.plugins?.legend?.labels;
            if (labels) labels.color = currentTheme.legend;
            const tooltip = chart.options.plugins?.tooltip;
            if (tooltip) {
                tooltip.backgroundColor = currentTheme.tooltip;
                tooltip.titleColor = currentTheme.tooltipText;
                tooltip.bodyColor = currentTheme.tooltipText;
            }
            chart.update('none');
        });
    }
    applyChartTheme();
    window.addEventListener('investory:themechange', applyChartTheme);
    activeDashboardCharts = charts;
    window.InvestoryDashboardCharts = {
        destroy() {
            window.removeEventListener('investory:themechange', applyChartTheme);
            activeDashboardCharts.forEach(chart => chart?.destroy?.());
            board.getPerformanceBoardChart()?.destroy?.();
            activeDashboardCharts = [];
        }
    };
}
