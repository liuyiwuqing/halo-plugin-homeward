import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { JSDOM } from 'jsdom'
import { afterEach, describe, expect, it, vi } from 'vitest'

const base = resolve(process.cwd(), '../src/main/resources')
const windows: JSDOM[] = []
const flush = () => new Promise((resolve) => setTimeout(resolve, 0))
afterEach(() => windows.splice(0).forEach((dom) => dom.window.close()))
async function mount(enabled = true, failure = false) {
  const dom = new JSDOM(readFileSync(base + '/templates/homeward-application.html', 'utf8'), {
    url: 'https://fixture.invalid/homeward/apply',
    runScripts: 'outside-only',
  })
  windows.push(dom)
  const w = dom.window
  w.fetch = vi.fn(async (_url: string, options?: RequestInit) =>
    options?.method === 'POST'
      ? failure
        ? { ok: false, status: 429, text: async () => JSON.stringify({ detail: '申请提交太频繁' }) }
        : {
            ok: true,
            json: async () => ({ status: 'PENDING', message: '已提交，资料审核前不会公开' }),
          }
      : { ok: true, json: async () => ({ enabled }) },
  ) as unknown as typeof fetch
  w.eval(readFileSync(base + '/static/homeward/homeward-common.js', 'utf8'))
  w.eval(readFileSync(base + '/static/homeward/homeward-application.js', 'utf8'))
  await flush()
  const form = w.document.querySelector<HTMLFormElement>('form')!
  return { w, form, doc: w.document }
}
function fill(form: HTMLFormElement) {
  for (const name of ['personName', 'missingLocation', 'applicantName', 'relationship', 'contact'])
    (form.elements.namedItem(name) as HTMLInputElement).value = '测试资料'
  ;(form.elements.namedItem('description') as HTMLTextAreaElement).value =
    '这是隔离测试提供的足够详细申请情况，不是一份真实的寻亲资料。'
  ;(form.elements.namedItem('consent') as HTMLInputElement).checked = true
}
describe('public applications', () => {
  it('keeps the form hidden while service is closed', async () => {
    const { form, doc } = await mount(false)
    expect(form.hidden).toBe(true)
    expect(doc.querySelector('[data-application-availability]')?.textContent).toContain('暂未开放')
  })
  it('requires consent and submits to the private review queue without echoing sensitive values', async () => {
    const { w, form, doc } = await mount()
    fill(form)
    ;(form.elements.namedItem('consent') as HTMLInputElement).checked = false
    form.dispatchEvent(new w.Event('submit', { cancelable: true }))
    expect(w.fetch).toHaveBeenCalledTimes(1)
    ;(form.elements.namedItem('consent') as HTMLInputElement).checked = true
    form.dispatchEvent(new w.Event('submit', { cancelable: true }))
    await flush()
    expect(w.fetch).toHaveBeenLastCalledWith(
      expect.stringMatching(/\/applications$/),
      expect.objectContaining({ method: 'POST', body: expect.stringContaining('"consent":true') }),
    )
    expect(form.hidden).toBe(true)
    expect((form.elements.namedItem('contact') as HTMLInputElement).value).toBe('')
    expect(doc.querySelector('[data-application-availability]')?.textContent).toContain(
      '审核前不会公开',
    )
  })
  it('retains details after a rejected submission and allows retry', async () => {
    const { w, form, doc } = await mount(true, true)
    fill(form)
    form.dispatchEvent(new w.Event('submit', { cancelable: true }))
    await flush()
    expect(form.hidden).toBe(false)
    expect((form.elements.namedItem('contact') as HTMLInputElement).value).toBe('测试资料')
    expect(doc.querySelector('[data-application-message]')?.textContent).toBe('申请提交太频繁')
    expect(form.querySelector<HTMLButtonElement>('button')?.disabled).toBe(false)
  })
})
