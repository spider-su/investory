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
            fetch('/import/xtb', {
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

// Modal Close Function helper
window.closeModal = function() {
    document.getElementById('status-modal').style.display = 'none';
}