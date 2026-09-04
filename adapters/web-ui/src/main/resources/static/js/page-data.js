export function readPageData(id, fallback = {}) {
    const element = document.getElementById(id);
    if (!element) return fallback;
    try {
        return JSON.parse(element.textContent || '') || fallback;
    } catch (error) {
        console.error(`Could not read page data from #${id}`, error);
        return fallback;
    }
}
