(function () {
  const API = '/apis/public.homeward.muyin.site/v1alpha1';

  async function fetchJson(url, options) {
    let response;
    try {
      response = await fetch(url, { headers: { 'Content-Type': 'application/json' }, ...options });
    } catch (error) {
      if (error.name === 'AbortError') throw error;
      throw new Error('网络连接失败，请检查网络后重试。');
    }
    if (!response.ok) {
      const messages = {
        400: '填写内容不符合要求，请检查后重试。',
        404: '这份档案已撤下或不存在，请返回档案列表。',
        410: '这份寻亲信息已结案，请停止传播旧资料。',
        429: '提交太频繁，请稍后再试。',
        503: '服务暂未开放，请稍后再试。',
      };
      let message = messages[response.status] || '服务暂时无法响应，请稍后重试。';
      try {
        const body = JSON.parse(await response.text());
        if (typeof body.detail === 'string' && body.detail.trim() && body.detail.length <= 500)
          message = body.detail;
      } catch {
        /* Non-JSON proxy errors use the status-specific fallback. */
      }
      const error = new Error(message);
      error.status = response.status;
      throw error;
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

  function safeUrl(value) {
    const url = String(value || '').trim();
    if (/[\\\u0000-\u0020]/.test(url)) return '';
    if (/^\/(?!\/)/.test(url)) return url;
    try {
      const parsed = new URL(url);
      return ['http:', 'https:'].includes(parsed.protocol) && !parsed.username && !parsed.password
        ? parsed.href
        : '';
    } catch {
      return '';
    }
  }

  function placeholderImage() {
    return (
      'data:image/svg+xml;utf8,' +
      encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" width="480" height="600"><rect width="480" height="600" fill="#e8e1d5"/><circle cx="240" cy="220" r="75" fill="#c9b9a6"/><path d="M110 500c20-110 65-170 130-170s110 60 130 170" fill="#c9b9a6"/></svg>',
      )
    );
  }

  function bindImageFallback(root) {
    root.querySelectorAll('img').forEach((image) =>
      image.addEventListener(
        'error',
        () => {
          image.src = placeholderImage();
          image.alt = '照片暂时无法载入';
        },
        { once: true },
      ),
    );
  }

  window.Homeward = { API, fetchJson, escapeHtml, safeUrl, placeholderImage, bindImageFallback };
})();
