(function () {
  const { API, fetchJson, escapeHtml, safeUrl, bindImageFallback, mountClues } = window.Homeward;
  document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('homeward-case-content');
    if (!root) return;
    bindImageFallback(root);
    const back = root.querySelector('[data-homeward-back]');
    const incoming = new URLSearchParams(window.location.search);
    const context = new URLSearchParams();
    const keyword = (incoming.get('keyword') || '').trim();
    const page = incoming.get('page') || '';
    if (keyword) context.set('keyword', keyword);
    if (/^\d+$/.test(page) && Number.isSafeInteger(Number(page)) && Number(page) > 1)
      context.set('page', page);
    if (back) back.href = '/homeward' + (context.size ? '?' + context : '');
    const url = safeUrl(root.dataset.caseUrl);
    const title = root.dataset.caseTitle;
    const copy = root.querySelector('[data-homeward-copy]');
    const share = root.querySelector('[data-homeward-share]');
    const message = root.querySelector('[data-homeward-share-message]');
    const manual = root.querySelector('[data-homeward-manual-copy]');
    function manualCopy() {
      manual.hidden = false;
      const input = manual.querySelector('input');
      input.value = url;
      input.focus();
      input.select();
      message.textContent = '请手动复制下方链接。';
    }
    const qrToggle = root.querySelector('[data-homeward-qr-toggle]');
    const qrRegion = root.querySelector('[data-homeward-qr]');
    function closeQr() {
      qrRegion.hidden = true;
      qrToggle.setAttribute('aria-expanded', 'false');
      qrToggle.textContent = '显示二维码';
      qrToggle.focus();
    }
    qrToggle.addEventListener('click', () => {
      if (!qrRegion.hidden) {
        closeQr();
        return;
      }
      try {
        if (!url || typeof window.qrcode !== 'function') throw new Error('QR encoder unavailable');
        const qr = window.qrcode(0, 'M');
        // URL serialization keeps Unicode domain/path bytes deterministic for scanner clients.
        qr.addData(new URL(url, window.location.origin).href, 'Byte');
        qr.make();
        const image = qrRegion.querySelector('[data-homeward-qr-image]');
        // The pinned local encoder generates only SVG geometry; no case text enters the markup.
        image.innerHTML = qr.createSvgTag({ cellSize: 4, margin: 16, scalable: true });
        const svg = image.querySelector('svg');
        svg.setAttribute('role', 'img');
        svg.setAttribute('aria-label', '这份寻亲档案的二维码');
        qrRegion.hidden = false;
        qrToggle.setAttribute('aria-expanded', 'true');
        qrToggle.textContent = '收起二维码';
        message.textContent = '二维码已显示，可扫码查看这份档案。';
      } catch {
        qrRegion.hidden = true;
        qrToggle.setAttribute('aria-expanded', 'false');
        manualCopy();
        message.textContent = '二维码生成失败，链接可能过长。请复制下方链接后分享。';
      }
    });
    root.querySelector('[data-homeward-qr-close]').addEventListener('click', closeQr);
    copy.addEventListener('click', async () => {
      if (!url) {
        message.textContent = '档案链接暂时不可用，请复制浏览器地址。';
        return;
      }
      try {
        if (!navigator.clipboard?.writeText) {
          manualCopy();
          return;
        }
        await navigator.clipboard.writeText(url);
        manual.hidden = true;
        message.textContent = '档案链接已复制。';
      } catch {
        manualCopy();
      }
    });
    if (typeof navigator.share === 'function') {
      share.hidden = false;
      share.addEventListener('click', async () => {
        if (!url) {
          manualCopy();
          return;
        }
        share.disabled = true;
        try {
          await navigator.share({
            title,
            text: '请留意这份寻亲档案，分享前核对来源与更新时间。',
            url,
          });
          message.textContent = '已完成系统分享。';
        } catch (error) {
          if (error.name === 'AbortError') message.textContent = '已取消分享。';
          else {
            manualCopy();
            message.textContent = '系统分享未完成，请复制链接后分享。';
          }
        } finally {
          share.disabled = false;
        }
      });
    }
    const entry = root.querySelector('[data-homeward-clue-entry]');
    fetchJson(`${API}/widget?surface=page`)
      .then((settings) => {
        if (!settings.enabled || !settings.clueSubmissionEnabled) {
          entry.innerHTML = '<p>线索提交暂未开放。</p>';
          return;
        }
        entry.innerHTML = `<button class="homeward-button homeward-button--clue" type="button" aria-haspopup="dialog" data-homeward-clue="${escapeHtml(root.dataset.caseSlug)}" data-homeward-title="${escapeHtml(title)}">我有相关线索 ↗</button>`;
        mountClues(root, settings);
      })
      .catch((error) => {
        entry.textContent = '暂时无法确认线索提交服务：' + error.message;
      });
  });
})();
