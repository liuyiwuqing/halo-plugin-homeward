import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { JSDOM } from 'jsdom'
import { afterEach, describe, expect, it, vi } from 'vitest'

const assetRoot = resolve(process.cwd(), '../src/main/resources/static/homeward')
const windows: JSDOM[] = []
const flush = () => new Promise((resolve) => setTimeout(resolve, 0))
afterEach(() => windows.splice(0).forEach((dom) => dom.window.close()))

async function mount(
  options: {
    copy?: () => Promise<void>
    share?: () => Promise<void>
    search?: string
    canonical?: string
  } = {},
) {
  // The SSR template itself is validated by Java/Thymeleaf tests; this fixture exercises only DOM behavior.
  const dom = new JSDOM(
    `<!doctype html><html><body>
    <main id="homeward-case-content" data-case-url="https://fixture.invalid/homeward/cases/case-a" data-case-title="测试案例 A" data-case-slug="case-a">
      <a href="/homeward" data-homeward-back>全部档案</a><div data-homeward-clue-entry></div><button data-homeward-copy>复制档案链接</button><button data-homeward-share hidden>分享</button>
      <button data-homeward-qr-toggle aria-expanded="false">显示二维码</button><section id="homeward-qrcode" data-homeward-qr hidden><div data-homeward-qr-image></div><button data-homeward-qr-close>关闭二维码</button></section><p data-homeward-share-message></p><label data-homeward-manual-copy hidden><input readonly /></label>
    </main></body></html>`,
    {
      url: 'https://fixture.invalid/homeward/cases/case-a' + (options.search || ''),
      runScripts: 'outside-only',
    },
  )
  windows.push(dom)
  const w = dom.window
  if (options.canonical)
    w.document.querySelector<HTMLElement>('#homeward-case-content')!.dataset.caseUrl =
      options.canonical
  w.fetch = vi.fn(
    async () =>
      ({
        ok: true,
        json: async () => ({ enabled: true, clueSubmissionEnabled: false }),
      }) as Response,
  )
  if (options.copy)
    Object.defineProperty(w.navigator, 'clipboard', { value: { writeText: options.copy } })
  if (options.share) Object.defineProperty(w.navigator, 'share', { value: options.share })
  await new Promise((resolve) => setTimeout(resolve, 0))
  for (const file of [
    'homeward-common.js',
    'homeward-clue.js',
    'vendor/qrcode.js',
    'homeward-detail.js',
  ])
    w.eval(readFileSync(resolve(assetRoot, file), 'utf8'))
  w.document.dispatchEvent(new w.Event('DOMContentLoaded'))
  await flush()
  return { w, doc: w.document }
}

describe('case detail link sharing', () => {
  it('copies the canonical URL and only reports success after clipboard resolves', async () => {
    const copy = vi.fn(async () => {})
    const { doc } = await mount({ copy })
    doc.querySelector<HTMLButtonElement>('[data-homeward-copy]')!.click()
    await flush()
    expect(copy).toHaveBeenCalledWith('https://fixture.invalid/homeward/cases/case-a')
    expect(doc.querySelector('[data-homeward-share-message]')!.textContent).toBe('档案链接已复制。')
  })

  it('shows a selectable manual URL when clipboard is unavailable or denied', async () => {
    const { doc } = await mount({
      copy: async () => {
        throw new Error('permission denied')
      },
    })
    doc.querySelector<HTMLButtonElement>('[data-homeward-copy]')!.click()
    await flush()
    expect(doc.querySelector<HTMLElement>('[data-homeward-manual-copy]')!.hidden).toBe(false)
    expect(doc.querySelector<HTMLInputElement>('[data-homeward-manual-copy] input')!.value).toBe(
      'https://fixture.invalid/homeward/cases/case-a',
    )
    expect(doc.querySelector('[data-homeward-share-message]')!.textContent).not.toContain('已复制')
  })

  it('treats user cancellation as cancellation, and keeps submission disabled', async () => {
    const { doc } = await mount({
      share: async () => {
        throw Object.assign(new Error('cancel'), { name: 'AbortError' })
      },
    })
    const button = doc.querySelector<HTMLButtonElement>('[data-homeward-share]')!
    expect(button.hidden).toBe(false)
    button.click()
    await flush()
    expect(doc.querySelector('[data-homeward-share-message]')!.textContent).toBe('已取消分享。')
    expect(button.disabled).toBe(false)
    expect(doc.querySelector('[data-homeward-clue-entry]')!.textContent).toContain('暂未开放')
    expect(doc.querySelector('[data-homeward-clue]')).toBeNull()
  })
})

