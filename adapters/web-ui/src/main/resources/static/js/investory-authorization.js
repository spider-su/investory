function applyAuthorizationCapabilities() {
    const capabilities = window.investoryCapabilities || {};
    if (capabilities.canEdit !== false) return;
    document.querySelectorAll('form[method="post"], form[method="POST"]').forEach(form => {
        form.querySelectorAll('button, input[type="submit"], input[type="image"]').forEach(control => {
            control.disabled = true;
            control.setAttribute('aria-disabled', 'true');
            control.title = 'Read-only profile';
        });
    });
    document.querySelectorAll('a[href*="/new/"], a[href*="/edit"], a[href*="/delete"]').forEach(link => {
        link.classList.add('disabled');
        link.setAttribute('aria-disabled', 'true');
        link.addEventListener('click', event => event.preventDefault(), {once: true});
    });
}

document.addEventListener('DOMContentLoaded', applyAuthorizationCapabilities, {once: true});
document.addEventListener('turbo:load', applyAuthorizationCapabilities);
