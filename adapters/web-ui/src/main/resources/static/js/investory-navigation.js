import {initDashboard} from './dashboard.js';
import {initSimulationPage} from './simulation-page.js';
import {initSimulationCharts} from './retirement-simulation.js';
import {initLongTermAssets} from './long-term-assets.js';
import {initRealEstateDetail} from './real-estate-detail.js';
import {initAssetDetail} from './asset-detail.js';
import {initRetirementAnalysis} from './retirement-analysis.js';
import {initSimulationPlanEdit} from './simulation-plan-edit-lifecycle.js';
import {initRetirementSandbox} from './retirement-sandbox.js';
import {initTheme} from './theme.js';
import {applyAuthorizationCapabilities} from './investory-authorization.js';

function addCsrfInputs() {
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    if (!token) return;
    document.querySelectorAll('form[method="post"], form[method="POST"]').forEach(form => {
        if (form.querySelector('input[name="_csrf"]')) return;
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = '_csrf';
        input.value = token;
        form.appendChild(input);
    });
}

function initializePage() {
    addCsrfInputs();
    initTheme();
    applyAuthorizationCapabilities();
    initDashboard();
    initSimulationPage();
    initSimulationCharts();
    initLongTermAssets();
    initRealEstateDetail();
    initAssetDetail();
    initRetirementAnalysis();
    initSimulationPlanEdit();
    initRetirementSandbox();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializePage, {once: true});
} else {
    initializePage();
}

export {initializePage};
