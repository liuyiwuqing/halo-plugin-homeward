(function () {
  const { API, fetchJson, escapeHtml, safeUrl, placeholderImage, bindImageFallback, mountClues } =
    window.Homeward;

  function fact(label, value) {
    return `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value == null || value === '' ? '待补充' : value)}</dd></div>`;
  }
  function caseCard(item, showAction, index) {
    const title = item.title || `${item.personName || '姓名待确认'}的寻亲信息`;
    let href = '/homeward/cases/' + encodeURIComponent(item.slug);
    if (window.__HOMEWARD_PAGE__) {
      const state = readLocation();
      const context = new URLSearchParams();
      if (state.keyword) context.set('keyword', state.keyword);
      if (state.page > 1) context.set('page', String(state.page));
      if (context.size) href += '?' + context;
    }
    return `<article class="homeward-case-card${index === 0 ? ' homeward-case-card--featured' : ''}">
      <a class="homeward-case-card__media" href="${escapeHtml(href)}" aria-label="查看${escapeHtml(title)}详情">
        <img src="${escapeHtml(safeUrl(item.coverImage) || placeholderImage())}" alt="${escapeHtml(title)}的寻人照片" loading="lazy" />
      </a>
      <div class="homeward-case-card__body">
        <p class="homeward-case-card__eyebrow">寻人档案 · ${escapeHtml(item.missingDate || '日期待补充')}</p>
        <h3><a href="${escapeHtml(href)}">${escapeHtml(title)}</a></h3>
        <dl class="homeward-case-facts">${fact('失踪地点', item.missingLocation)}${fact('失踪时年龄', item.missingAge == null ? '' : item.missingAge + ' 岁')}${fact('当前年龄', item.currentAge == null ? '' : item.currentAge + ' 岁')}</dl>
        <p class="homeward-case-card__description">${escapeHtml(item.physicalFeatures || item.description || '请查看完整档案，留意照片与公开特征。')}</p>
        <a class="homeward-link homeward-detail-link" href="${escapeHtml(href)}">查看完整档案 →</a>
        ${showAction ? `<button class="homeward-button homeward-button--clue" type="button" aria-haspopup="dialog" data-homeward-clue="${escapeHtml(item.slug)}" data-homeward-title="${escapeHtml(title)}"><span>我有相关线索</span><span aria-hidden="true">↗</span></button>` : ''}
      </div></article>`;
  }
  function renderState(root, title, description) {
    root.setAttribute('aria-busy', 'false');
    root.innerHTML = `<div class="homeward-state"><h2>${escapeHtml(title)}</h2><p>${escapeHtml(description)}</p></div>`;
  }
  function readLocation() {
    const params = new URLSearchParams(window.location.search);
    const raw = params.get('page') || '1';
    const page =
      /^\d+$/.test(raw) && Number.isSafeInteger(Number(raw)) && Number(raw) > 0 ? Number(raw) : 1;
    return { keyword: (params.get('keyword') || '').trim(), page };
  }
  function saveLocation(state, replace) {
    const url = new URL(window.location.href);
    state.keyword
      ? url.searchParams.set('keyword', state.keyword)
      : url.searchParams.delete('keyword');
    state.page > 1
      ? url.searchParams.set('page', String(state.page))
      : url.searchParams.delete('page');
    window.history[replace ? 'replaceState' : 'pushState']({}, '', url);
  }

  async function mountPage(root) {
    let settings;
    try {
      settings = await fetchJson(`${API}/widget?surface=page`);
    } catch (error) {
      renderState(root, '档案暂时无法载入', error.message);
      return;
    }
    if (!settings.enabled) {
      renderState(root, '寻亲服务暂未开放', '请稍后再来。');
      return;
    }
    root.setAttribute('aria-busy', 'false');
    root.innerHTML = `<header class="homeward-header"><div class="homeward-header__copy"><p>公益寻亲档案</p><h2>${escapeHtml(settings.title || '正在寻找他们')}</h2><span>${escapeHtml(settings.subtitle || '请记住这些面孔，也请把他们带到更远的地方。')}</span></div></header>
      <form class="homeward-search" role="search" data-homeward-search><label for="homeward-keyword">搜索姓名或失踪地点</label><div><input id="homeward-keyword" name="keyword" type="search" maxlength="100" placeholder="例如：姓名、城市或街道" /><button class="homeward-button" type="submit">搜索</button><button class="homeward-button homeward-button--quiet" type="button" data-homeward-clear>清除</button></div></form>
      <div data-homeward-results aria-live="polite" aria-busy="true"></div>
      <aside class="homeward-share-note" aria-label="寻亲信息使用提示"><span class="homeward-share-note__mark" aria-hidden="true">归</span><div><strong>转发前，请保留完整信息</strong><p>请分享档案原始链接，核对来源和更新时间，避免传播已失效的信息。</p></div></aside>`;
    mountClues(root, settings);
    const results = root.querySelector('[data-homeward-results]');
    const input = root.querySelector('[name="keyword"]');
    const size = Math.max(1, Math.min(50, Number(settings.pageSize) || 20));
    let state = readLocation();
    let generation = 0;
    let controller;

    async function load() {
      const current = ++generation;
      controller?.abort();
      controller = new AbortController();
      input.value = state.keyword;
      results.setAttribute('aria-busy', 'true');
      const params = new URLSearchParams({ page: String(state.page), size: String(size) });
      if (state.keyword) params.set('keyword', state.keyword);
      try {
        const data = await fetchJson(`${API}/cases?${params}`, { signal: controller.signal });
        if (current !== generation) return;
        const total = Number(data.total) || 0;
        const pages = Math.max(1, Math.ceil(total / size));
        if (state.page > pages) {
          state.page = pages;
          saveLocation(state, true);
          await load();
          return;
        }
        const items = data.items || [];
        results.innerHTML = `<p class="homeward-result-count">${state.keyword ? '搜索结果' : '公开档案'}：<strong data-homeward-total>${total}</strong> 份</p>
          ${items.length ? `<div class="homeward-grid">${items.map((item, index) => caseCard(item, settings.clueSubmissionEnabled, index)).join('')}</div>` : `<div class="homeward-state"><h2>${state.keyword ? '没有找到匹配的档案' : '暂时没有公开档案'}</h2><p>${state.keyword ? '请尝试其他姓名或地点，或清除搜索条件。' : '新的寻亲信息经过整理后将在这里展示。'}</p></div>`}
          <nav class="homeward-pagination" aria-label="档案分页"><button type="button" data-homeward-prev ${state.page <= 1 ? 'disabled' : ''}>上一页</button><span>第 ${state.page} / ${pages} 页</span><button type="button" data-homeward-next ${state.page >= pages ? 'disabled' : ''}>下一页</button></nav>`;
        bindImageFallback(results);
      } catch (error) {
        if (current !== generation || error.name === 'AbortError') return;
        results.innerHTML = `<div class="homeward-state"><h2>档案暂时无法载入</h2><p>${escapeHtml(error.message)}</p><button type="button" data-homeward-retry>重新加载</button></div>`;
      } finally {
        if (current === generation) results.setAttribute('aria-busy', 'false');
      }
    }
    root.querySelector('[data-homeward-search]').addEventListener('submit', (event) => {
      event.preventDefault();
      state = { keyword: input.value.trim(), page: 1 };
      saveLocation(state);
      load();
    });
    root.querySelector('[data-homeward-clear]').addEventListener('click', () => {
      state = { keyword: '', page: 1 };
      saveLocation(state);
      load();
    });
    results.addEventListener('click', (event) => {
      const button = event.target.closest('button');
      if (!button || button.disabled) return;
      if (button.hasAttribute('data-homeward-retry')) {
        load();
        return;
      }
      if (button.hasAttribute('data-homeward-prev')) state.page--;
      else if (button.hasAttribute('data-homeward-next')) state.page++;
      else return;
      saveLocation(state);
      load();
    });
    window.addEventListener('popstate', () => {
      state = readLocation();
      load();
    });
    await load();
  }

  async function mount404(root) {
    try {
      const settings = await fetchJson(`${API}/widget`);
      if (!settings.enabled) {
        renderState(root, '寻亲服务暂未开放', '您可以返回博客首页。');
        return;
      }
      const cases = settings.cases || [];
      root.setAttribute('aria-busy', 'false');
      root.innerHTML = `<header class="homeward-header"><div class="homeward-header__copy"><p>公益寻亲</p><h2>${escapeHtml(settings.title || '正在寻找他们')}</h2><span>${escapeHtml(settings.subtitle || '')}</span></div><a class="homeward-link" href="/homeward">查看全部档案 →</a></header>
        ${cases.length ? `<div class="homeward-grid homeward-grid--compact">${cases.map((item, index) => caseCard(item, false, index)).join('')}</div>` : '<p>暂时没有公开档案。</p>'}`;
      bindImageFallback(root);
    } catch (error) {
      renderState(root, '档案暂时无法载入', error.message);
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('homeward-page-content');
    if (!root) return;
    if (window.__HOMEWARD_404__) mount404(root);
    else if (window.__HOMEWARD_PAGE__) mountPage(root);
  });
})();
