// Modal Close Function helper
window.closeModal = function() {
    window.ivSetModalState(document.getElementById('status-modal'), false);
};

// Sort account and open-position popover rows without moving their total rows.
(function () {
    function compareValues(left, right, direction) {
        const leftNumber = Number(left);
        const rightNumber = Number(right);
        const leftIsNumber = left !== '' && Number.isFinite(leftNumber);
        const rightIsNumber = right !== '' && Number.isFinite(rightNumber);

        if (leftIsNumber && rightIsNumber) {
            return (leftNumber - rightNumber) * direction;
        }
        if (leftIsNumber !== rightIsNumber) {
            return leftIsNumber ? -1 : 1;
        }
        return String(left || '').localeCompare(String(right || ''), undefined, {
            numeric: true,
            sensitivity: 'base'
        }) * direction;
    }

    document.querySelectorAll('.iv-sortable-grid').forEach(function (grid) {
        const buttons = Array.from(grid.querySelectorAll(':scope > .iv-balance-popover__row--head .iv-sort-button'));
        buttons.forEach(function (button) {
            button.addEventListener('click', function () {
                const key = button.dataset.sortKey;
                const ascending = !button.classList.contains('is-sort-asc');
                const direction = ascending ? 1 : -1;
                const rows = Array.from(grid.querySelectorAll(':scope > [data-sort-row]'));
                const totalRow = Array.from(grid.children).find(function (child) {
                    return child.classList.contains('iv-balance-popover__row--total')
                        || child.classList.contains('iv-position-popover__row--total');
                }) || null;

                rows.sort(function (left, right) {
                    return compareValues(left.dataset['sort' + key.charAt(0).toUpperCase() + key.slice(1)],
                        right.dataset['sort' + key.charAt(0).toUpperCase() + key.slice(1)], direction);
                });
                rows.forEach(function (row) { grid.insertBefore(row, totalRow); });

                buttons.forEach(function (candidate) {
                    candidate.classList.remove('is-sort-asc', 'is-sort-desc');
                    candidate.removeAttribute('aria-pressed');
                });
                button.classList.add(ascending ? 'is-sort-asc' : 'is-sort-desc');
                button.setAttribute('aria-pressed', 'true');
            });
        });
    });
})();
