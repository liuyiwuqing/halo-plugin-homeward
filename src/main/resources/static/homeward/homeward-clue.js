(function () {
  const { API, fetchJson, escapeHtml } = window.Homeward;

  function mountClues(container, settings) {
    if (!settings.clueSubmissionEnabled) return;
    const minimum = Math.max(1, Number(settings.minContentLength) || 10);
    const host = document.createElement('div');
    host.innerHTML = `
      <div class="homeward-dialog" data-homeward-dialog hidden>
        <button class="homeward-dialog__backdrop" type="button" aria-label="关闭线索提交窗口" data-homeward-dialog-close></button>
        <section class="homeward-dialog__panel" role="dialog" aria-modal="true" aria-labelledby="homeward-dialog-title">
          <button class="homeward-dialog__close" type="button" aria-label="关闭" data-homeward-dialog-close>×</button>
          <div class="homeward-dialog__intro">
            <p class="homeward-dialog__eyebrow">提交线索</p>
            <h2 id="homeward-dialog-title">提供关于<br /><span data-homeward-case-title></span><br />的线索</h2>
            <p>${escapeHtml(settings.contactText || '提交内容仅用于线索核验，不会在公开页面展示。')}</p>
            <div class="homeward-dialog__privacy"><strong>隐私提示</strong><span>请勿填写与寻亲无关的身份证号、银行卡号等敏感信息。</span></div>
          </div>
          <form id="homeward-clue-form" class="homeward-form" data-case-slug="">
            <div class="homeward-form__row">
              <label>您的姓名 <span>选填</span><input name="submitterName" autocomplete="name" maxlength="80" /></label>
              <label>联系方式 <span>必填</span><input name="contact" autocomplete="tel" required maxlength="120" /></label>
            </div>
            <label>线索位置 <span>选填</span><input name="location" maxlength="200" placeholder="例如：城市、街道或地标" /></label>
            <label>线索内容 <span>至少 ${minimum} 字</span><textarea name="content" rows="6" required minlength="${minimum}" maxlength="3000" placeholder="请说明发现时间、人物特征及其他有帮助的信息"></textarea></label>
            <label class="homeward-honeypot" aria-hidden="true">网站<input name="website" tabindex="-1" autocomplete="off" /></label>
            <div class="homeward-form__footer">
              <p class="homeward-form__message" data-homeward-message role="status" aria-live="polite"></p>
              <button class="homeward-button homeward-button--submit" type="submit"><span data-homeward-submit-label>确认提交</span><span aria-hidden="true">→</span></button>
            </div>
            <section class="homeward-receipt-delivery" data-homeward-receipt-delivery hidden aria-label="线索查询凭证">
              <h3>请保存查询凭证</h3>
              <p>凭证仅在本次页面停留期间保留。请复制或下载保存；持有凭证的人可以查询处理进度，请勿公开分享。</p>
              <div data-homeward-receipt-list></div>
              <a class="homeward-link" href="/homeward/receipts" target="_blank" rel="noopener noreferrer" data-homeward-receipt-lookup>打开进度查询页面 ↗</a>
              <p data-homeward-receipt-action-message role="status" aria-live="polite"></p>
            </section>
          </form>
        </section>
      </div>`;
    container.appendChild(host);
    const dialog = host.querySelector('[data-homeward-dialog]');
    const form = host.querySelector('form');
    const fields = Array.from(form.querySelectorAll('input[name], textarea[name]'));
    const message = form.querySelector('[data-homeward-message]');
    const submit = form.querySelector('[type="submit"]');
    // Drafts live only in memory and are keyed by case, never persisted in shared browser storage.
    const drafts = new Map();
    let activeSlug = '';
    let trigger = null;
    let closeTimer;

    function stateFor(slug) {
      if (!drafts.has(slug))
        drafts.set(slug, { values: {}, pending: false, message: '', status: '', receipts: [] });
      return drafts.get(slug);
    }
    function remember() {
      if (!activeSlug) return;
      const state = stateFor(activeSlug);
      state.values = Object.fromEntries(fields.map((field) => [field.name, field.value]));
    }
    function show(state) {
      fields.forEach((field) => {
        field.value = state.values[field.name] || '';
        field.disabled = state.pending;
      });
      submit.disabled = state.pending;
      form.querySelector('[data-homeward-submit-label]').textContent = state.pending
        ? '正在提交…'
        : '确认提交';
      message.textContent = state.message;
      message.dataset.state = state.status;
      const delivery = form.querySelector('[data-homeward-receipt-delivery]');
      delivery.hidden = !state.receipts.length;
      form.querySelector('[data-homeward-receipt-action-message]').textContent = '';
      form.querySelector('[data-homeward-receipt-list]').innerHTML = state.receipts
        .map(
          (token, index) => `
        <article class="homeward-receipt-token-card">
          <label>查询凭证 ${index + 1}<input type="text" readonly autocomplete="off" spellcheck="false" value="${escapeHtml(token)}" data-homeward-receipt-token /></label>
          <div class="homeward-receipt-token-actions"><button type="button" data-homeward-receipt-copy="${index}">复制凭证</button><button type="button" data-homeward-receipt-download="${index}">下载凭证</button></div>
        </article>`,
        )
        .join('');
    }
    form.addEventListener('click', async (event) => {
      const copy = event.target.closest('[data-homeward-receipt-copy]');
      const download = event.target.closest('[data-homeward-receipt-download]');
      if ((!copy && !download) || !activeSlug) return;
      const slug = activeSlug;
      const button = copy || download;
      const index = Number(
        copy ? copy.dataset.homewardReceiptCopy : download.dataset.homewardReceiptDownload,
      );
      const token = stateFor(slug).receipts[index];
      if (!token) return;
      const feedback = form.querySelector('[data-homeward-receipt-action-message]');
      if (copy) {
        try {
          if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
          await navigator.clipboard.writeText(token);
          if (activeSlug === slug) feedback.textContent = '查询凭证已复制，请妥善保存。';
        } catch {
          if (activeSlug !== slug) return;
          const input = button.closest('article').querySelector('input');
          input.focus();
          input.select();
          feedback.textContent = '请手动复制已选中的查询凭证。';
        }
      } else {
        try {
          const content =
            '归航寻亲线索查询凭证\n\n凭证：' +
            token +
            '\n查询页面：' +
            new URL('/homeward/receipts', window.location.origin).href +
            '\n\n凭证用于查询处理状态，请妥善保存，勿公开分享。\n';
          const objectUrl = URL.createObjectURL(
            new Blob([content], { type: 'text/plain;charset=utf-8' }),
          );
          const anchor = document.createElement('a');
          anchor.href = objectUrl;
          anchor.download = 'homeward-receipt.txt';
          document.body.appendChild(anchor);
          anchor.click();
          anchor.remove();
          window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
          feedback.textContent = '已发起凭证下载，请检查下载文件并妥善保存。';
        } catch {
          feedback.textContent = '无法下载凭证，请使用复制凭证保存。';
        }
      }
    });
    function close() {
      remember();
      activeSlug = '';
      dialog.classList.remove('is-open');
      document.documentElement.classList.remove('homeward-dialog-active');
      closeTimer = window.setTimeout(() => {
        dialog.hidden = true;
      }, 240);
      if (trigger?.isConnected) trigger.focus();
    }
    container.addEventListener('click', (event) => {
      const button = event.target.closest('[data-homeward-clue]');
      if (!button || !container.contains(button)) return;
      remember();
      window.clearTimeout(closeTimer);
      trigger = button;
      activeSlug = button.dataset.homewardClue;
      form.dataset.caseSlug = activeSlug;
      host.querySelector('[data-homeward-case-title]').textContent =
        button.dataset.homewardTitle || '该寻亲档案';
      show(stateFor(activeSlug));
      dialog.hidden = false;
      document.documentElement.classList.add('homeward-dialog-active');
      window.requestAnimationFrame(() => {
        dialog.classList.add('is-open');
        host.querySelector('.homeward-dialog__close').focus();
      });
    });
    host
      .querySelectorAll('[data-homeward-dialog-close]')
      .forEach((button) => button.addEventListener('click', close));
    dialog.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        close();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = Array.from(
        host
          .querySelector('[role="dialog"]')
          .querySelectorAll(
            'button:not([disabled]), input:not([disabled]):not([tabindex="-1"]), textarea:not([disabled]), a[href]',
          ),
      ).filter((element) => !element.closest('[hidden]'));
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    });
    window.addEventListener('pagehide', () => {
      activeSlug = '';
      drafts.clear();
      form.reset();
      form.querySelector('[data-homeward-receipt-list]').innerHTML = '';
      form.querySelector('[data-homeward-receipt-delivery]').hidden = true;
      dialog.hidden = true;
      dialog.classList.remove('is-open');
      document.documentElement.classList.remove('homeward-dialog-active');
    });
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const slug = activeSlug;
      if (!slug) return;
      const state = stateFor(slug);
      if (state.pending) return;
      remember();
      const body = { ...state.values };
      const invalid = !body.contact?.trim()
        ? '请留下联系方式。'
        : (body.content || '').trim().length < minimum
          ? `请补充线索细节，至少填写 ${minimum} 字。`
          : '';
      if (invalid) {
        state.message = invalid;
        state.status = 'error';
        show(state);
        return;
      }
      state.pending = true;
      state.message = '';
      state.status = '';
      show(state);
      try {
        const result = await fetchJson(`${API}/cases/${encodeURIComponent(slug)}/clues`, {
          method: 'POST',
          body: JSON.stringify(body),
        });
        state.values = {};
        state.message = result.message || '线索已提交，感谢您的帮助。';
        state.status = 'success';
        if (
          typeof result.receiptToken === 'string' &&
          /^[A-Za-z0-9_-]{43}$/.test(result.receiptToken)
        )
          state.receipts.push(result.receiptToken);
      } catch (error) {
        state.message = error.message;
        state.status = 'error';
      } finally {
        state.pending = false;
        // A request only updates its own case; switching or closing never retargets its response.
        if (activeSlug === slug) show(state);
      }
    });
  }
  window.Homeward.mountClues = mountClues;
})();
