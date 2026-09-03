export function initPerformanceMode({
        updateAccountValueChart, selectedAccountValueAccountIds}) {
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


    return {setPerformanceMode};
}



