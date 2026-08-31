export function initLongTermAssets() {
document.querySelectorAll('.iv-planning-section__details').forEach((details) => {
    const summary = details.querySelector(':scope > summary');
    if (!summary) return;
    const syncAriaState = () => summary.setAttribute('aria-expanded', String(details.open));
    details.addEventListener('toggle', syncAriaState);
    syncAriaState();
  });
  document.addEventListener('click', (event) => {
    const link = event.target.closest('a[href^="#"]');
    if (!link) return;
    const target = document.getElementById(link.getAttribute('href').slice(1));
    const details = target instanceof HTMLDetailsElement ? target : target?.querySelector(':scope > .iv-planning-section__details');
    if (!(details instanceof HTMLDetailsElement)) return;
    details.open = true;
    requestAnimationFrame(() => target.scrollIntoView({block: 'start'}));
  });

}
export function destroyLongTermAssets() {}

