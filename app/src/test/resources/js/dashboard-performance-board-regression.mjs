import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const source = readFileSync('adapters/web-ui/src/main/resources/static/js/dashboard-performance-board.js', 'utf8');
const {initPerformanceBoard} = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
function element(dataset = {}) {
  return {dataset, checked: true, value: '1', style: {}, classList: {toggle() {}},
    handlers: {}, addEventListener(event, handler) {this.handlers[event] = handler;}, getContext() {return {};}};
}
for (const period of ['YTD', '1Y']) for (const aggregation of ['monthly', 'quarterly', 'annual']) for (const filtered of [false, true]) {
  const ids = new Map();
  const get = id => {if (!ids.has(id)) ids.set(id, element()); return ids.get(id);};
  get('performance-board-aggregation').value = aggregation;
  const metricButtons = ['return', 'pl'].map(metric => element({metric}));
  const styleButtons = ['line', 'bars'].map(style => element({style}));
  const account = element();
  global.document = {getElementById: get, querySelector: () => account, querySelectorAll: selector =>
    selector === '.js-performance-board-metric' ? metricButtons : selector === '.js-performance-board-style' ? styleButtons :
    selector.startsWith('.js-performance-board-account') ? (filtered ? [account] : []) : []};
  const label = aggregation === 'monthly' ? ['2026-01', '2026-02'] : aggregation === 'quarterly' ? ['2026-Q1'] : ['2026'];
  const baseline = aggregation === 'monthly' ? '2025-12' : aggregation === 'quarterly' ? '2025-Q4' : '2025';
  const values = profit => aggregation === 'monthly' ? (profit ? [6481, 14297] : [7.6, 16.8]) : [profit ? 14297 : 16.8];
  const view = profit => ({available: true, labels: label, series: [{accountId: filtered ? 1 : null, label: filtered ? 'Broker' : 'Portfolio', values: values(profit)}],
    accounts: [{id: 1, name: 'Broker', selected: true}], benchmarkValues: aggregation === 'monthly' ? [1.5, 11.8] : [11.8], kpis: {portfolioProfitLoss: 14297}});
  const requests = [];
  global.fetch = async url => {requests.push(url); return {ok: true, json: async () => view(url.includes('metric=profit'))};};
  let chart, table;
  class Chart {constructor(_, config) {chart = this; this.config = config; this.data = config.data;} update() {} destroy() {}}
  initPerformanceBoard({Chart, selectedDashboardPeriod: period, portfolioId: 1, baseCurrency: 'USD',
    amountFormatter: new Intl.NumberFormat('en-US'), percentFormatter: new Intl.NumberFormat('en-US'), chartTheme: () => ({}),
    renderTable: (_, headers, rows) => {table = {headers, rows};}, enableKeyboardChart() {}, percentValue: value => value.toFixed(1) + '%', initialView: view(false)});
  async function click(button) {button.handlers.click(); await new Promise(resolve => setImmediate(resolve));}
  for (const metric of [false, true]) {
    await click(metricButtons[metric ? 1 : 0]);
    assert.deepEqual(chart.data.labels, [baseline, ...label], `${period}/${aggregation}/${filtered}/${metric ? 'P/L' : 'Return'} opening labels`);
    assert.deepEqual(chart.data.datasets[0].data, [0, ...values(metric)]);
    assert.deepEqual(table.rows.map(row => row[0]), chart.data.labels);
    assert.equal(table.rows[0][1], metric ? '+0 USD' : '+0.0%');
    if (metric && aggregation === 'monthly') assert.equal(table.rows[1][1], '+6,481 USD');
    assert.equal(table.rows.at(-1)[1], metric ? '+14,297 USD' : '+16.8%');
    if (!metric) assert.equal(chart.data.datasets[1].data.at(-1), 11.8);
    await click(styleButtons[1]);
    assert.deepEqual(chart.data.labels, label);
    assert.deepEqual(chart.data.datasets[0].data, values(metric));
    assert.deepEqual(table.rows.map(row => row[0]), label);
    await click(styleButtons[0]);
  }
  assert(requests.every(url => url.includes('period=' + period)));
  assert(requests.every(url => url.includes('accountIds=1') === filtered));
}
console.log('PASS: Return/P&L, chart/table, YTD/1Y, monthly/quarterly/annual, all/filtered accounts, Line/Bars; January/final/benchmark unchanged');
