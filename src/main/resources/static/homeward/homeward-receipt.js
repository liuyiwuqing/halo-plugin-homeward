(function () {
  const { API, fetchJson } = window.Homeward;
  const labels = {
    PENDING: '待处理',
    CONTACTED: '已联系',
    INVALID: '无效线索',
    RESOLVED: '已完成处理',
  };
  function timestamp(value) {
    const date = new Date(value);
    return value && !Number.isNaN(date.getTime())
      ? date.toISOString().slice(0, 16).replace('T', ' ') + ' UTC'
      : '暂未记录';
  }
  document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('homeward-receipt-page');
    if (!root) return;
    const form = root.querySelector('[data-homeward-receipt-form]');
    const input = form.querySelector('[name="receiptToken"]');
    const button = form.querySelector('[type="submit"]');
    const result = root.querySelector('[data-homeward-receipt-result]');
    const message = root.querySelector('[data-homeward-receipt-message]');
    let controller;
    let generation = 0;
    input.addEventListener('input', () => {
      generation++;
      controller?.abort();
      result.hidden = true;
      message.textContent = '';
      button.disabled = false;
    });
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const current = ++generation;
      controller?.abort();
      result.hidden = true;
      const receiptToken = input.value.trim();
      if (!/^[A-Za-z0-9_-]{43}$/.test(receiptToken)) {
        button.disabled = false;
        message.textContent = '请输入完整的 43 位查询凭证。';
        return;
      }
      const requestController = new AbortController();
      controller = requestController;
      const timeout = window.setTimeout(() => requestController.abort(), 12000);
      button.disabled = true;
      message.textContent = '正在查询处理进度…';
      try {
        const data = await fetchJson(`${API}/clue-receipts/lookup`, {
          method: 'POST',
          cache: 'no-store',
          signal: requestController.signal,
          body: JSON.stringify({ receiptToken }),
        });
        if (current !== generation) return;
        root.querySelector('[data-receipt-status]').textContent = labels[data.status] || '处理中';
        root.querySelector('[data-receipt-submitted]').textContent = timestamp(data.submittedAt);
        root.querySelector('[data-receipt-updated]').textContent = timestamp(data.updatedAt);
        root.querySelector('[data-receipt-public-message]').textContent =
          typeof data.message === 'string' ? data.message : '';
        result.hidden = false;
        message.textContent = '已更新处理进度。';
      } catch (error) {
        if (current !== generation) return;
        message.textContent =
          error.status === 404
            ? '查询凭证无效或已过期，请检查后重试。'
            : error.status === 429
              ? '查询太频繁，请稍后再试。'
              : error.name === 'AbortError'
                ? '查询超时，请稍后重试。'
                : error.message;
      } finally {
        window.clearTimeout(timeout);
        if (current === generation) button.disabled = false;
      }
    });
    // Clear credential/result state when leaving, including a page restored from the browser back-forward cache.
    window.addEventListener('pagehide', () => {
      generation++;
      controller?.abort();
      input.value = '';
      result.hidden = true;
      message.textContent = '';
      button.disabled = false;
    });
  });
})();
