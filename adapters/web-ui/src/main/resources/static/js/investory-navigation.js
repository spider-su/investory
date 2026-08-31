import {initDashboard, destroyDashboard} from './dashboard.js';
import {initSimulationPage, destroySimulationPage} from './simulation-page.js';
import {initSimulationCharts, destroySimulationCharts} from './retirement-simulation.js';
import {initLongTermAssets, destroyLongTermAssets} from './long-term-assets.js';
import {initRealEstateDetail} from './real-estate-detail.js';
import {initAssetDetail, destroyAssetDetail} from './asset-detail.js';
import {initRetirementAnalysis, destroyRetirementAnalysis} from './retirement-analysis.js';
import {initSimulationPlanEdit, destroySimulationPlanEdit} from './simulation-plan-edit-lifecycle.js';

let initializedPageRoot = null;

if (window.Turbo?.config?.drive) {
    // The UI uses full-document server redirects. Keep the vendored Turbo
    // runtime available for the shared theme, but do not let Drive intercept
    // links and forms until the pages provide Turbo-compatible responses.
    window.Turbo.config.drive.preloading = false;
    window.Turbo.session.drive = false;
}

function initializePage() {
    const pageRoot = document.querySelector('main') || document.body;
    if (initializedPageRoot === pageRoot) return;
    initializedPageRoot = pageRoot;
    initDashboard();
    initSimulationPage();
    initSimulationCharts();
    initLongTermAssets();
    initRealEstateDetail();
    initAssetDetail();
    initRetirementAnalysis();
    initSimulationPlanEdit();
}

function beforeCache() {
    initializedPageRoot = null;
    destroyDashboard();
    destroySimulationPage();
    destroySimulationCharts();
    destroyLongTermAssets();
    destroyAssetDetail();
    destroyRetirementAnalysis();
    destroySimulationPlanEdit();
    document.querySelectorAll('canvas').forEach(canvas => window.Chart?.getChart?.(canvas)?.destroy?.());
    document.querySelectorAll('.iv-modal').forEach(modal => {
        if (modal.style.display !== 'none') modal.style.display = 'none';
    });
    document.body.classList.remove('iv-modal-open');
    document.querySelectorAll('.iv-toast-region, [data-turbo-temporary]').forEach(element => element.remove());
    document.querySelectorAll('[aria-busy="true"]').forEach(element => {
        element.removeAttribute('aria-busy');
        if ('disabled' in element) element.disabled = false;
    });
}

document.addEventListener('turbo:load', initializePage);
document.addEventListener('turbo:before-cache', beforeCache);

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializePage, {once: true});
} else {
    initializePage();
}

// Turbo 8 may be loaded after this module on a hard visit only when script
// scheduling changes; the event is still the single initialization boundary.
export {initializePage, beforeCache};
