(function () {
  const { API, fetchJson } = window.Homeward;
  const form = document.querySelector('[data-application-form]');
  if (!form) return;
  const availability = document.querySelector('[data-application-availability]');
  const message = form.querySelector('[data-application-message]');
  const button = form.querySelector('[type=submit]');
  let pending = false;
  fetchJson(`${API}/applications/config`).then((config) => {
    availability.textContent = config.enabled ? '' : '寻亲申请暂未开放，请稍后再试。';
    form.hidden = !config.enabled;
  }).catch((error) => { availability.textContent = error.message; });
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (pending || form.hidden || !form.reportValidity()) return;
    const values = Object.fromEntries(new FormData(form));
    values.consent = form.elements.namedItem('consent').checked;
    if (String(values.description || '').trim().length < 20) {
      message.textContent = '详细情况至少填写 20 字。';
      return;
    }
    pending = true;
    button.disabled = true;
    message.textContent = '正在提交…';
    try {
      const result = await fetchJson(`${API}/applications`, { method: 'POST', body: JSON.stringify(values) });
      form.reset();
      form.hidden = true;
      availability.textContent = result.message;
    } catch (error) {
      message.textContent = error.message;
    } finally {
      pending = false;
      button.disabled = false;
    }
  });
})();