describe('case context and public URL boundaries', () => {
  it('keeps only search/page in the back link and preserves canonical sharing', async () => {
    const copy = vi.fn(async () => {})
    const { doc } = await mount({
      copy,
      search: '?keyword=%E5%8C%97%E4%BA%AC&page=3&redirect=https://untrusted.invalid',
    })
    expect(doc.querySelector<HTMLAnchorElement>('[data-homeward-back]')!.href).toBe(
      'https://fixture.invalid/homeward?keyword=%E5%8C%97%E4%BA%AC&page=3',
    )
    doc.querySelector<HTMLButtonElement>('[data-homeward-copy]')!.click()
    await flush()
    expect(copy).toHaveBeenCalledWith('https://fixture.invalid/homeward/cases/case-a')
  })

  it('rejects credentials, protocol-relative, unsafe schemes and URL parser backslash normalization', async () => {
    const { w } = await mount()
    const safeUrl = w.Homeward.safeUrl
    expect(safeUrl('https://user:pass@example.org/image.jpg')).toBe('')
    expect(safeUrl('//untrusted.invalid/image.jpg')).toBe('')
    expect(safeUrl('javascript:alert(1)')).toBe('')
    expect(safeUrl('https:' + String.fromCharCode(92, 92) + 'untrusted.invalid')).toBe('')
    expect(safeUrl('https://example.org/' + String.fromCharCode(10) + 'image.jpg')).toBe('')
    expect(safeUrl('/upload/image.jpg')).toBe('/upload/image.jpg')
    expect(safeUrl('https://example.org/image.jpg')).toBe('https://example.org/image.jpg')
  })
})

describe('real QR encoder integration', () => {
  it('generates a local QR from canonical URL, ignores search context, and can close it', async () => {
    const { w, doc } = await mount({ search: '?keyword=secret&page=3' })
    const button = doc.querySelector<HTMLButtonElement>('[data-homeward-qr-toggle]')!
    button.click()
    const region = doc.querySelector<HTMLElement>('[data-homeward-qr]')!
    expect(region.hidden).toBe(false)
    expect(button.getAttribute('aria-expanded')).toBe('true')
    const svg = region.querySelector('svg')!
    expect(svg).not.toBeNull()
    expect(svg.getAttribute('role')).toBe('img')
    expect(svg.getAttribute('aria-label')).toBe('这份寻亲档案的二维码')
    const expected = w.qrcode(0, 'M')
    expected.addData('https://fixture.invalid/homeward/cases/case-a', 'Byte')
    expected.make()
    const reference = doc.createElement('div')
    reference.innerHTML = expected.createSvgTag({ cellSize: 4, margin: 16, scalable: true })
    expect(svg.querySelector('path')!.getAttribute('d')).toBe(
      reference.querySelector('path')!.getAttribute('d'),
    )
    expect(vi.mocked(w.fetch).mock.calls).toHaveLength(1)
    doc.querySelector<HTMLButtonElement>('[data-homeward-qr-close]')!.click()
    expect(region.hidden).toBe(true)
    expect(button.getAttribute('aria-expanded')).toBe('false')
  })

  it('reports actual encoder capacity failures and keeps manual copy available', async () => {
    const canonical = 'https://fixture.invalid/homeward/cases/' + 'a'.repeat(5000)
    const { doc } = await mount({ canonical })
    doc.querySelector<HTMLButtonElement>('[data-homeward-qr-toggle]')!.click()
    expect(doc.querySelector<HTMLElement>('[data-homeward-qr]')!.hidden).toBe(true)
    expect(doc.querySelector('[data-homeward-share-message]')!.textContent).toContain(
      '二维码生成失败',
    )
    expect(doc.querySelector<HTMLElement>('[data-homeward-manual-copy]')!.hidden).toBe(false)
    expect(doc.querySelector<HTMLInputElement>('[data-homeward-manual-copy] input')!.value).toBe(
      canonical,
    )
  })
})
