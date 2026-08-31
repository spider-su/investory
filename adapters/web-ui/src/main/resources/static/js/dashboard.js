/* Dashboard module entry point. */
import {initDashboardActions} from './dashboard-actions.js';
import {initDashboardCore} from './dashboard-core.js';
import {initDashboardAccessibility} from './dashboard-accessibility.js';
import {initDashboardCharts} from './dashboard-charts.js';

export function initDashboard() {
    if (!document.querySelector('#monthly-performance-chart, #benchmark-chart, #account-value-chart, #performance-board-chart')) return;
    initDashboardActions();
    initDashboardCore();
    initDashboardAccessibility();
    initDashboardCharts();
}

export function destroyDashboard() {
    window.InvestoryDashboardCharts?.destroy?.();
}

