import { readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { JSDOM } from 'jsdom'
import { afterEach, describe, expect, it, vi } from 'vitest'

const assetRoot = resolve(process.cwd(), '../src/main/resources/static/homeward') + '/'
const windows: JSDOM[] = []
const cases = [
  { slug: 'case-a', title: '测试案例 A', personName: '测试 A' },
  { slug: 'case-b', title: '测试案例 B', personName: '测试 B' },
]
const widget = {
  enabled: true,
  cases,
  clueSubmissionEnabled: true,
  minContentLength: 10,
  pageSize: 2,
}
const ok = (data: unknown) => ({ ok: true, status: 200, json: async () => data })
const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

afterEach(() => windows.splice(0).forEach((dom) => dom.window.close()))

// All requests are explicit in-memory fixtures; these are DOM behavior checks, not Halo/browser acceptance.
async function mount(
  options: {
    settings?: object
    request?: (url: string, init?: RequestInit) => unknown
    url?: string
    mode?: 'page' | '404'
  } = {},
) {
  const dom = new JSDOM(
    '<!doctype html><html><body><div id="homeward-page-content"></div></body></html>',
    {
      url: options.url || 'https://fixture.invalid/homeward',
      runScripts: 'outside-only',
    },
  )
  windows.push(dom)
  const w = dom.window
  w.__HOMEWARD_PAGE__ = options.mode !== '404'
  w.__HOMEWARD_404__ = options.mode === '404'
  w.requestAnimationFrame = (callback) => {
    callback(0)
    return 1
  }
  w.fetch = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input)
    const custom = options.request?.(url, init)
    if (custom !== undefined) return (await custom) as Response
    if (url.includes('/widget')) return ok({ ...widget, ...options.settings }) as Response
    if (url.includes('/cases?')) return ok({ page: 1, size: 2, total: 3, items: cases }) as Response
    throw new Error('Unexpected fixture request: ' + url)
  })
  await new Promise((resolve) => setTimeout(resolve, 0))
  for (const file of ['homeward-common.js', 'homeward-clue.js', 'homeward-public.js']) {
    if (existsSync(assetRoot + file)) w.eval(readFileSync(assetRoot + file, 'utf8'))
  }
  w.document.dispatchEvent(new w.Event('DOMContentLoaded'))
  await flush()
  return {
    w,
    doc: w.document,
    form: () => w.document.querySelector<HTMLFormElement>('#homeward-clue-form')!,
  }
}

function open(doc: Document, slug: string) {
  doc.querySelector<HTMLButtonElement>(`[data-homeward-clue="${slug}"]`)!.click()
}
function fill(form: HTMLFormElement, content: string) {
  form.querySelector<HTMLInputElement>('[name="contact"]')!.value = 'fixture-contact'
  form.querySelector<HTMLTextAreaElement>('[name="content"]')!.value = content
}

describe('public case browsing and clue submission', () => {
  it('uses public list totals and offers independent case detail links', async () => {
    const { doc, w } = await mount()
    expect(w.fetch).toHaveBeenCalledWith(expect.stringContaining('/cases?'), expect.anything())
    expect(doc.querySelector('[data-homeward-total]')?.textContent).toBe('3')
    expect(doc.querySelector('a[href="/homeward/cases/case-a"]')).not.toBeNull()
    expect(doc.querySelector('[data-homeward-next]')).not.toBeNull()
  })

  it('separates case drafts when switching A to B and back', async () => {
    const { doc, form } = await mount()
    open(doc, 'case-a')
    fill(form(), '测试：仅为案例 A 填写的线索内容')
    doc.querySelector<HTMLButtonElement>('.homeward-dialog__close')!.click()
    open(doc, 'case-b')
    expect(form().querySelector<HTMLTextAreaElement>('[name="content"]')!.value).toBe('')
    fill(form(), '测试：仅为案例 B 填写的线索内容')
    doc.querySelector<HTMLButtonElement>('.homeward-dialog__close')!.click()
    open(doc, 'case-a')
    expect(form().querySelector<HTMLTextAreaElement>('[name="content"]')!.value).toContain('案例 A')
  })

  it('shows actionable 429 reason and retains failed input', async () => {
    const { w, doc, form } = await mount({
      request: (url) =>
        url.endsWith('/clues')
          ? {
              ok: false,
              status: 429,
              text: async () => JSON.stringify({ detail: '提交太频繁，请稍后再试' }),
            }
          : undefined,
    })
    open(doc, 'case-a')
    fill(form(), '测试：足够详细且提交失败应保留的内容')
    form().dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }))
    await flush()
    expect(doc.querySelector('[data-homeward-message]')!.textContent).toBe('提交太频繁，请稍后再试')
    expect(form().querySelector<HTMLTextAreaElement>('[name="content"]')!.value).toContain('应保留')
  })

  it('does not let A pending response clear B draft or show A result', async () => {
    let resolveSubmission!: (value: unknown) => void
    const pending = new Promise((resolve) => {
      resolveSubmission = resolve
    })
    const { w, doc, form } = await mount({
      request: (url) => (url.endsWith('/clues') ? pending : undefined),
    })
    open(doc, 'case-a')
    fill(form(), '测试：案例 A 正在提交中的内容')
    form().dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }))
    await flush()
    doc.querySelector<HTMLButtonElement>('.homeward-dialog__close')!.click()
    open(doc, 'case-b')
    fill(form(), '测试：案例 B 新填写的内容不要清空')
    resolveSubmission(ok({ message: 'A 提交成功' }))
    await flush()
    expect(form().querySelector<HTMLTextAreaElement>('[name="content"]')!.value).toContain('案例 B')
    expect(doc.querySelector('[data-homeward-message]')!.textContent).not.toContain('A 提交成功')
    expect(form().querySelector<HTMLButtonElement>('[type="submit"]')!.disabled).toBe(false)
  })

  it('hides clue entry when disabled and validates configured minimum length before POST', async () => {
    const disabled = await mount({ settings: { clueSubmissionEnabled: false } })
    expect(disabled.doc.querySelector('[data-homeward-clue]')).toBeNull()
    const { w, doc, form } = await mount({ settings: { minContentLength: 20 } })
    open(doc, 'case-a')
    fill(form(), '只有几个字')
    form().dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }))
    await flush()
    expect(doc.querySelector('[data-homeward-message]')!.textContent).toContain('20')
    expect(vi.mocked(w.fetch).mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })

  it('404 uses compact widget and never the full list endpoint', async () => {
    const { w, doc } = await mount({ mode: '404' })
    expect(vi.mocked(w.fetch).mock.calls.map(([url]) => String(url))).toEqual([
      '/apis/public.homeward.muyin.site/v1alpha1/widget',
    ])
    expect(doc.querySelector('#homeward-page-content a[href="/homeward"]')).not.toBeNull()
  })
})

