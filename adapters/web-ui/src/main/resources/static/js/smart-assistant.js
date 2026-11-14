/** Progressive enhancement for the scoped, stateless v1 RAG endpoint. */
export function initSmartAssistant() {
    const root = document.getElementById('iv-smart-assistant');
    if (!root || root.dataset.initialized === 'true') return;
    const portfolioId = root.dataset.portfolioId;
    if (!/^[1-9]\d*$/.test(portfolioId || '')) return;
    const trigger = root.querySelector('#iv-assistant-trigger');
    const panel = root.querySelector('#iv-assistant-panel');
    const close = root.querySelector('#iv-assistant-close');
    const form = root.querySelector('#iv-assistant-form');
    const input = root.querySelector('#iv-assistant-question');
    const mode = root.querySelector('#iv-assistant-mode');
    const send = root.querySelector('#iv-assistant-send');
    const messages = root.querySelector('#iv-assistant-messages');
    const status = root.querySelector('#iv-assistant-status');
    root.dataset.initialized = 'true';
    let busy = false;
    let abortController = null;

    function toggle(open) {
        panel.hidden = !open;
        trigger.setAttribute('aria-expanded', String(open));
        if (open) input.focus();
        else {
            abortController?.abort();
            trigger.focus();
        }
    }
    trigger.addEventListener('click', () => toggle(panel.hidden));
    close.addEventListener('click', () => toggle(false));
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !panel.hidden) toggle(false);
    });

    function appendMessage(text, kind) {
        const item = document.createElement('div');
        item.className = 'iv-assistant__bubble iv-assistant__bubble--' + kind;
        // No innerHTML: question, generated text, and citations are untrusted.
        item.textContent = text;
        messages.appendChild(item);
        messages.scrollTop = messages.scrollHeight;
        return item;
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const question = input.value.trim();
        const questionMode = mode.value === 'docs' ? 'docs' : 'portfolio';
        if (busy || !question || question.length > 2000) return;
        busy = true;
        input.disabled = true;
        mode.disabled = true;
        send.disabled = true;
        status.textContent = 'Thinking…';
        appendMessage(question, 'user');
        input.value = '';
        const controller = new AbortController();
        abortController = controller;
        try {
            const csrf = document.querySelector('meta[name="_csrf"]')?.content;
            const headers = {'Content-Type': 'application/json'};
            if (csrf) headers['X-XSRF-TOKEN'] = csrf;
            const response = await fetch('/api/v1/portfolios/' + portfolioId + '/rag/' +
                (questionMode === 'docs' ? 'ask-docs' : 'ask'), {
                method: 'POST', credentials: 'same-origin', headers,
                body: JSON.stringify({question}), signal: controller.signal
            });
            if (controller.signal.aborted) return;
            if (!response.ok) {
                if (response.status === 401 || response.status === 403) throw new Error('You do not have access to this portfolio. Sign in again if needed.');
                if (response.status === 429) throw new Error('Assistant is busy or your request limit was reached. Please try again later.');
                if (response.status === 502 || response.status === 503 || response.status === 504) throw new Error('Assistant is temporarily unavailable. Please try again later.');
                throw new Error('Unable to answer this question right now.');
            }
            const data = await response.json().catch(() => { throw new Error("Assistant response unavailable. Please try again."); });
            if (controller.signal.aborted) return;
            if (!data || typeof data.answer !== 'string' || !data.answer.trim() ||
                !Array.isArray(data.sources) || data.sources.some(s => typeof s !== 'string' || !s.trim())) {
                throw new Error('Assistant response unavailable. Please try again.');
            }
            appendMessage(data.answer, 'answer');
            if (data.sources.length) {
                const sources = document.createElement('p');
                sources.className = 'iv-assistant__sources';
                // The backend returns source labels, not trusted navigable URLs.
                sources.textContent = 'Sources: ' + data.sources.filter(s => typeof s === 'string')
                    .map(s => s === 'live:profile' ? 'Portfolio data' :
                        s === 'live:investment-performance' ? 'Investment performance (MAX)' : s).join(', ');
                messages.appendChild(sources);
            }
            status.textContent = '';
        } catch (error) {
            if (!controller.signal.aborted && error.name !== 'AbortError') appendMessage(error.message || 'Assistant unavailable.', 'error');
            status.textContent = '';
        } finally {
            if (abortController === controller) abortController = null;
            busy = false;
            input.disabled = false;
            mode.disabled = false;
            send.disabled = false;
            if (!panel.hidden) input.focus();
        }
    });
}
