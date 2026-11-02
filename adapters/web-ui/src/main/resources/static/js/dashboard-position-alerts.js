/* Editable position thresholds; no trading calls. */
export function initDashboardPositionAlerts() {
    const modal = document.getElementById('position-alert-modal');
    const form = document.getElementById('position-alert-form');
    const context = document.getElementById('position-alert-context');
    const status = document.getElementById('position-alert-status');
    const historyList = document.getElementById('position-alert-history-list');
    const save = document.getElementById('position-alert-save');
    if (!modal || !form || !context || !status || !historyList || !save) return;

    const pageData = document.getElementById('dashboard-page-data');
    const portfolioId = pageData ? JSON.parse(pageData.textContent || '{}').portfolioId : null;
    if (!portfolioId) return;

    let activeSymbol = null;
    let originButton = null;
    const setModal = (open) => {
        if (window.ivSetModalState) window.ivSetModalState(modal, open);
        else modal.style.display = open ? 'flex' : 'none';
        if (!open && originButton) originButton.focus();
    };
    function url(symbol) {
        return '/api/v1/portfolios/' + encodeURIComponent(portfolioId)
            + '/position-alerts/' + encodeURIComponent(symbol);
    }
    function headers() {
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        return Object.assign({ 'Content-Type': 'application/json' },
            token ? { 'X-XSRF-TOKEN': token } : {});
    }
    async function request(endpoint, options) {
        const response = await fetch(endpoint, Object.assign({ credentials: 'same-origin' }, options));
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            throw new Error(body.message || body.detail || 'Unable to load position alerts (' + response.status + ')');
        }
        return response.json();
    }
    function field(side) {
        return form.querySelector('[data-side="' + side + '"]');
    }
    function fill(data) {
        const number = value => value == null ? 'Unavailable' : Number(value).toLocaleString('en-US', { maximumFractionDigits: 5 });
        context.textContent = 'Average ' + number(data.averageCost) + ' ' + (data.currency || '')
            + ' · Latest ' + number(data.price) + ' ' + (data.currency || '')
            + ' · quote ' + (data.quoteDate || 'unavailable');
        data.rules.forEach(rule => {
            const container = field(rule.side);
            if (!container) return;
            container.querySelector('[name="mode"]').value = rule.mode;
            container.querySelector('[name="threshold"]').value = rule.threshold;
            container.querySelector('[name="action"]').value = rule.actionText;
            container.querySelector('[name="enabled"]').checked = rule.enabled;
            container.classList.toggle('iv-position-alert-rule--active', rule.active);
        });
    }
    async function loadHistory(symbol) {
        const items = await request(url(symbol) + '/history');
        historyList.replaceChildren();
        if (!items.length) {
            const li = document.createElement('li');
            li.textContent = 'No alerts yet. Existing price levels are baselined on first evaluation.';
            historyList.append(li);
            return;
        }
        items.forEach(item => {
            const li = document.createElement('li');
            const strong = document.createElement('strong');
            strong.textContent = item.side + ' · ' + item.deliveryState
                + ' · ' + new Date(item.createdAt).toLocaleString();
            const message = document.createElement('div');
            message.textContent = item.message || '';
            li.append(strong, message);
            historyList.append(li);
        });
    }
    const bells = Array.from(document.querySelectorAll('.iv-position-alert-bell'));
    request('/api/v1/portfolios/' + encodeURIComponent(portfolioId) + '/position-alerts/eligible')
        .then(symbols => {
            const eligible = new Set(symbols);
            bells.forEach(button => {
                if (eligible.has(button.dataset.symbol)) button.style.display = 'inline-flex';
            });
        })
        .catch(() => { /* Fail closed: do not show unsupported asset editors. */ });
    bells.forEach(button => {
        button.addEventListener('click', async event => {
            event.preventDefault();
            event.stopPropagation();
            activeSymbol = button.dataset.symbol;
            if (!activeSymbol) return;
            originButton = button;
            document.getElementById('position-alert-title').textContent = activeSymbol + ' · Price alerts';
            status.textContent = 'Loading…';
            setModal(true);
            try {
                fill(await request(url(activeSymbol)));
                await loadHistory(activeSymbol);
                status.textContent = '';
            } catch (error) {
                status.textContent = error.message;
            }
        });
    });
    document.getElementById('position-alert-close')?.addEventListener('click', () => setModal(false));
    document.getElementById('position-alert-cancel')?.addEventListener('click', () => setModal(false));
    modal.addEventListener('click', event => { if (event.target === modal) setModal(false); });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (!activeSymbol) return;
        const rules = ['LOSS', 'PROFIT'].map(side => {
            const section = field(side);
            return {
                side,
                mode: section.querySelector('[name="mode"]').value,
                threshold: Number(section.querySelector('[name="threshold"]').value),
                actionText: section.querySelector('[name="action"]').value.trim(),
                enabled: section.querySelector('[name="enabled"]').checked
            };
        });
        save.disabled = true;
        status.textContent = 'Saving…';
        try {
            fill(await request(url(activeSymbol), {
                method: 'PUT', headers: headers(), body: JSON.stringify({ rules })
            }));
            status.textContent = 'Saved. Price changes will be checked at 15:00 Warsaw on weekdays.';
            await loadHistory(activeSymbol);
        } catch (error) {
            status.textContent = error.message;
        } finally {
            save.disabled = false;
        }
    });
}
