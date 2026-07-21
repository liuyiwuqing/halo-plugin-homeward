(function () {
  const API = '/apis/public.homeward.muyin.site/v1alpha1';

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
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function caseImage(item) {
    return item.coverImage || 'data:image/svg+xml;utf8,' + encodeURIComponent(`
      <svg xmlns="http://www.w3.org/2000/svg" width="800" height="600">
        <rect width="800" height="600" fill="#dbeafe"/>
        <circle cx="400" cy="260" r="90" fill="#f59e0b"/>
        <path d="M260 430c80-70 200-70 280 0" stroke="#2563eb" stroke-width="32" stroke-linecap="round" fill="none"/>
      </svg>
    `);
  }

  function card(item, showAction) {
    const title = item.title || `${item.personName || '无名'}的寻亲信息`;
    return `
      <article class="homeward-case-card">
        <img src="${escapeHtml(caseImage(item))}" alt="${escapeHtml(title)}" loading="lazy" />
        <div class="homeward-case-card__body">
          <h3>${escapeHtml(title)}</h3>
          <p class="homeward-case-meta">${escapeHtml(item.missingLocation || '失踪地点待补充')}</p>
          <p class="homeward-case-meta">${escapeHtml(item.physicalFeatures || item.description || '请帮助扩散这条寻亲信息。')}</p>
          ${showAction ? `<button class="homeward-button homeward-button--ghost" data-homeward-clue="${escapeHtml(item.slug)}" data-homeward-title="${escapeHtml(title)}">我有线索</button>` : ''}
        </div>
      </article>
    `;
  }

  function renderCases(container, widget, mode) {
    if (!widget.enabled) {
      container.innerHTML = '<div class="homeward-empty">归航寻亲暂未启用。</div>';
      return;
    }
    const cases = widget.cases || [];
    if (!cases.length) {
      container.innerHTML = '<div class="homeward-empty">暂未发布寻亲案例。</div>';
      return;
    }
    container.innerHTML = `
      <div class="homeward-header">
        <div>
          <h2>${escapeHtml(widget.title || '归航寻亲')}</h2>
          <p>${escapeHtml(widget.subtitle || '每一次迷路，都值得被照亮。')}</p>
        </div>
        ${mode === '404' ? '<a class="homeward-link" href="/homeward">查看全部</a>' : ''}
      </div>
      <div class="homeward-grid">${cases.map((item) => card(item, mode !== '404')).join('')}</div>
      ${mode !== '404' ? clueForm(widget.contactText) : ''}
    `;
    container.querySelectorAll('[data-homeward-clue]').forEach((button) => {
      button.addEventListener('click', () => {
        const form = document.getElementById('homeward-clue-form');
        form.dataset.caseSlug = button.dataset.homewardClue || '';
        form.querySelector('[data-homeward-case-title]').textContent = button.dataset.homewardTitle || '';
        form.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    });
    const form = document.getElementById('homeward-clue-form');
    if (form) {
      form.addEventListener('submit', submitClue);
    }
  }

  function clueForm(contactText) {
    return `
      <form id="homeward-clue-form" class="homeward-form" data-case-slug="">
        <h3>提交线索 <span data-homeward-case-title></span></h3>
        <p>${escapeHtml(contactText || '如有线索，请通过表单提交，站长会尽快核验处理。')}</p>
        <label>姓名<input name="submitterName" autocomplete="name" /></label>
        <label>联系方式<input name="contact" autocomplete="tel" required /></label>
        <label>线索位置<input name="location" /></label>
        <label>线索内容<textarea name="content" rows="5" required></textarea></label>
        <label class="homeward-honeypot">网站<input name="website" tabindex="-1" autocomplete="off" /></label>
        <button class="homeward-button" type="submit">提交线索</button>
        <p data-homeward-message></p>
      </form>
    `;
  }

  async function submitClue(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const message = form.querySelector('[data-homeward-message]');
    const slug = form.dataset.caseSlug;
    if (!slug) {
      message.textContent = '请先选择一个寻亲案例。';
      return;
    }
    const body = Object.fromEntries(new FormData(form).entries());
    try {
      const result = await fetchJson(`${API}/cases/${encodeURIComponent(slug)}/clues`, {
        method: 'POST',
        body: JSON.stringify(body),
      });
      message.textContent = result.message || '线索已提交。';
      form.reset();
    } catch (error) {
      message.textContent = '提交失败，请稍后再试。';
    }
  }

  async function mount404() {
    if (!window.__HOMEWARD_404__) {
      return;
    }
    const root = document.createElement('section');
    root.className = 'homeward-404';
    const footer = document.querySelector('footer');
    if (footer && footer.parentNode) {
      footer.parentNode.insertBefore(root, footer);
    } else {
      document.body.appendChild(root);
    }
    const widget = await fetchJson(`${API}/widget?surface=page`);
    renderCases(root, widget, '404');
  }

  async function mountPage() {
    if (!window.__HOMEWARD_PAGE__) {
      return;
    }
    const root = document.getElementById('homeward-page-content');
    if (!root) {
      return;
    }
    const widget = await fetchJson(`${API}/widget`);
    renderCases(root, widget, 'page');
  }

  document.addEventListener('DOMContentLoaded', () => {
    mount404().catch(() => {});
    mountPage().catch(() => {});
  });
})();
