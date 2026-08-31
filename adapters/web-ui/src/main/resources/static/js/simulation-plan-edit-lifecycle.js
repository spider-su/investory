let cleanup = () => {};

export function initSimulationPlanEdit() {
  const form = document.getElementById('plan-editor-form');
  if (!form) return;

  const currentPlanningYear = window.investorySimulationPlanEdit?.currentPlanningYear ?? 0;
  const start = document.getElementById('plan-start-year');
  const age = document.getElementById('age-at-plan-start');
  const retirement = document.getElementById('retirement-age');
  const end = document.getElementById('end-age');
  const inputs = [start, age, retirement, end].filter(Boolean);
  const setError = (field, message) => {
    const node = document.querySelector(`[data-plan-error="${field}"]`);
    if (node) node.textContent = message;
  };
  const validate = () => {
    const s = Number(start?.value), a = Number(age?.value), r = Number(retirement?.value), e = Number(end?.value);
    const current = a + currentPlanningYear - s;
    const retirementError = !Number.isInteger(r) || r < a
      ? `Retirement age cannot be before Plan start age ${a}.`
      : r > e ? `Retirement age cannot be after plan exit age ${e}.` : '';
    const endError = !Number.isInteger(e) || e < current
      ? `Plan exit age cannot be before the current planning age ${current}.` : '';
    setError('retirementAge', retirementError);
    setError('endAge', endError);
    retirement?.setCustomValidity(retirementError);
    end?.setCustomValidity(endError);
    return !retirementError && !endError;
  };
  const onSubmit = event => { if (!validate()) event.preventDefault(); };
  const onFormData = event => document.querySelectorAll('[form="plan-editor-form"]').forEach(control => {
    if ((control.type === 'checkbox' || control.type === 'radio') && !control.checked) return;
    if (control.name && !event.formData.has(control.name)) event.formData.append(control.name, control.value);
  });
  const onFocus = event => event.target.select();
  const rows = document.getElementById('expense-profile-rows');
  const hidden = document.getElementById('expense-profile');
  const sync = () => {
    if (!rows || !hidden) return;
    hidden.value = [...rows.querySelectorAll('.iv-expense-profile__row')]
      .map(row => [row.querySelector('[data-expense-age]')?.value, row.querySelector('[data-expense-percent]')?.value])
      .filter(value => value[0] !== '' && value[1] !== '').map(value => value.join(':')).join(';');
  };
  form.noValidate = true;
  inputs.forEach(input => input.addEventListener('input', validate));
  form.addEventListener('submit', onSubmit);
  form.addEventListener('formdata', onFormData);
  document.querySelectorAll('input[type="number"][form="plan-editor-form"]').forEach(input => input.addEventListener('focus', onFocus));
  rows?.querySelectorAll('input').forEach(input => input.addEventListener('input', sync));
  validate();
  sync();
  cleanup = () => {
    inputs.forEach(input => input.removeEventListener('input', validate));
    form.removeEventListener('submit', onSubmit);
    form.removeEventListener('formdata', onFormData);
    document.querySelectorAll('input[type="number"][form="plan-editor-form"]').forEach(input => input.removeEventListener('focus', onFocus));
    rows?.querySelectorAll('input').forEach(input => input.removeEventListener('input', sync));
    cleanup = () => {};
  };
}

export function destroySimulationPlanEdit() { cleanup(); }
