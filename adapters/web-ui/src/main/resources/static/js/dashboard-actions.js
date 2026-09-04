export function initDashboardActions() {
/* Dashboard companion script. Only progressive-enhancement code that does not need Thymeleaf
   inlining belongs here (chart blocks stay in dashboard.html so they can read `${stats}`). */

function setModalState(modal, open) {
    if (!modal) return;
    modal.style.display = open ? 'flex' : 'none';
    modal.setAttribute('aria-hidden', String(!open));
    document.body.classList.toggle('iv-modal-open', open);
}
window.ivSetModalState = setModalState;

const fileInput = document.getElementById('xtb-file-input');
const uploadForm = document.getElementById('xtb-upload-form');
const importStatementBtn = document.getElementById('import-statement-btn');
const fileNameBadge = document.getElementById('file-chosen-name');

if (importStatementBtn && fileInput) {
    importStatementBtn.addEventListener('click', function () {
        fileInput.click();
    });
}

if (fileInput) {
    fileInput.addEventListener('change', function() {
        if (this.files && this.files.length > 0) {
            const files = Array.from(this.files || []);
            if (!files.length) return;
            const originalImportText = importStatementBtn ? importStatementBtn.innerHTML : 'Import';
            if (importStatementBtn) {
                importStatementBtn.disabled = true;
                importStatementBtn.setAttribute('aria-busy', 'true');
                importStatementBtn.innerHTML = '<span class="iv-spinner" aria-hidden="true"></span> Importing statement…';
            }
            if (yahooGenerateBtn) yahooGenerateBtn.disabled = true;
            if (refreshPricesBtn) refreshPricesBtn.disabled = true;
            fileNameBadge.innerText = "Uploading " + files.length + " file" + (files.length === 1 ? '' : 's') + "…";
            fileNameBadge.style.display = 'inline-block';

            // Import files in order. Defer the expensive derived-data rebuild until the last file.
            let data;
            Promise.resolve().then(async function () {
                for (let index = 0; index < files.length; index++) {
                    const file = files[index];
                    fileNameBadge.innerText = 'Uploading file ' + (index + 1) + ' of ' + files.length + ': ' + file.name;
                    const formData = new FormData(uploadForm);
                    formData.set('file', file);
                    const url = new URL(uploadForm.action, window.location.origin);
                    url.searchParams.set('deferRefresh', String(index < files.length - 1));
                    const response = await fetch(url.toString(), { method: 'POST', body: formData });
                    const body = await response.json().catch(function () { return {}; });
                    if (!response.ok) throw new Error(body.message || ('HTTP ' + response.status));
                    data = body;
                }
                return data;
            }).then(data => {
                    // Populate data into the modal elements
                    document.getElementById('modal-message').innerText = data.message;
                    document.getElementById('modal-rows-total').innerText = data.rowsTotal;
                    document.getElementById('modal-rows-applied').innerText = data.rowsApplied;
                    document.getElementById('modal-rows-failed').innerText = data.rowsFailed;

                    // Show the row outcome. The batch status is COMPLETED for both full and
                    // partial imports, so it cannot drive this presentation by itself.
                    const statusBadge = document.getElementById('modal-status');
                    const modalTitle = document.getElementById('modal-title');
                    const reconciliationLink = document.getElementById('modal-reconciliation-link');
                    statusBadge.innerText = data.status;
                    const rowsApplied = Number(data.rowsApplied) || 0;
                    const rowsSkipped = Number(data.rowsFailed) || 0;
                    if (rowsSkipped === 0) {
                        statusBadge.className = "iv-badge iv-badge--pos";
                    } else if (rowsApplied > 0) {
                        statusBadge.className = "iv-badge iv-badge--warn";
                    } else {
                        statusBadge.className = "iv-badge iv-badge--neg";
                    }
                    if (modalTitle) {
                        modalTitle.innerText = rowsSkipped === 0 ? 'Import complete' : 'Import completed with issues';
                    }
                    if (reconciliationLink) {
                        reconciliationLink.style.display = rowsSkipped === 0 ? 'none' : 'inline-flex';
                    }

                    // Reveal Modal overlay
                    setModalState(document.getElementById('status-modal'), true);
                })
                .catch(error => {
                    console.error('Error:', error);
                    alert(error.message || 'Couldn\u2019t import this statement. Check the file and try again.');
                })
                .finally(() => {
                    if (importStatementBtn) {
                        importStatementBtn.disabled = false;
                        importStatementBtn.removeAttribute('aria-busy');
                        importStatementBtn.innerHTML = originalImportText;
                    }
                    if (yahooGenerateBtn) yahooGenerateBtn.disabled = false;
                    if (refreshPricesBtn) refreshPricesBtn.disabled = false;
                    if (fileNameBadge) {
                        fileNameBadge.textContent = '';
                        fileNameBadge.style.display = 'none';
                    }
                });
     }
   });
 }
 // Yahoo Portfolio — Generate & Download
 const yahooGenerateBtn = document.getElementById('yahoo-generate-btn');

 if (yahooGenerateBtn) {
     yahooGenerateBtn.addEventListener('click', function () {
         const originalExportHtml = yahooGenerateBtn.innerHTML;
         yahooGenerateBtn.disabled = true;
         yahooGenerateBtn.setAttribute('aria-busy', 'true');
         yahooGenerateBtn.innerHTML = '<span class="iv-spinner" aria-hidden="true"></span> Preparing export…';

         const exportUrl = new URL('/api/v1/investment/export/generate', window.location.origin);
         const exportPortfolioId = yahooGenerateBtn.dataset.portfolioId
             || new URLSearchParams(window.location.search).get('portfolioId');
         if (exportPortfolioId) exportUrl.searchParams.set('portfolioId', exportPortfolioId);

         fetch(exportUrl.toString(), {
             method: 'GET',
             credentials: 'same-origin'
         })
             .then(response => {
                 if (!response.ok) {
                     throw new Error('HTTP ' + response.status);
                 }
                 return response.blob().then(blob => ({ blob, response }));
             })
             .then(({ blob, response }) => {
                 const contentDisposition = response.headers.get('content-disposition') || '';
                 let fileName = 'yahoo-portfolio.csv';
                 const match = contentDisposition.match(/filename="?([^";\r\n]+)"?/);
                 if (match) fileName = match[1];

                 const url = window.URL.createObjectURL(blob);
                 const a = document.createElement('a');
                 a.href = url;
                 a.download = fileName;
                 document.body.appendChild(a);
                 a.click();
                 window.URL.revokeObjectURL(url);
                 document.body.removeChild(a);

                 if (window.ivNotify) {
                     window.ivNotify('Portfolio exported', 'success');
                 }
             })
             .catch(error => {
                 console.error('Yahoo export error:', error);
                 if (window.ivNotify) {
                     window.ivNotify('Couldn’t create the export. Try again.', 'error');
                 }
             })
             .finally(() => {
                 yahooGenerateBtn.disabled = false;
                 yahooGenerateBtn.removeAttribute('aria-busy');
                 yahooGenerateBtn.innerHTML = originalExportHtml;
             });
     });
 }

 // Dashboard — refresh market prices and rebuild derived summaries
 const refreshPricesBtn = document.getElementById('refresh-prices-btn');

 function elapsedLabel(text, startedAt) {
     return '<span class="iv-spinner" aria-hidden="true"></span> ' + text + ' (' + Math.floor((Date.now() - startedAt) / 1000) + 's)';
 }

 function runDashboardMaintenance(button, url, loadingText, successMessage, failureMessage) {
     if (!button) return;
     button.disabled = true;
     button.setAttribute('aria-busy', 'true');
     const originalButtonHtml = button.innerHTML;
     const startedAt = Date.now();
     const timer = window.setInterval(function () {
         button.innerHTML = elapsedLabel(loadingText, startedAt);
     }, 1000);
     button.innerHTML = elapsedLabel(loadingText, startedAt);

     fetch(url, {
         method: 'POST',
         credentials: 'same-origin'
     })
         .then(response => {
             if (!response.ok) {
                 throw new Error('HTTP ' + response.status);
             }
             return response.json();
         })
         .then(() => {
             if (window.ivNotify) {
                 window.ivNotify(successMessage, 'success');
             }
             window.setTimeout(function () {
                 window.location.reload();
             }, 700);
         })
         .catch(error => {
             console.error('Dashboard maintenance error:', error);
             if (window.ivNotify) {
                 window.ivNotify(failureMessage, 'error');
             }
         })
         .finally(() => {
             window.clearInterval(timer);
             button.disabled = false;
             button.removeAttribute('aria-busy');
             button.innerHTML = originalButtonHtml;
         });
 }

 if (refreshPricesBtn) {
     refreshPricesBtn.addEventListener('click', function () {
         runDashboardMaintenance(
             refreshPricesBtn,
             '/api/v1/investment/maintenance/update-history',
             'Updating market data…',
              'Market data updated',
              'Couldn’t update market data.');
     });
 }

 const refreshCurrencyBtn = document.getElementById('refresh-currency-btn');
 const refreshCurrencyStatus = document.getElementById('refresh-currency-status');
 if (refreshCurrencyBtn) {
     refreshCurrencyBtn.addEventListener('click', function () {
         const originalText = refreshCurrencyBtn.textContent;
         refreshCurrencyBtn.disabled = true;
         refreshCurrencyBtn.setAttribute('aria-busy', 'true');
         refreshCurrencyBtn.innerHTML = '<span class="iv-spinner" aria-hidden="true"></span> Updating exchange rates…';
         if (refreshCurrencyStatus) refreshCurrencyStatus.textContent = '';
         fetch('/api/v1/investment/maintenance/refresh-currency', { method: 'POST', credentials: 'same-origin' })
             .then(response => {
                 if (!response.ok) throw new Error('HTTP ' + response.status);
                 return response.json();
             })
             .then(data => {
                 if (data.failed && data.failed.length) {
                     throw new Error(data.failed.join(', '));
                 }
                 if (refreshCurrencyStatus) refreshCurrencyStatus.textContent = 'Exchange rates updated';
                 window.setTimeout(() => window.location.reload(), 500);
             })
             .catch(error => {
                 if (refreshCurrencyStatus) refreshCurrencyStatus.textContent = 'Couldn’t update exchange rates.';
                 if (window.ivNotify) window.ivNotify('Couldn’t update exchange rates.', 'error');
             })
             .finally(() => {
                 refreshCurrencyBtn.disabled = false;
                 refreshCurrencyBtn.removeAttribute('aria-busy');
                 refreshCurrencyBtn.textContent = originalText;
             });
     });
 }

 // Manual asset price entry for stale position rows.
 (function () {
     const modal = document.getElementById('manual-price-modal');
     const form = document.getElementById('manual-price-form');
     const symbolInput = document.getElementById('manual-price-symbol');
     const averageOpenInput = document.getElementById('manual-price-open');
     const priceInput = document.getElementById('manual-price-value');
     const status = document.getElementById('manual-price-status');
     const saveButton = document.getElementById('manual-price-save');
     const closeButton = document.getElementById('manual-price-close');
     const cancelButton = document.getElementById('manual-price-cancel');

     if (!modal || !form || !symbolInput || !averageOpenInput || !priceInput || !status || !saveButton) {
         return;
     }

     function formatPrice(value) {
         const number = Number(value);
         if (!Number.isFinite(number) || number <= 0) {
             return '';
         }
         return number.toLocaleString(undefined, {
             minimumFractionDigits: 0,
             maximumFractionDigits: 6
         });
     }

     function closeManualPriceModal() {
         setModalState(modal, false);
         status.textContent = '';
         averageOpenInput.value = '';
         priceInput.value = '';
     }

     document.querySelectorAll('.iv-manual-price-button').forEach(function (button) {
         button.addEventListener('click', function () {
             symbolInput.value = button.getAttribute('data-symbol') || '';
             averageOpenInput.value = formatPrice(button.getAttribute('data-average-open-price'));
             priceInput.value = '';
             status.textContent = '';
             setModalState(modal, true);
             window.setTimeout(function () { priceInput.focus(); }, 0);
         });
     });

     if (closeButton) closeButton.addEventListener('click', closeManualPriceModal);
     if (cancelButton) cancelButton.addEventListener('click', closeManualPriceModal);

     form.addEventListener('submit', function (event) {
         event.preventDefault();
         const symbol = symbolInput.value;
         const price = Number(priceInput.value);
         if (!symbol || !Number.isFinite(price) || price <= 0) {
             status.textContent = 'Enter a positive market price.';
             status.className = 'iv-form-status iv-neg';
             return;
         }

         saveButton.disabled = true;
         status.textContent = 'Saving price…';
         status.className = 'iv-form-status';

         fetch('/api/v1/investment/maintenance/assets/' + encodeURIComponent(symbol) + '/price', {
             method: 'POST',
             credentials: 'same-origin',
             headers: { 'Content-Type': 'application/json' },
             body: JSON.stringify({ marketPrice: price })
         })
             .then(function (response) {
                 return response.json().catch(function () { return {}; })
                     .then(function (body) { return { ok: response.ok, status: response.status, body: body }; });
             })
             .then(function (result) {
                 if (!result.ok) {
                     throw new Error(result.body.message || ('HTTP ' + result.status));
                 }
                 status.textContent = 'Price saved';
                 status.className = 'iv-form-status iv-pos';
                 window.setTimeout(function () { window.location.reload(); }, 450);
             })
             .catch(function (error) {
                 status.textContent = 'Couldn’t save the price.';
                 status.className = 'iv-form-status iv-neg';
             })
             .finally(function () {
                 saveButton.disabled = false;
             });
     });
 })();

}
