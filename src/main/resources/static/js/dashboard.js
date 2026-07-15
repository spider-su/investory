/* Dashboard companion script. Only progressive-enhancement code that does not need Thymeleaf
   inlining belongs here (chart blocks stay in dashboard.html so they can read `${stats}`). */

// Generic spinner for any classic page-submit form on the dashboard.
document.addEventListener("DOMContentLoaded", function () {
    const form = document.querySelector("form");
    const content = document.getElementById("content");
    const spinner = document.getElementById("spinner");

    if (form && content && spinner) {
        form.addEventListener("submit", function () {
            content.classList.add("d-none");
            spinner.classList.remove("d-none");
        });
    }
});

// Async XLSX/CSV uploader for the "Import statement" card.
(function () {
    var form = document.getElementById('iv-import-form');
    if (!form) return;
    var status = document.getElementById('iv-import-status');

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        var broker = document.getElementById('iv-import-broker').value;
        var fileInput = document.getElementById('iv-import-file');
        if (!fileInput.files || !fileInput.files[0]) {
            status.textContent = 'Pick a file first.';
            return;
        }
        var fd = new FormData();
        fd.append('file', fileInput.files[0]);
        status.textContent = 'Uploading...';

        fetch('/import/broker/' + encodeURIComponent(broker), {
            method: 'POST',
            body: fd,
            credentials: 'same-origin'
        }).then(function (r) {
            return r.json().then(function (body) { return { ok: r.ok, body: body }; })
                .catch(function () { return { ok: r.ok, body: null }; });
        }).then(function (res) {
            if (!res.ok) {
                status.textContent = 'Import failed.';
                return;
            }
            var b = res.body || {};
            status.textContent = (b.duplicate ? 'Already imported. ' : 'Imported. ')
                + 'Rows ' + (b.rowsApplied || 0) + '/' + (b.rowsTotal || 0)
                + (b.rowsFailed ? (', failed ' + b.rowsFailed) : '')
                + ' (' + (b.status || '') + ')';
        }).catch(function (err) {
            status.textContent = 'Import error: ' + err;
        });
    });
})();

const fileInput = document.getElementById('xtb-file-input');
const uploadForm = document.getElementById('xtb-upload-form');
const fileNameBadge = document.getElementById('file-chosen-name');

