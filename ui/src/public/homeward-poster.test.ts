import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { JSDOM } from 'jsdom'
import { afterEach, describe, expect, it, vi } from 'vitest'

const assetRoot = resolve(process.cwd(), '../src/main/resources/static/homeward')
const windows: JSDOM[] = []
const flush = () => new Promise((resolve) => setTimeout(resolve, 0))
afterEach(() => windows.splice(0).forEach((dom) => dom.window.close()))
const publicCase = {
  slug: 'case-a',
  title: '<script>测试档案标题</script>',
  personName: '公开姓名',
  gender: '男',
  missingDate: '2020-01-02',
  missingAge: 12,
  currentAge: 18,
  missingLocation: '北京市朝阳区',
  coverImage: 'https://photos.fixture.invalid/a.jpg',
  physicalFeatures: '公开特征描述',
  description: '公开详细说明',
  sourceName: '公开资料来源',
  updatedAt: '2026-10-11T04:30:00Z',
  contactName: '禁止导出的联络人',
  contactPhone: 'fixture-private-phone',
}

async function mount(
  options: {
    photoFails?: boolean
    api?: () => unknown
    canvasFails?: boolean
    taintedOnce?: boolean
  } = {},
) {
  // Canvas is instrumented to verify content/workflow only. PNG pixels must be verified separately in a real browser.
  const dom = new JSDOM(
    `<!doctype html><html><body><main id="homeward-case-content" data-case-slug="case-a" data-case-url="https://fixture.invalid/homeward/cases/case-a">
    <button data-homeward-poster-generate>生成分享海报</button>
    <p data-homeward-poster-message role="status"></p>
    <section data-homeward-poster-preview hidden><img data-homeward-poster-image alt="" /><a data-homeward-poster-download>下载 PNG 海报</a><button data-homeward-poster-close>关闭预览</button></section>
  </main></body></html>`,
    {
      url: 'https://fixture.invalid/homeward/cases/case-a?keyword=secret',
      runScripts: 'outside-only',
    },
  )
  windows.push(dom)
  const w = dom.window
  const texts: string[] = []
  const drawn: unknown[] = []
  const rectangles: { color: string; x: number; y: number; width: number; height: number }[] = []
  const images: { crossOrigin: string; src: string }[] = []
  const requests = vi.fn(async () => options.api?.() || { ok: true, json: async () => publicCase })
  w.fetch = requests as unknown as typeof w.fetch
  class FixtureImage {
    crossOrigin = ''
    naturalWidth = 640
    naturalHeight = 960
    onload: null | (() => void) = null
    onerror: null | (() => void) = null
    set src(value: string) {
      if (!value) return
      images.push({ crossOrigin: this.crossOrigin, src: value })
      Promise.resolve().then(() => (options.photoFails ? this.onerror?.() : this.onload?.()))
    }
  }
  w.Image = FixtureImage as unknown as typeof w.Image
  const context = {
    fillStyle: '',
    font: '',
    textBaseline: '',
    fillRect(x: number, y: number, width: number, height: number) {
      rectangles.push({ color: this.fillStyle, x, y, width, height })
    },
    measureText: (text: string) => ({ width: Array.from(text).length * 22 }),
    fillText: (text: string) => texts.push(text),
    drawImage: (...args: unknown[]) => drawn.push(args),
  }
  w.HTMLCanvasElement.prototype.getContext = vi.fn(() => context) as never
  let exports = 0
  w.HTMLCanvasElement.prototype.toBlob = function (callback) {
    if (options.taintedOnce && ++exports === 1)
      throw new w.DOMException('tainted canvas', 'SecurityError')
    callback(options.canvasFails ? null : new w.Blob(['fixture png'], { type: 'image/png' }))
  }
  const createObjectURL = vi.fn(() => 'blob:https://fixture.invalid/preview-' + Math.random())
  const revokeObjectURL = vi.fn()
  w.URL.createObjectURL = createObjectURL
  w.URL.revokeObjectURL = revokeObjectURL
  await flush()
  for (const file of ['homeward-common.js', 'vendor/qrcode.js', 'homeward-poster.js']) {
    if (existsSync(resolve(assetRoot, file))) w.eval(readFileSync(resolve(assetRoot, file), 'utf8'))
  }
  w.document.dispatchEvent(new w.Event('DOMContentLoaded'))
  await flush()
  return {
    w,
    doc: w.document,
    texts,
    drawn,
    images,
    rectangles,
    requests,
    createObjectURL,
    revokeObjectURL,
  }
}

async function generate(doc: Document) {
  doc.querySelector<HTMLButtonElement>('[data-homeward-poster-generate]')!.click()
  await flush()
  await flush()
}