describe('public navigation and request ordering', () => {
  it('restores keyword/page from URL, searches from page one, and follows popstate', async () => {
    const { w, doc } = await mount({
      url: 'https://fixture.invalid/homeward?keyword=%E5%8C%97%E4%BA%AC&page=2',
      request: (url) => {
        if (!url.includes('/cases?')) return undefined
        const params = new URL(url, 'https://fixture.invalid').searchParams
        return ok({ page: Number(params.get('page')), size: 2, total: 6, items: cases })
      },
    })
    expect(doc.querySelector<HTMLInputElement>('[name="keyword"]')!.value).toBe('北京')
    expect(doc.querySelector('.homeward-pagination')!.textContent).toContain('第 2 / 3 页')
    const detail = doc.querySelector<HTMLAnchorElement>('h3 a')!
    expect(new URL(detail.href).searchParams.get('keyword')).toBe('北京')
    expect(new URL(detail.href).searchParams.get('page')).toBe('2')
    const input = doc.querySelector<HTMLInputElement>('[name="keyword"]')!
    input.value = '  小安  '
    doc
      .querySelector('form[data-homeward-search]')!
      .dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }))
    await flush()
    expect(new URL(w.location.href).searchParams.get('keyword')).toBe('小安')
    expect(new URL(w.location.href).searchParams.get('page')).toBeNull()
    doc.querySelector<HTMLButtonElement>('[data-homeward-next]')!.click()
    await flush()
    expect(new URL(w.location.href).searchParams.get('page')).toBe('2')
    w.history.replaceState({}, '', '/homeward?keyword=%E5%8C%97%E4%BA%AC&page=3')
    w.dispatchEvent(new w.PopStateEvent('popstate'))
    await flush()
    expect(input.value).toBe('北京')
    expect(doc.querySelector('.homeward-pagination')!.textContent).toContain('第 3 / 3 页')
  })

  it('ignores a slower obsolete search response even if fixture fetch ignores abort', async () => {
    let resolveOld!: (value: unknown) => void
    const old = new Promise((resolve) => {
      resolveOld = resolve
    })
    const { w, doc } = await mount({
      request: (url) => {
        if (url.includes('keyword=old')) return old
        if (url.includes('keyword=new'))
          return ok({ page: 1, size: 2, total: 1, items: [{ slug: 'new', title: '新结果' }] })
        return undefined
      },
    })
    const input = doc.querySelector<HTMLInputElement>('[name="keyword"]')!
    const form = doc.querySelector('[data-homeward-search]')!
    input.value = 'old'
    form.dispatchEvent(new w.Event('submit', { cancelable: true }))
    await flush()
    input.value = 'new'
    form.dispatchEvent(new w.Event('submit', { cancelable: true }))
    await flush()
    resolveOld(ok({ page: 1, size: 2, total: 99, items: [{ slug: 'old', title: '过期结果' }] }))
    await flush()
    expect(doc.querySelector('[data-homeward-results]')!.textContent).toContain('新结果')
    expect(doc.querySelector('[data-homeward-results]')!.textContent).not.toContain('过期结果')
    expect(doc.querySelector('[data-homeward-total]')!.textContent).toBe('1')
  })

  it('keeps filters available after a list failure and retries into an empty result', async () => {
    let attempts = 0
    const { doc } = await mount({
      request: (url) => {
        if (!url.includes('/cases?')) return undefined
        return ++attempts === 1
          ? { ok: false, status: 503, text: async () => '{}' }
          : ok({ page: 1, size: 2, total: 0, items: [] })
      },
    })
    expect(doc.querySelector('[data-homeward-search]')).not.toBeNull()
    doc.querySelector<HTMLButtonElement>('[data-homeward-retry]')!.click()
    await flush()
    expect(doc.querySelector('[data-homeward-results]')!.textContent).toContain('暂时没有公开档案')
    expect(doc.querySelector('[data-homeward-total]')!.textContent).toBe('0')
  })
})
