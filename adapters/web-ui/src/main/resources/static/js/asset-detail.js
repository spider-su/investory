let assetPriceChart = null;
export function initAssetDetail() {
if (!window.investoryAssetDetail) return;
const pricePoints = window.investoryAssetDetail.pricePoints;
if (pricePoints.length) {
    const context = document.getElementById('asset-price-chart');
    assetPriceChart = new Chart(context, {
        type: 'line',
        data: {
            labels: pricePoints.map(point => point.date),
            datasets: [{
                label: window.investoryAssetDetail.label,
                data: pricePoints.map(point => point.closePrice),
                borderWidth: 2,
                pointRadius: pricePoints.length > 90 ? 0 : 2,
                tension: 0.15
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                tooltip: {
                    callbacks: {
                        afterLabel: context => {
                            const point = pricePoints[context.dataIndex];
                            return [point.currency, point.source, point.qualityClass].filter(Boolean);
                        }
                    }
                }
            },
            scales: { x: { ticks: { maxTicksLimit: 12 } } }
        }
    });
    const priceHistoryDetails = document.getElementById('price-history');
    priceHistoryDetails.addEventListener('toggle', () => {
        if (priceHistoryDetails.open) {
            requestAnimationFrame(() => assetPriceChart.resize());
        }
    });
}

const manualPriceForm = document.getElementById('manual-price-form');
if (!manualPriceForm) return;
    manualPriceForm.addEventListener('submit', async event => {
        event.preventDefault();
        const priceInput = document.getElementById('manual-market-price');
        const submitButton = manualPriceForm.querySelector('button[type="submit"]');
        const status = document.getElementById('manual-price-status');
        submitButton.disabled = true;
        status.className = 'text-secondary';
        status.textContent = 'Saving price…';
        try {
            const response = await fetch(manualPriceForm.dataset.url, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({marketPrice: Number(priceInput.value)})
            });
            if (!response.ok) {
                throw new Error('Couldn’t save the price.');
            }
            status.textContent = 'Price saved';
            window.setTimeout(() => window.location.reload(), 500);
        } catch (error) {
            status.className = 'text-danger';
            status.textContent = error.message;
            submitButton.disabled = false;
        }
    });
}
export function destroyAssetDetail() {
    assetPriceChart?.destroy();
    assetPriceChart = null;
}


