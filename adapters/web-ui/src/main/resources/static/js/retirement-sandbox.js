let sandboxChart;

function renderSandboxChart() {
  const rows = window.retirementSandbox?.rows || [];
  const canvas = document.getElementById('sandbox-chart');
  sandboxChart?.destroy();
  sandboxChart = null;
  if (!canvas || !window.Chart || !rows.length) return;

  sandboxChart = new Chart(canvas, {type: 'line', data: {labels: rows.map(row => row.age), datasets: [
    {label: 'Spending', data: rows.map(row => row.spending), borderColor: '#db2031'},
    {label: 'Cash', data: rows.map(row => row.cash), borderColor: '#00916d'},
    {label: 'Bonds', data: rows.map(row => row.bonds), borderColor: '#376cd5'},
    {label: 'Equities', data: rows.map(row => row.equities), borderColor: '#8b5cf6'},
    {label: 'Unfunded', data: rows.map(row => row.gap), borderColor: '#f59f00', borderDash: [6, 4]}
  ]}, options: {responsive: true, maintainAspectRatio: false, interaction: {mode: 'index', intersect: false}}});
}

renderSandboxChart();
document.addEventListener('turbo:load', renderSandboxChart);
document.addEventListener('turbo:before-cache', () => {
  sandboxChart?.destroy();
  sandboxChart = null;
});
