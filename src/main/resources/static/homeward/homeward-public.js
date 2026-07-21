(function () {
  const API = '/apis/public.homeward.muyin.site/v1alpha1';
  const DIALOG_TRANSITION_MS = 240;
  let lastDialogTrigger = null;

  async function fetchJson(url, options) {
    const response = await fetch(url, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || '请求失败');
    }
    return response.json();
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function placeholderImage() {
    return 'data:image/svg+xml;utf8,' + encodeURIComponent(`
      <svg xmlns="http://www.w3.org/2000/svg" width="960" height="1200" viewBox="0 0 960 1200">
        <rect width="960" height="1200" fill="#e8e1d5"/>
        <path d="M0 920C212 812 375 806 530 896s272 85 430-4v308H0Z" fill="#d8ccbd"/>
        <circle cx="480" cy="438" r="142" fill="#c9b9a6"/>
        <path d="M250 935c45-195 138-292 230-292s185 97 230 292" fill="#c9b9a6"/>
        <path d="M724 142v242M603 263h242" stroke="#b44736" stroke-width="18" stroke-linecap="round" opacity=".7"/>
      </svg>
    `);
  }

  function caseImage(item) {
    const value = String(item.coverImage || '').trim();
    if (/^(https?:\/\/|\/|\.\/|data:image\/)/i.test(value)) {
      return value;
    }
    return placeholderImage();
  }

  function fact(label, value) {
    const content = value == null || value === '' ? '待补充' : value;
    return `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(content)}</dd></div>`;
  }

  function caseCard(item, showAction, index) {
    const personName = item.personName || '姓名待确认';
    const title = item.title || `${personName}的寻亲信息`;
    const missingAge = item.missingAge == null ? '' : `${item.missingAge} 岁`;
    const currentAge = item.currentAge == null ? '' : `${item.currentAge} 岁`;
    const description = item.physicalFeatures || item.description || '请留意照片与公开特征，帮助这条信息被更多人看见。';
    return `
      <article class="homeward-case-card${index === 0 ? ' homeward-case-card--featured' : ''}">
        <div class="homeward-case-card__media">
          <img src="${escapeHtml(caseImage(item))}" alt="${escapeHtml(title)}的寻人照片" loading="lazy" />
          <span class="homeward-case-card__index" aria-hidden="true">${String(index + 1).padStart(2, '0')}</span>
        </div>
        <div class="homeward-case-card__body">
          <p class="homeward-case-card__eyebrow">寻人档案 · ${escapeHtml(item.missingDate || '日期待补充')}</p>
          <h3>${escapeHtml(title)}</h3>
          <dl class="homeward-case-facts">
            ${fact('失踪地点', item.missingLocation)}
            ${fact('失踪时年龄', missingAge)}
            ${fact('当前年龄', currentAge)}
          </dl>
          <p class="homeward-case-card__description">${escapeHtml(description)}</p>
          ${showAction ? `
            <button
              class="homeward-button homeward-button--clue"
              type="button"
              aria-haspopup="dialog"
              data-homeward-clue="${escapeHtml(item.slug)}"
              data-homeward-title="${escapeHtml(title)}"
            >
              <span>我有相关线索</span><span aria-hidden="true">↗</span>
            </button>
          ` : ''}
        </div>
      </article>
    `;
  }

  function clueDialog(contactText) {
    return `
      <div class="homeward-dialog" data-homeward-dialog hidden>
        <button class="homeward-dialog__backdrop" type="button" aria-label="关闭线索提交窗口" data-homeward-dialog-close></button>
        <section class="homeward-dialog__panel" role="dialog" aria-modal="true" aria-labelledby="homeward-dialog-title">
          <button class="homeward-dialog__close" type="button" aria-label="关闭" data-homeward-dialog-close>
            <span aria-hidden="true">×</span>
          </button>
          <div class="homeward-dialog__intro">
            <p class="homeward-dialog__eyebrow">CLUE REPORT / 提交线索</p>
            <h2 id="homeward-dialog-title">提供关于<br /><span data-homeward-case-title></span><br />的线索</h2>
            <p>${escapeHtml(contactText || '请尽量提供真实、具体的信息。提交内容仅用于线索核验，不会在公开页面展示。')}</p>
            <div class="homeward-dialog__privacy">
              <strong>隐私提示</strong>
              <span>请勿填写与寻亲无关的身份证号、银行卡号等敏感信息。</span>
            </div>
          </div>
          <form id="homeward-clue-form" class="homeward-form" data-case-slug="">
            <div class="homeward-form__row">
              <label>您的姓名 <span>选填</span><input name="submitterName" autocomplete="name" /></label>
              <label>联系方式 <span>必填</span><input name="contact" autocomplete="tel" required /></label>
            </div>
            <label>线索位置 <span>选填</span><input name="location" placeholder="例如：城市、街道或地标" /></label>
            <label>线索内容 <span>必填</span><textarea name="content" rows="6" required placeholder="请说明发现时间、人物特征及其他有帮助的信息"></textarea></label>
            <label class="homeward-honeypot" aria-hidden="true">网站<input name="website" tabindex="-1" autocomplete="off" /></label>
            <div class="homeward-form__footer">
              <p class="homeward-form__message" data-homeward-message role="status" aria-live="polite"></p>
              <button class="homeward-button homeward-button--submit" type="submit">
                <span data-homeward-submit-label>确认提交</span><span aria-hidden="true">→</span>
              </button>
            </div>
          </form>
        </section>
      </div>
    `;
  }

  function renderState(container, eyebrow, title, description) {
    container.setAttribute('aria-busy', 'false');
    container.innerHTML = `
      <div class="homeward-state">
        <p>${escapeHtml(eyebrow)}</p>
        <h2>${escapeHtml(title)}</h2>
        <span>${escapeHtml(description)}</span>
      </div>
    `;
  }

  function renderCases(container, widget, mode) {
    if (!widget.enabled) {
      renderState(container, 'NOTICE / 公告', '寻亲服务暂未开放', '管理员启用服务后，公开档案将在这里展示。');
      return;
    }
    const cases = widget.cases || [];
    if (!cases.length) {
      renderState(container, 'ARCHIVE / 档案', '暂时没有公开档案', '新的寻亲信息经过整理和核验后，会第一时间展示在这里。');
      return;
    }

    const pageMode = mode !== '404';
    container.setAttribute('aria-busy', 'false');
    container.innerHTML = `
      <header class="homeward-header">
        <div class="homeward-header__copy">
          <p>PUBLIC NOTICE / 公益寻亲</p>
          <h2>${escapeHtml(widget.title || '正在寻找他们')}</h2>
          <span>${escapeHtml(widget.subtitle || '请记住这些面孔，也请把他们带到更远的地方。')}</span>
        </div>
        <div class="homeward-header__meta">
          <strong>${String(cases.length).padStart(2, '0')}</strong>
          <span>份公开档案</span>
          ${pageMode ? '' : '<a class="homeward-link" href="/homeward">查看全部档案 <span aria-hidden="true">→</span></a>'}
        </div>
      </header>
      <div class="homeward-grid${pageMode ? '' : ' homeward-grid--compact'}">
        ${cases.map((item, index) => caseCard(item, pageMode, index)).join('')}
      </div>
      ${pageMode ? `
        <aside class="homeward-share-note" aria-label="��亲信息使用提示">
          <span class="homeward-share-note__mark" aria-hidden="true">归</span>
          <div><strong>转发前，请保留完整信息</strong><p>请勿裁剪照片、篡改联系方式或传播未经核实的内容。可靠，比热闹更重要。</p></div>
        </aside>
        ${clueDialog(widget.contactText)}
      ` : ''}
    `;

    if (pageMode) {
      bindClueDialog(container);
    }
  }

  function bindClueDialog(container) {
    const dialog = container.querySelector('[data-homeward-dialog]');
    const form = container.querySelector('#homeward-clue-form');
    if (!dialog || !form) {
      return;
    }

    container.querySelectorAll('[data-homeward-clue]').forEach((button) => {
      button.addEventListener('click', () => openDialog(dialog, form, button));
    });
    dialog.querySelectorAll('[data-homeward-dialog-close]').forEach((button) => {
      button.addEventListener('click', () => closeDialog(dialog));
    });
    dialog.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        closeDialog(dialog);
        return;
      }
      if (event.key === 'Tab') {
        keepFocusInsideDialog(event, dialog);
      }
    });
    form.addEventListener('submit', submitClue);
  }

  function openDialog(dialog, form, trigger) {
    lastDialogTrigger = trigger;
    form.dataset.caseSlug = trigger.dataset.homewardClue || '';
    dialog.querySelector('[data-homeward-case-title]').textContent = trigger.dataset.homewardTitle || '该寻亲档案';
    dialog.hidden = false;
    document.documentElement.classList.add('homeward-dialog-active');
    window.requestAnimationFrame(() => {
      dialog.classList.add('is-open');
      dialog.querySelector('.homeward-dialog__close').focus();
    });
  }

  function closeDialog(dialog) {
    dialog.classList.remove('is-open');
    document.documentElement.classList.remove('homeward-dialog-active');
    window.setTimeout(() => {
      dialog.hidden = true;
    }, DIALOG_TRANSITION_MS);
    if (lastDialogTrigger) {
      lastDialogTrigger.focus();
      lastDialogTrigger = null;
    }
  }

  function keepFocusInsideDialog(event, dialog) {
    const panel = dialog.querySelector('[role="dialog"]');
    const focusable = panel
      ? Array.from(panel.querySelectorAll('button:not([disabled]), input:not([disabled]), textarea:not([disabled]), a[href]'))
      : [];
    if (!focusable.length) {
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  async function submitClue(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const message = form.querySelector('[data-homeward-message]');
    const submitButton = form.querySelector('[type="submit"]');
    const submitLabel = form.querySelector('[data-homeward-submit-label]');
    const slug = form.dataset.caseSlug;
    if (!slug) {
      message.dataset.state = 'error';
      message.textContent = '请先选择一份寻亲档案。';
      return;
    }

    submitButton.disabled = true;
    submitLabel.textContent = '正在提交…';
    message.textContent = '';
    delete message.dataset.state;
    try {
      const body = Object.fromEntries(new FormData(form).entries());
      const result = await fetchJson(`${API}/cases/${encodeURIComponent(slug)}/clues`, {
        method: 'POST',
        body: JSON.stringify(body),
      });
      message.dataset.state = 'success';
      message.textContent = result.message || '线索已提交，感谢您的帮助。';
      form.reset();
    } catch {
      message.dataset.state = 'error';
      message.textContent = '提交失败，请检查网络后再试。';
    } finally {
      submitButton.disabled = false;
      submitLabel.textContent = '确认提交';
    }
  }

  async function mount404() {
    if (!window.__HOMEWARD_404__) {
      return;
    }
    const root = document.createElement('section');
    root.className = 'homeward-404';
    root.setAttribute('aria-live', 'polite');
    const footer = document.querySelector('footer');
    if (footer && footer.parentNode) {
      footer.parentNode.insertBefore(root, footer);
    } else {
      document.body.appendChild(root);
    }
    try {
      const widget = await fetchJson(`${API}/widget`);
      renderCases(root, widget, '404');
    } catch {
      root.remove();
    }
  }

  async function mountPage() {
    if (!window.__HOMEWARD_PAGE__) {
      return;
    }
    const root = document.getElementById('homeward-page-content');
    if (!root) {
      return;
    }
    try {
      const widget = await fetchJson(`${API}/widget?surface=page`);
      renderCases(root, widget, 'page');
    } catch {
      renderState(root, 'CONNECTION / 连接', '档案暂时无法载入', '请稍后刷新页面。如果问题持续存在，请联系网站管理员。');
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    mount404();
    mountPage();
  });
})();
