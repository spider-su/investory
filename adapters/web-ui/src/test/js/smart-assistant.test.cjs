const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(
    path.resolve(__dirname, '../../main/resources/static/js/smart-assistant.js'), 'utf8');
const modulePromise = import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));

class Element {
    constructor() {
        this.dataset = {};
        this.listeners = {};
        this.children = [];
        this.hidden = false;
        this.disabled = false;
        this.textContent = '';
        this.value = '';
        this.attributes = {};
    }
    addEventListener(type, callback) { this.listeners[type] = callback; }
    appendChild(child) { this.children.push(child); return child; }
    setAttribute(name, value) { this.attributes[name] = value; }
    focus() { this.focused = true; }
}

function setup(portfolioId = '21') {
    const names = ['trigger', 'panel', 'close', 'form', 'question', 'mode', 'send', 'messages', 'status'];
    const nodes = Object.fromEntries(names.map(name => [name, new Element()]));
    nodes.panel.hidden = true;
    nodes.mode.value = 'portfolio';
    const root = new Element();
    root.dataset.portfolioId = portfolioId;
    root.querySelector = selector => {
        const found = selector.match(/^#iv-assistant-(.*)$/);
        return found && nodes[found[1]] || null;
    };
    const previousDocument = global.document;
    const previousFetch = global.fetch;
    global.document = {
        getElementById: id => id === 'iv-smart-assistant' ? root : null,
        querySelector: selector => selector === 'meta[name="_csrf"]' ? {content: 'csrf-test'} : null,
        createElement: () => new Element(),
        addEventListener: () => {}
    };
    return {
        root, nodes,
        restore() {
            global.document = previousDocument;
            global.fetch = previousFetch;
        }
    };
}

test('opens, sends same-origin CSRF request, renders answer and sources safely', async () => {
    const harness = setup();
    try {
        const {initSmartAssistant} = await modulePromise;
        const calls = [];
        global.fetch = async (url, options) => {
            calls.push({url, options});
            return {ok: true, json: async () => ({
                answer: '<script>alert(1)</script> Net worth: 100 PLN [live:profile]',
                sources: ['live:profile']
            })};
        };
        initSmartAssistant();
        harness.nodes.trigger.listeners.click();
        assert.equal(harness.nodes.panel.hidden, false);
        assert.equal(harness.nodes.trigger.attributes['aria-expanded'], 'true');
        harness.nodes.question.value = 'What is my net worth?';
        await harness.nodes.form.listeners.submit({preventDefault() {}});
        assert.equal(calls.length, 1);
        assert.equal(calls[0].url, '/api/v1/portfolios/21/rag/ask');
        assert.equal(calls[0].options.method, 'POST');
        assert.equal(calls[0].options.credentials, 'same-origin');
        assert.equal(calls[0].options.headers['X-XSRF-TOKEN'], 'csrf-test');
        assert.deepEqual(JSON.parse(calls[0].options.body), {question: 'What is my net worth?'});
        assert.match(harness.nodes.messages.children[1].textContent, /<script>/);
        assert.equal(harness.nodes.messages.children[2].textContent, 'Sources: Portfolio data');
        assert.equal(harness.nodes.send.disabled, false);
        harness.nodes.close.listeners.click();
        assert.equal(harness.nodes.panel.hidden, true);
        assert.equal(harness.nodes.trigger.attributes['aria-expanded'], 'false');
    } finally { harness.restore(); }
});

test('one in-flight request; 429 shows a human-readable failure', async () => {
    const harness = setup();
    try {
        const {initSmartAssistant} = await modulePromise;
        let reply;
        let count = 0;
        global.fetch = () => {
            count += 1;
            return new Promise(resolve => { reply = resolve; });
        };
        initSmartAssistant();
        harness.nodes.question.value = 'What is my net worth?';
        const first = harness.nodes.form.listeners.submit({preventDefault() {}});
        assert.equal(harness.nodes.send.disabled, true);
        const second = harness.nodes.form.listeners.submit({preventDefault() {}});
        await second;
        assert.equal(count, 1);
        reply({ok: false, status: 429});
        await first;
        assert.match(harness.nodes.messages.children[1].textContent, /request limit/);
        assert.equal(harness.nodes.send.disabled, false);
    } finally { harness.restore(); }
});

test('documentation questions use the separate read-only endpoint', async () => {
    const harness = setup();
    try {
        const {initSmartAssistant} = await modulePromise;
        const calls = [];
        global.fetch = async (url, options) => {
            calls.push({url, options});
            return {ok: true, json: async () => ({
                answer: 'Investory calculates returns using documented cash-flow rules [method.md]',
                sources: ['method.md']
            })};
        };
        initSmartAssistant();
        harness.nodes.mode.value = 'docs';
        harness.nodes.question.value = 'How does Investory calculate returns?';
        await harness.nodes.form.listeners.submit({preventDefault() {}});
        assert.equal(calls[0].url, '/api/v1/portfolios/21/rag/ask-docs');
        assert.deepEqual(JSON.parse(calls[0].options.body),
            {question: 'How does Investory calculate returns?'});
        assert.equal(calls[0].options.headers['X-XSRF-TOKEN'], 'csrf-test');
        assert.equal(harness.nodes.mode.disabled, false);
        assert.equal(harness.nodes.messages.children[2].textContent, 'Sources: method.md');
    } finally { harness.restore(); }
});

test('invalid portfolio IDs cannot create a request handler', async () => {
    const harness = setup('not-a-portfolio');
    try {
        const {initSmartAssistant} = await modulePromise;
        initSmartAssistant();
        assert.equal(harness.nodes.form.listeners.submit, undefined);
    } finally { harness.restore(); }
});

test('malformed responses show a friendly error and permit another request', async () => {
    const {initSmartAssistant} = await modulePromise;
    for (const body of [null, {}, {answer: ' ', sources: []},
        {answer: 'Reply', sources: null}, {answer: 'Reply', sources: [42]},
        {answer: 'Reply', sources: ['']}]) {
        const harness = setup();
        try {
            global.fetch = async () => ({ok: true, json: async () => body});
            initSmartAssistant();
            harness.nodes.question.value = 'What is my net worth?';
            await harness.nodes.form.listeners.submit({preventDefault() {}});
            assert.equal(harness.nodes.messages.children[1].textContent,
                'Assistant response unavailable. Please try again.');
            assert.equal(harness.nodes.send.disabled, false);
            assert.equal(harness.nodes.mode.disabled, false);
            global.fetch = async () => ({ok: true, json: async () => ({
                answer: 'Recovered [method.md]', sources: ['method.md']
            })});
            harness.nodes.question.value = 'Explain the methodology';
            await harness.nodes.form.listeners.submit({preventDefault() {}});
            assert.equal(harness.nodes.messages.children[3].textContent, 'Recovered [method.md]');
        } finally { harness.restore(); }
    }
});

test('closing during response parsing discards the cancelled answer silently', async () => {
    const harness = setup();
    try {
        const {initSmartAssistant} = await modulePromise;
        let rejectBody;
        let reading;
        const bodyStarted = new Promise(resolve => { reading = resolve; });
        global.fetch = async () => ({ok: true, json: () => {
            reading();
            return new Promise((_, reject) => { rejectBody = reject; });
        }});
        initSmartAssistant();
        harness.nodes.trigger.listeners.click();
        harness.nodes.question.value = 'What is my net worth?';
        const request = harness.nodes.form.listeners.submit({preventDefault() {}});
        await bodyStarted;
        harness.nodes.close.listeners.click();
        rejectBody(Object.assign(new Error('Body aborted'), {name: 'AbortError'}));
        await request;
        assert.equal(harness.nodes.messages.children.length, 1);
        assert.equal(harness.nodes.status.textContent, '');
        assert.equal(harness.nodes.send.disabled, false);
        assert.equal(harness.nodes.mode.disabled, false);
    } finally { harness.restore(); }
});

test('closing discards a late successful body even if the transport ignores cancellation', async () => {
    const harness = setup();
    try {
        const {initSmartAssistant} = await modulePromise;
        let resolveBody;
        let reading;
        const bodyStarted = new Promise(resolve => { reading = resolve; });
        global.fetch = async () => ({ok: true, json: () => {
            reading();
            return new Promise(resolve => { resolveBody = resolve; });
        }});
        initSmartAssistant();
        harness.nodes.trigger.listeners.click();
        harness.nodes.question.value = 'What is my net worth?';
        const request = harness.nodes.form.listeners.submit({preventDefault() {}});
        await bodyStarted;
        harness.nodes.close.listeners.click();
        resolveBody({answer: 'Discarded', sources: []});
        await request;
        assert.equal(harness.nodes.messages.children.length, 1);
        assert.equal(harness.nodes.send.disabled, false);
    } finally { harness.restore(); }
});
