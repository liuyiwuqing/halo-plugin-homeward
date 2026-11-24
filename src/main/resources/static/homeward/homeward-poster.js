(function () {
  const { API, fetchJson, safeUrl } = window.Homeward;
  const WIDTH = 1080;
  const HEIGHT = 1600;
  const FONT = '"PingFang SC", "Microsoft YaHei", sans-serif';
  const INK = '#25211d';
  const MUTED = '#736b62';
  const ACCENT = '#783129';

  function text(value, fallback) {
    return (
      String(value == null ? '' : value)
        .replace(/\s+/g, ' ')
        .trim() ||
      fallback ||
      ''
    );
  }

  function dateText(value) {
    const date = new Date(value);
    return value && !Number.isNaN(date.getTime()) ? date.toISOString().slice(0, 10) : '待补充';
  }

  // Canvas text never interprets HTML. Cap input work and visibly shorten content to its allotted space.
  function lines(context, value, x, y, width, lineHeight, maxLines) {
    const characters = Array.from(text(value)).slice(0, 2000);
    const rows = [];
    let row = '';
    let truncated = false;
    for (let i = 0; i < characters.length; i++) {
      const next = row + characters[i];
      if (row && context.measureText(next).width > width) {
        rows.push(row);
        row = characters[i];
        if (rows.length === maxLines) {
          truncated = true;
          break;
        }
      } else row = next;
    }
    if (rows.length < maxLines && row) rows.push(row);
    if (truncated || text(value).length > 2000) {
      let last = rows[rows.length - 1] || '';
      while (last && context.measureText(last + '…').width > width)
        last = Array.from(last).slice(0, -1).join('');
      rows[rows.length - 1] = last + '…';
    }
    rows.forEach((line, index) => context.fillText(line, x, y + index * lineHeight));
  }

  function loadPhoto(value) {
    const url = safeUrl(value);
    if (!url) return Promise.resolve(null);
    return new Promise((resolve) => {
      const image = new Image();
      let done = false;
      const finish = (result) => {
        if (done) return;
        done = true;
        window.clearTimeout(timer);
        image.onload = null;
        image.onerror = null;
        if (!result) image.src = '';
        resolve(result);
      };
      const timer = window.setTimeout(() => finish(null), 8000);
      image.crossOrigin = 'anonymous';
      image.referrerPolicy = 'no-referrer';
      image.onload = () => finish(image.naturalWidth && image.naturalHeight ? image : null);
      image.onerror = () => finish(null);
      image.src = url;
    });
  }

  function makeCode(url) {
    if (typeof window.qrcode !== 'function') throw new Error('二维码组件未载入，请刷新后重试。');
    const qr = window.qrcode(0, 'M');
    try {
      qr.addData(new URL(url, window.location.origin).href, 'Byte');
      qr.make();
    } catch {
      throw new Error('档案链接过长，无法生成清晰二维码，请直接分享链接。');
    }
    if (Math.floor(280 / (qr.getModuleCount() + 8)) < 2)
      throw new Error('档案链接过长，无法生成清晰二维码，请直接分享链接。');
    return qr;
  }

  function drawPoster(data, photo, qr, generatedAt) {
    const canvas = document.createElement('canvas');
    canvas.width = WIDTH;
    canvas.height = HEIGHT;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('当前浏览器不支持海报绘制，请尝试其他浏览器。');
    const write = (value, x, y, width, size, maxLines = 1, color = INK, weight = '400') => {
      context.font = `${weight} ${size}px ${FONT}`;
      context.fillStyle = color;
      lines(context, value, x, y, width, Math.round(size * 1.4), maxLines);
    };
    context.textBaseline = 'alphabetic';
    context.fillStyle = '#f4efe7';
    context.fillRect(0, 0, WIDTH, HEIGHT);
    context.fillStyle = ACCENT;
    context.fillRect(72, 62, 8, 34);
    write('归航寻亲 · 公益档案', 100, 89, 850, 28, 1, ACCENT, '600');
    write(text(data.title, '寻找回家的路'), 72, 156, 936, 48, 2, INK, '600');

    context.fillStyle = '#e8e0d4';
    context.fillRect(72, 258, 396, 480);
    if (photo) {
      const ratio = Math.min(396 / photo.naturalWidth, 480 / photo.naturalHeight);
      const width = photo.naturalWidth * ratio;
      const height = photo.naturalHeight * ratio;
      context.drawImage(photo, 72 + (396 - width) / 2, 258 + (480 - height) / 2, width, height);
    } else {
      write(data.coverImage ? '照片未载入' : '暂无公开照片', 110, 479, 320, 32, 1, MUTED);
      write('请扫码查看原档案', 110, 527, 320, 24, 1, MUTED);
    }
    write(text(data.personName, '姓名待确认'), 512, 302, 496, 38, 2, INK, '600');
    const fact = (label, value, y, maxLines = 1) => {
      write(label, 512, y, 496, 24, 1, MUTED);
      write(value, 512, y + 42, 496, 30, maxLines, INK, '500');
    };
    fact('失踪日期', text(data.missingDate, '待补充'), 420);
    fact('失踪地点', text(data.missingLocation, '待补充'), 521, 2);
    fact(
      '失踪时 / 当前年龄',
      `${data.missingAge == null ? '待补充' : data.missingAge + ' 岁'} / ${data.currentAge == null ? '待补充' : data.currentAge + ' 岁'}`,
      663,
    );
    write('外貌特征与线索', 72, 812, 936, 28, 1, ACCENT, '600');
    write(
      text(data.physicalFeatures || data.description, '请留意照片与公开信息，扫码查看完整档案。'),
      72,
      867,
      936,
      32,
      3,
    );
    write('资料摘要；完整内容请扫码查看。', 72, 1018, 936, 22, 1, MUTED);
    write('信息来源：' + text(data.sourceName, '暂未注明'), 72, 1071, 936, 24, 2, MUTED);
    write('档案更新：' + dateText(data.updatedAt || data.publishedAt), 72, 1153, 936, 24, 1, MUTED);

    context.fillStyle = '#ddd3c5';
    context.fillRect(72, 1182, 936, 2);
    const modules = qr.getModuleCount();
    const cell = Math.floor(280 / (modules + 8));
    const box = (modules + 8) * cell;
    const left = 728 + (280 - box) / 2;
    const top = 1212;
    context.fillStyle = '#fff';
    context.fillRect(left, top, box, box);
    context.fillStyle = '#000';
    for (let row = 0; row < modules; row++) {
      for (let column = 0; column < modules; column++) {
        if (qr.isDark(row, column))
          context.fillRect(left + (column + 4) * cell, top + (row + 4) * cell, cell, cell);
      }
    }
    write('一条可靠的线索，', 72, 1246, 610, 32, 1, INK, '600');
    write('也许就是重逢的开始。', 72, 1297, 610, 32, 1, INK, '600');
    write('请通过档案页提交线索，', 72, 1361, 600, 25, 1, MUTED);
    write('扫码核对最新状态。', 72, 1404, 600, 25, 1, MUTED);
    write('扫码查看完整档案', 730, 1516, 300, 22, 1, ACCENT, '600');
    write('此海报为生成时快照，撤下或结案后请停止转发。', 72, 1475, 620, 22, 2, MUTED);
    write(
      '生成于 ' + generatedAt.toISOString().slice(0, 16).replace('T', ' ') + ' UTC',
      72,
      1558,
      936,
      20,
      1,
      MUTED,
    );
    return canvas;
  }

  function pngBlob(canvas) {
    return new Promise((resolve, reject) => {
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error('图片导出失败，请尝试其他浏览器。'))),
        'image/png',
      );
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('homeward-case-content');
    const generate = root?.querySelector('[data-homeward-poster-generate]');
    if (!generate) return;
    const preview = root.querySelector('[data-homeward-poster-preview]');
    const image = root.querySelector('[data-homeward-poster-image]');
    const download = root.querySelector('[data-homeward-poster-download]');
    const message = root.querySelector('[data-homeward-poster-message]');
    const slug = root.dataset.caseSlug;
    const canonical = safeUrl(root.dataset.caseUrl);
    let objectUrl = '';
    let running = false;
    function clearPreview() {
      preview.hidden = true;
      image.removeAttribute('src');
      download.removeAttribute('href');
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      objectUrl = '';
    }
    root.querySelector('[data-homeward-poster-close]').addEventListener('click', () => {
      clearPreview();
      generate.focus();
    });
    window.addEventListener('pagehide', clearPreview);
    generate.addEventListener('click', async () => {
      if (running) return;
      running = true;
      generate.disabled = true;
      generate.textContent = '正在生成海报…';
      clearPreview();
      message.textContent = '正在确认档案状态并载入照片…';
      const controller = new AbortController();
      const timeout = window.setTimeout(() => controller.abort(), 12000);
      try {
        if (!slug || !canonical) throw new Error('档案链接无效，请刷新页面。');
        // Always revalidate the public endpoint so a stale open page cannot export a withdrawn case.
        const data = await fetchJson(`${API}/cases/${encodeURIComponent(slug)}`, {
          cache: 'no-store',
          signal: controller.signal,
        });
        window.clearTimeout(timeout);
        if (data.slug !== slug) throw new Error('档案已更新，请刷新页面后重试。');
        const qr = makeCode(canonical);
        let photo = await loadPhoto(data.coverImage);
        let degraded = Boolean(data.coverImage && !photo);
        const generatedAt = new Date();
        let blob;
        try {
          blob = await pngBlob(drawPoster(data, photo, qr, generatedAt));
        } catch (error) {
          if (!photo || error.name !== 'SecurityError') throw error;
          photo = null;
          degraded = true;
          blob = await pngBlob(drawPoster(data, null, qr, generatedAt));
        }
        objectUrl = URL.createObjectURL(blob);
        image.src = objectUrl;
        image.alt = '寻亲分享海报：' + text(data.personName, data.title);
        download.href = objectUrl;
        download.download =
          'homeward-' + slug.replace(/[^a-zA-Z0-9_-]/g, '-').slice(0, 80) + '.png';
        preview.hidden = false;
        message.textContent = degraded
          ? '照片未能载入或不允许跨站导出，已生成无照片海报。请先预览，再下载或长按保存。'
          : '海报已生成，请先预览，再下载或长按图片保存。分享前请核对信息。';
      } catch (error) {
        clearPreview();
        message.textContent =
          '海报生成失败：' +
          (error.name === 'AbortError' ? '请求超时，请稍后重试。' : error.message);
      } finally {
        window.clearTimeout(timeout);
        running = false;
        generate.disabled = false;
        generate.textContent = '生成分享海报';
      }
    });
  });
})();