if (fileInput) {
    fileInput.addEventListener('change', function() {
        if (this.files && this.files.length > 0) {
            const file = this.files[0];
            fileNameBadge.innerText = "Uploading: " + file.name;
            fileNameBadge.style.display = 'inline-block';

            // Prepare multipart data payload
            const formData = new FormData();
            formData.append('file', file);

            // Send asynchronous POST request
            fetch(uploadForm.action, {
                method: 'POST',
                body: formData
                // Browser automatically applies basic auth if logged into localhost:8080
            })
                .then(response => {
                    if (!response.ok) throw new Error('Network response error');
                    return response.json();
                })
                .then(data => {
                    // Populate data into the modal elements
                    document.getElementById('modal-message').innerText = data.message;
                    document.getElementById('modal-status').innerText = data.status;
                    document.getElementById('modal-rows-total').innerText = data.rowsTotal;
                    document.getElementById('modal-rows-applied').innerText = data.rowsApplied;
                    document.getElementById('modal-rows-failed').innerText = data.rowsFailed;

                    // Apply success/failure coloring to status badge
                    const statusBadge = document.getElementById('modal-status');
                    if(data.status === 'APPLIED') {
                        statusBadge.className = "iv-badge iv-badge--pos";
                    } else {
                        statusBadge.className = "iv-badge iv-badge--neg";
                    }

                    // Reveal Modal overlay
                    document.getElementById('status-modal').style.display = 'flex';
                })
                .catch(error => {
                    console.error('Error:', error);
                    alert('Failed to upload statement layout. Check server logs.');
                });
     }
   });
 }
 // Yahoo Portfolio — Generate & Download
 const yahooGenerateBtn = document.getElementById('yahoo-generate-btn');
 const yahooExportStatus = document.getElementById('yahoo-export-status');

 if (yahooGenerateBtn) {
     yahooGenerateBtn.addEventListener('click', function () {
         yahooGenerateBtn.disabled = true;
         yahooExportStatus.innerText = '⏳ Generating…';
         yahooExportStatus.className = 'iv-badge iv-badge--muted';
         yahooExportStatus.style.display = 'inline-block';

         fetch('/export/generate', {
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

                 yahooExportStatus.innerText = '✓ Downloaded: ' + fileName;
                 yahooExportStatus.className = 'iv-badge iv-badge--pos';
             })
             .catch(error => {
                 console.error('Yahoo export error:', error);
                 yahooExportStatus.innerText = '✗ Error: ' + error.message;
                 yahooExportStatus.className = 'iv-badge iv-badge--neg';
             })
             .finally(() => {
                 yahooGenerateBtn.disabled = false;
             });
     });
 }

 // Dashboard — refresh market prices and rebuild derived summaries
 const refreshPricesBtn = document.getElementById('refresh-prices-btn');
 const rebuildSummaryBtn = document.getElementById('rebuild-summary-btn');

 function runDashboardMaintenance(button, url, loadingText, fallbackSuccess, errorLabel) {
     if (!button || !yahooExportStatus) return;
     button.disabled = true;
     yahooExportStatus.innerText = loadingText;
     yahooExportStatus.className = 'iv-badge iv-badge--muted';
     yahooExportStatus.style.display = 'inline-block';

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
         .then(data => {
             yahooExportStatus.innerText = '✓ ' + (data.message || fallbackSuccess);
             yahooExportStatus.className = 'iv-badge iv-badge--pos';
             window.setTimeout(function () {
                 window.location.reload();
             }, 500);
         })
         .catch(error => {
             console.error(errorLabel + ' error:', error);
             yahooExportStatus.innerText = '✗ Error: ' + error.message;
             yahooExportStatus.className = 'iv-badge iv-badge--neg';
         })
         .finally(() => {
             button.disabled = false;
         });
 }

 if (refreshPricesBtn) {
     refreshPricesBtn.addEventListener('click', function () {
         runDashboardMaintenance(
             refreshPricesBtn,
             '/admin/refresh-prices',
             '⏳ Refreshing prices…',
             'Prices refreshed',
             'Dashboard price refresh');
     });
 }

 if (rebuildSummaryBtn) {
     rebuildSummaryBtn.addEventListener('click', function () {
         runDashboardMaintenance(
             rebuildSummaryBtn,
             '/admin/rebuild-monthly',
             '⏳ Rebuilding account stats…',
             'Account stats rebuilt',
             'Dashboard monthly rebuild');
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
         modal.style.display = 'none';
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
             modal.style.display = 'flex';
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
         status.textContent = 'Saving...';
         status.className = 'iv-form-status';

         fetch('/admin/assets/' + encodeURIComponent(symbol) + '/price', {
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
                 status.textContent = 'Saved. Refreshing...';
                 status.className = 'iv-form-status iv-pos';
                 window.setTimeout(function () { window.location.reload(); }, 450);
             })
             .catch(function (error) {
                 status.textContent = 'Error: ' + error.message;
                 status.className = 'iv-form-status iv-neg';
             })
             .finally(function () {
                 saveButton.disabled = false;
             });
     });
 })();

 // Modal Close Function helper
window.closeModal = function() {
    document.getElementById('status-modal').style.display = 'none';
}

