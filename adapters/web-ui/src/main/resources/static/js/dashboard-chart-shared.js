/* Shared dashboard chart helpers. */

export function cssThemeColor(name, fallback) {
    const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return value || fallback;
}

export function chartTheme() {
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

export function renderTable(id, headers, rows) {
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

export function enableKeyboardChart(chart, canvasId, activate) {
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

export function showChartUnavailable(emptyEl, contentEl, message) {
    if (emptyEl) {
        emptyEl.textContent = message || 'This chart is temporarily unavailable.';
        emptyEl.style.display = '';
    }
    if (contentEl) contentEl.style.display = 'none';
}

export function showChartErrorNear(chartElement, message) {
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

export function selectedBenchmarkAccountIds() {
    return new Set(Array.from(document.querySelectorAll('.js-benchmark-account:checked')).map(input => Number(input.value)));
}

export function selectedAccountValueAccountIds() {
    return new Set(Array.from(document.querySelectorAll('.js-account-value-account:checked')).map(input => Number(input.value)));
}

export function selectedMonthlyAccountIds() {
    return new Set(Array.from(document.querySelectorAll('.js-monthly-account:checked')).map(input => Number(input.value)));
}

export function effectiveAccountIds(selectedIds, allIds) {
    return selectedIds.size === allIds.length ? new Set(allIds) : selectedIds;
}

