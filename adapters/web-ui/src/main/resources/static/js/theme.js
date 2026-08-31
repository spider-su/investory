(function () {
    'use strict';

    const STORAGE_KEY = 'investory.theme';
    const DARK_QUERY = window.matchMedia('(prefers-color-scheme: dark)');
    const root = document.documentElement;

    function storedTheme() {
        try {
            const value = window.localStorage.getItem(STORAGE_KEY);
            return value === 'light' || value === 'dark' ? value : null;
        } catch (ignored) {
            return null;
        }
    }

    function preferredTheme() {
        return storedTheme() || (DARK_QUERY.matches ? 'dark' : 'light');
    }

    function updateToggle(theme) {
        const button = document.getElementById('theme-toggle');
        if (!button) return;

        const nextTheme = theme === 'dark' ? 'light' : 'dark';
        const label = 'Switch to ' + nextTheme + ' mode';
        button.setAttribute('aria-label', label);
        button.setAttribute('title', label);
        button.setAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
    }

    function applyTheme(theme, persist, notify) {
        const resolved = theme === 'dark' ? 'dark' : 'light';
        root.dataset.theme = resolved;
        root.dataset.bsTheme = resolved;

        if (persist) {
            try {
                window.localStorage.setItem(STORAGE_KEY, resolved);
            } catch (ignored) {
                // Storage can be unavailable in privacy-restricted browser contexts.
            }
        }

        updateToggle(resolved);

        if (notify) {
            window.dispatchEvent(new CustomEvent('investory:themechange', {
                detail: { theme: resolved }
            }));
        }
    }

    function wireToggle() {
        updateToggle(root.dataset.theme || preferredTheme());

        const button = document.getElementById('theme-toggle');
        if (!button) return;

        button.addEventListener('click', function () {
            const next = root.dataset.theme === 'dark' ? 'light' : 'dark';
            applyTheme(next, true, true);
        });
    }

    applyTheme(preferredTheme(), false, false);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', wireToggle);
    } else {
        wireToggle();
    }

    const followSystemTheme = function (event) {
        if (!storedTheme()) {
            applyTheme(event.matches ? 'dark' : 'light', false, true);
        }
    };

    if (typeof DARK_QUERY.addEventListener === 'function') {
        DARK_QUERY.addEventListener('change', followSystemTheme);
    } else if (typeof DARK_QUERY.addListener === 'function') {
        DARK_QUERY.addListener(followSystemTheme);
    }

    window.InvestoryTheme = {
        current: function () {
            return root.dataset.theme || preferredTheme();
        },
        set: function (theme) {
            applyTheme(theme, true, true);
        },
        useSystem: function () {
            try {
                window.localStorage.removeItem(STORAGE_KEY);
            } catch (ignored) {
                // Ignore unavailable storage and still apply the current system preference.
            }
            applyTheme(DARK_QUERY.matches ? 'dark' : 'light', false, true);
        }
    };
})();