describe('public poster workflow', () => {
  it('refreshes public data before local generation and previews a PNG using public fields only', async () => {
    const { doc, requests, texts, images, drawn } = await mount()
    await generate(doc)
    expect(requests).toHaveBeenCalledWith(
      '/apis/public.homeward.muyin.site/v1alpha1/cases/case-a',
      expect.objectContaining({ cache: 'no-store' }),
    )
    expect(doc.querySelector<HTMLElement>('[data-homeward-poster-preview]')!.hidden).toBe(false)
    expect(doc.querySelector<HTMLAnchorElement>('[data-homeward-poster-download]')!.href).toContain(
      'blob:',
    )
    expect(
      doc.querySelector<HTMLAnchorElement>('[data-homeward-poster-download]')!.download,
    ).toMatch(/\.png$/)
    expect(images).toEqual([{ crossOrigin: 'anonymous', src: publicCase.coverImage }])
    expect(drawn).toHaveLength(1)
    expect(texts.join(' ')).toContain('公开姓名')
    expect(texts.join(' ')).toContain('扫码核对最新状态')
    expect(texts.join(' ')).not.toContain('禁止导出')
    expect(texts.join(' ')).not.toContain('fixture-private-phone')
    expect(doc.querySelector('script')).toBeNull()
  })

  it('falls back to an explicitly labelled photo-free poster after CORS failure', async () => {
    const { doc, texts, drawn } = await mount({ photoFails: true })
    await generate(doc)
    expect(doc.querySelector('[data-homeward-poster-message]')!.textContent).toContain('无照片')
    expect(doc.querySelector<HTMLElement>('[data-homeward-poster-preview]')!.hidden).toBe(false)
    expect(texts.join(' ')).toContain('照片未载入')
    expect(drawn).toHaveLength(0)
  })

  it('invalidates the previous preview when refreshed case becomes unavailable', async () => {
    let available = true
    const { doc, revokeObjectURL } = await mount({
      api: () =>
        available
          ? { ok: true, json: async () => publicCase }
          : { ok: false, status: 404, text: async () => '{}' },
    })
    await generate(doc)
    available = false
    await generate(doc)
    expect(revokeObjectURL).toHaveBeenCalledTimes(1)
    expect(doc.querySelector<HTMLElement>('[data-homeward-poster-preview]')!.hidden).toBe(true)
    expect(doc.querySelector('[data-homeward-poster-message]')!.textContent).toContain(
      '已撤下或不存在',
    )
    expect(
      doc.querySelector<HTMLAnchorElement>('[data-homeward-poster-download]')!.hasAttribute('href'),
    ).toBe(false)
  })

  it('reports failed canvas export without creating a misleading download', async () => {
    const { doc, createObjectURL } = await mount({ canvasFails: true })
    await generate(doc)
    expect(createObjectURL).not.toHaveBeenCalled()
    expect(doc.querySelector('[data-homeward-poster-message]')!.textContent).toContain(
      '海报生成失败',
    )
    expect(doc.querySelector<HTMLElement>('[data-homeward-poster-preview]')!.hidden).toBe(true)
    expect(doc.querySelector<HTMLButtonElement>('[data-homeward-poster-generate]')!.disabled).toBe(
      false,
    )
  })
})

describe('poster rendering boundaries', () => {
  it('draws the actual QR matrix for the canonical link with a four-module white border', async () => {
    const { doc, w, rectangles } = await mount()
    await generate(doc)
    const expected = w.qrcode(0, 'M')
    expected.addData('https://fixture.invalid/homeward/cases/case-a', 'Byte')
    expected.make()
    const modules = expected.getModuleCount()
    const cell = Math.floor(280 / (modules + 8))
    const dark = rectangles.filter((rectangle) => rectangle.color === '#000')
    const white = rectangles.find((rectangle) => rectangle.color === '#fff')!
    const actual: { color: string; x: number; y: number; width: number; height: number }[] = []
    for (let row = 0; row < modules; row++)
      for (let col = 0; col < modules; col++) {
        if (expected.isDark(row, col))
          actual.push({
            color: '#000',
            x: white.x + (col + 4) * cell,
            y: white.y + (row + 4) * cell,
            width: cell,
            height: cell,
          })
      }
    expect(dark).toEqual(actual)
    expect(white.width).toBe((modules + 8) * cell)
  })

  it('redraws without a photo if the browser rejects canvas export as tainted', async () => {
    const { doc, texts, createObjectURL } = await mount({ taintedOnce: true })
    await generate(doc)
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(texts.join(' ')).toContain('照片未载入')
    expect(doc.querySelector('[data-homeward-poster-message]')!.textContent).toContain('无照片')
  })

  it('bounds long text and releases the preview URL when closed', async () => {
    const { doc, texts, revokeObjectURL } = await mount({
      api: () => ({
        ok: true,
        json: async () => ({
          ...publicCase,
          title: '测试长标题'.repeat(100),
          physicalFeatures: '描述'.repeat(2000),
        }),
      }),
    })
    await generate(doc)
    expect(texts.filter((value) => value.endsWith('…')).length).toBeGreaterThanOrEqual(2)
    expect(texts.join(' ').length).toBeLessThan(1000)
    doc.querySelector<HTMLButtonElement>('[data-homeward-poster-close]')!.click()
    expect(revokeObjectURL).toHaveBeenCalledTimes(1)
    expect(doc.querySelector<HTMLElement>('[data-homeward-poster-preview]')!.hidden).toBe(true)
  })
})
