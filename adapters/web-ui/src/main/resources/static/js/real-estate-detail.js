export function initRealEstateDetail() {
  const panels = [...document.querySelectorAll('.iv-rental-contract')];
  panels.forEach(panel => panel.addEventListener('toggle', () => {
    if (panel.open) panels.forEach(other => { if (other !== panel) other.open = false; });
  }));

  document.querySelectorAll('[data-edit-contract]').forEach(button => button.addEventListener('click', () => {
    const body = button.closest('.iv-rental-contract__body');
    body.querySelector('[data-contract-read]').hidden = true;
    const form = body.querySelector('[data-contract-edit]');
    form.hidden = false;
    form.querySelector('input:not([type="hidden"]), select')?.focus();
  }));
  document.querySelectorAll('[data-cancel-edit]').forEach(button => button.addEventListener('click', () => {
    const body = button.closest('.iv-rental-contract__body');
    body.querySelector('[data-contract-edit]').hidden = true;
    body.querySelector('[data-contract-read]').hidden = false;
  }));
  document.querySelectorAll('[data-contract-edit][data-edit-initial="true"]').forEach(form => {
    form.closest('.iv-rental-contract').open = true;
    form.hidden = false;
    form.closest('.iv-rental-contract__body').querySelector('[data-contract-read]').hidden = true;
  });

  document.querySelectorAll('[data-terminate-contract]').forEach(button => button.addEventListener('click', () => {
    const panel = button.closest('[data-contract-read]').querySelector('[data-terminate-panel]');
    panel.hidden = false;
    panel.querySelector('input[type="date"]').focus();
  }));
  document.querySelectorAll('[data-cancel-terminate]').forEach(button => button.addEventListener('click', () => {
    button.closest('[data-terminate-panel]').hidden = true;
  }));
  document.querySelectorAll('[data-delete-contract]').forEach(form => form.addEventListener('submit', event => {
    if (!window.confirm('Delete this contract permanently? Its terms will be removed and historical calculations may change.')) event.preventDefault();
  }));

  const addForm = document.querySelector('[data-add-contract]');
  document.querySelector('[data-show-add-contract]')?.addEventListener('click', () => {
    addForm.hidden = false;
    addForm.querySelector('input:not([type="hidden"])')?.focus();
  });
  document.querySelector('[data-cancel-add-contract]')?.addEventListener('click', () => { addForm.hidden = true; });
  document.querySelector('[data-copy-latest]')?.addEventListener('click', () => {
    const source = document.querySelector('[data-contract-edit]');
    if (!source) return;
    addForm.querySelectorAll('[name]').forEach(target => {
      if (target.name === 'portfolioId' || target.name === 'endCurrentContractBeforeStart') return;
      const origin = source.querySelector(`[name="${target.name}"]`);
      if (!origin) return;
      if (target.type === 'checkbox') target.checked = origin.checked;
      else target.value = origin.value;
    });
    const start = addForm.querySelector('[name="startDate"]');
    start.value = addForm.dataset.suggestedStart || '';
    addForm.querySelector('[name="endDate"]').value = '';
    start.focus();
  });
}

