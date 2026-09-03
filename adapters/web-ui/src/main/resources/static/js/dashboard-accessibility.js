export function initDashboardAccessibility() {
// Dashboard usability enhancements.
(function () {
    'use strict';

    function setModalState(modal, open) {
        if (window.ivSetModalState) {
            window.ivSetModalState(modal, open);
        }
    }

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


    document.querySelectorAll('.iv-modal').forEach(function (modal) {
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.setAttribute('aria-hidden', modal.style.display === 'none' ? 'true' : 'false');
        modal.addEventListener('click', function (event) {
            if (event.target === modal) setModalState(modal, false);
        });
    });

    if (!window.__investoryDashboardAccessibilityGlobals) document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape') return;
        const openModal = Array.from(document.querySelectorAll('.iv-modal'))
            .find(function (modal) { return modal.style.display !== 'none'; });
        if (openModal) {
            setModalState(openModal, false);
            return;
        }
        const openDetails = Array.from(document.querySelectorAll('details[open]')).pop();
        if (openDetails) {
            openDetails.removeAttribute('open');
            const trigger = openDetails.querySelector(':scope > summary');
            if (trigger) trigger.focus();
        }
    });

    // Only disclosures that opt into the same named group are mutually exclusive.
    document.querySelectorAll('details').forEach(function (details) {
        details.addEventListener('toggle', function () {
            const group = details.dataset.disclosureGroup;
            if (!details.open || !group) return;
            document.querySelectorAll('details[open][data-disclosure-group="' + group + '"]').forEach(function (other) {
                if (other !== details) other.removeAttribute('open');
            });
        });
    });

    if (!window.__investoryDashboardAccessibilityGlobals) document.addEventListener('click', function (event) {
        if (event.target.closest && event.target.closest('details')) return;
        document.querySelectorAll('details.iv-compact-popover[open][data-disclosure-group]').forEach(function (details) {
            details.removeAttribute('open');
        });
        document.querySelectorAll('.iv-metric-context:focus').forEach(function (metric) {
            if (!metric.contains(event.target)) metric.blur();
        });
    });

    function placeCompactPopover(details) {
        const panel = details.querySelector(':scope > .iv-compact-popover__panel');
        const trigger = details.querySelector(':scope > summary');
        if (!panel || !trigger || !details.open) return;
        details.classList.remove('placement-top');
        panel.style.setProperty('--iv-compact-shift-x', '0px');
        if (window.matchMedia('(max-width: 640px)').matches) return;

        const triggerRect = trigger.getBoundingClientRect();
        const panelHeight = Math.min(panel.scrollHeight, 320, window.innerHeight - 32);
        const spaceBelow = window.innerHeight - triggerRect.bottom - 16;
        const spaceAbove = triggerRect.top - 16;
        details.classList.toggle('placement-top', spaceBelow < panelHeight && spaceAbove > spaceBelow);

        const panelRect = panel.getBoundingClientRect();
        const leftCorrection = Math.max(0, 16 - panelRect.left);
        const rightCorrection = Math.max(0, panelRect.right - (window.innerWidth - 16));
        panel.style.setProperty('--iv-compact-shift-x', (leftCorrection - rightCorrection) + 'px');
    }

    const compactPopovers = Array.from(document.querySelectorAll('details.iv-compact-popover'));
    compactPopovers.forEach(function (details) {
        details.addEventListener('toggle', function () {
            if (details.open) window.requestAnimationFrame(function () { placeCompactPopover(details); });
        });
    });
    function repositionOpenCompactPopover() {
        const open = document.querySelector('details.iv-compact-popover[open]');
        if (open) placeCompactPopover(open);
    }
    if (!window.__investoryDashboardAccessibilityGlobals) {
        window.addEventListener('resize', repositionOpenCompactPopover);
        window.addEventListener('scroll', repositionOpenCompactPopover, true);
    }

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

    // Preserve the current benchmark-account selection when changing dashboard period.
    const periodLinks = Array.from(
        document.querySelectorAll('.iv-period-nav[aria-label="Dashboard period"] a')
    );
    periodLinks.forEach(function (link) {
        link.addEventListener('click', function (event) {
            event.preventDefault();

            const target = new URL(link.href, window.location.origin);
            target.searchParams.delete('accountIds');
            target.searchParams.set('benchmarkAccountsSubmitted', 'true');

            document.querySelectorAll('.js-benchmark-account:checked')
                .forEach(function (account) {
                    target.searchParams.append('accountIds', account.value);
                });

            const destination = target.pathname + target.search + target.hash;
            if (window.Turbo?.session?.drive) {
                window.Turbo.visit(destination);
            } else {
                window.location.assign(destination);
            }
        });
    });

    // Update navigation state while scrolling.
    const navLinks = Array.from(document.querySelectorAll('.iv-page-nav a'))
        .filter(function (link) {
            return (link.getAttribute('href') || '').startsWith('#');
        });
    const sections = navLinks
        .map(function (link) {
            return document.querySelector(link.getAttribute('href'));
        })
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
    const fxInfo = document.querySelector('.iv-chip--fx-info');
    if (fxInfo) {
        fxInfo.addEventListener('click', event => { event.stopPropagation(); fxInfo.classList.toggle('is-tooltip-open'); });
        fxInfo.addEventListener('keydown', event => { if (event.key === 'Escape') fxInfo.classList.remove('is-tooltip-open'); });
    }
    const reconciledTime = document.querySelector('[data-reconciled-hours]');
    if (reconciledTime) {
        const hours = Number(reconciledTime.dataset.reconciledHours || 0);
        reconciledTime.textContent = hours === 0 ? 'Last reconciled: just now' : `Last reconciled: ${hours} ${hours === 1 ? 'hour' : 'hours'} ago`;
    }
    window.__investoryDashboardAccessibilityGlobals = true;
}
/* Legacy block intentionally removed: page setup is driven by turbo:load. */
/*
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
*/