// Dashboard usability enhancements.
(function () {
    'use strict';

    function ensureToastRegion() {
        let region = document.querySelector('.iv-toast-region');
        if (!region) {
            region = document.createElement('div');
            region.className = 'iv-toast-region';
            region.setAttribute('aria-live', 'polite');
            region.setAttribute('aria-atomic', 'true');
            document.body.appendChild(region);
        }
        return region;
    }

    window.ivNotify = function (message, type) {
        const region = ensureToastRegion();
        const toast = document.createElement('div');
        toast.className = 'iv-toast iv-toast--' + (type === 'error' ? 'error' : 'success');
        toast.textContent = message;
        region.appendChild(toast);
        window.setTimeout(function () {
            toast.remove();
        }, 4200);
    };

    function setModalState(modal, open) {
        if (!modal) return;
        modal.style.display = open ? 'flex' : 'none';
        modal.setAttribute('aria-hidden', String(!open));
        document.body.classList.toggle('iv-modal-open', open);
    }

    document.querySelectorAll('.iv-modal').forEach(function (modal) {
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.setAttribute('aria-hidden', modal.style.display === 'none' ? 'true' : 'false');
        modal.addEventListener('click', function (event) {
            if (event.target === modal) setModalState(modal, false);
        });
    });

    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape') return;
        const openModal = Array.from(document.querySelectorAll('.iv-modal'))
            .find(function (modal) { return modal.style.display !== 'none'; });
        if (openModal) setModalState(openModal, false);
    });

    // Keep only one disclosure open at a time to reduce visual clutter.
    document.querySelectorAll('details').forEach(function (details) {
        details.addEventListener('toggle', function () {
            if (!details.open) return;
            document.querySelectorAll('details[open]').forEach(function (other) {
                if (other !== details) other.removeAttribute('open');
            });
        });
    });

    const uploadBox = document.querySelector('.iv-upload-box');
    const xtbInput = document.getElementById('xtb-file-input');
    if (uploadBox && xtbInput) {
        uploadBox.setAttribute('tabindex', '0');
        uploadBox.setAttribute('role', 'button');
        uploadBox.setAttribute('aria-label', 'Select an XTB statement file');

        uploadBox.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                xtbInput.click();
            }
        });

        ['dragenter', 'dragover'].forEach(function (name) {
            uploadBox.addEventListener(name, function (event) {
                event.preventDefault();
                uploadBox.classList.add('is-dragover');
            });
        });
        ['dragleave', 'drop'].forEach(function (name) {
            uploadBox.addEventListener(name, function (event) {
                event.preventDefault();
                uploadBox.classList.remove('is-dragover');
            });
        });
        uploadBox.addEventListener('drop', function (event) {
            const files = event.dataTransfer && event.dataTransfer.files;
            if (!files || !files.length) return;
            try {
                const transfer = new DataTransfer();
                transfer.items.add(files[0]);
                xtbInput.files = transfer.files;
                xtbInput.dispatchEvent(new Event('change', { bubbles: true }));
            } catch (error) {
                window.ivNotify('Drop is not supported by this browser. Select the file instead.', 'error');
            }
        });
    }

    // Update navigation state while scrolling.
    const navLinks = Array.from(document.querySelectorAll('.iv-page-nav a'));
    const sections = navLinks
        .map(function (link) { return document.querySelector(link.getAttribute('href')); })
        .filter(Boolean);
    if ('IntersectionObserver' in window && sections.length) {
        const observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (!entry.isIntersecting) return;
                navLinks.forEach(function (link) {
                    link.classList.toggle('is-active', link.getAttribute('href') === '#' + entry.target.id);
                });
            });
        }, { rootMargin: '-20% 0px -70% 0px' });
        sections.forEach(function (section) { observer.observe(section); });
    }
})();


// Trust indicators and contextual FX help.
document.addEventListener('DOMContentLoaded', () => {
    const fxInfo = document.querySelector('.iv-chip--fx-info');
    if (fxInfo) {
        fxInfo.addEventListener('click', event => { event.stopPropagation(); fxInfo.classList.toggle('is-tooltip-open'); });
        document.addEventListener('click', () => fxInfo.classList.remove('is-tooltip-open'));
        fxInfo.addEventListener('keydown', event => { if (event.key === 'Escape') fxInfo.classList.remove('is-tooltip-open'); });
    }
    const reconciledTime = document.querySelector('[data-reconciled-hours]');
    if (reconciledTime) {
        const hours = Number(reconciledTime.dataset.reconciledHours || 0);
        reconciledTime.textContent = hours === 0 ? 'Last reconciled: just now' : `Last reconciled: ${hours} ${hours === 1 ? 'hour' : 'hours'} ago`;
    }
});
