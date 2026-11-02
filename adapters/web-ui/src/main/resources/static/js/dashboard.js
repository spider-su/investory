/* Dashboard module entry point. */
import {initDashboardActions} from './dashboard-actions.js';
import {initDashboardCore} from './dashboard-core.js';
import {initDashboardAccessibility} from './dashboard-accessibility.js';
import {initDashboardCharts} from './dashboard-charts.js';
import {initDashboardPositionAlerts} from './dashboard-position-alerts.js';

export function initDashboard() {
    if (!document.querySelector('#monthly-performance-chart, #benchmark-chart, #account-value-chart, #performance-board-chart')) return;
    initDashboardActions();
    initDashboardPositionAlerts();
    initDashboardCore();
    initDashboardAccessibility();
    initDashboardCharts();
}

