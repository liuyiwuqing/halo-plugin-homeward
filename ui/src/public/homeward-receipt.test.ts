import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { JSDOM } from 'jsdom'
import { afterEach, describe, expect, it, vi } from 'vitest'

const assetRoot = resolve(process.cwd(), '../src/main/resources/static/homeward')
const token = 'Ab_12-xy'.repeat(5) + 'ABC'
const windows: JSDOM[] = []
const flush = () => new Promise((resolve) => setTimeout(resolve, 0))
afterEach(() => windows.splice(0).forEach((dom) => dom.window.close()))

async function mount(mode: 'clue' | 'lookup', response?: () => unknown) {
  const html =
    mode === 'clue'
      ? '<main id="root"><button data-homeward-clue="case-a">A</button><button data-homeward-clue="case-b">B</button></main>'
      : `<main id="homeward-receipt-page"><form data-homeward-receipt-form><label>凭证<input name="receiptToken" /></label><button type="submit">查询进度</button></form><p data-homeward-receipt-message></p><section data-homeward-receipt-result hidden><p data-receipt-status></p><time data-receipt-submitted></time><time data-receipt-updated></time><p data-receipt-public-message></p></section></main>`
  const dom = new JSDOM(html, {
    url: 'https://fixture.invalid/homeward/receipts',
    runScripts: 'outside-only',
  })
  windows.push(dom)
  const w = dom.window
  w.requestAnimationFrame = (callback) => {
    callback(0)
    return 1
  }
  const fetch = vi.fn(
    async (_input: string, _init?: RequestInit) =>
      response?.() || {
        ok: true,
        json: async () => ({ message: '线索已提交', receiptToken: token }),
      },
  )
  w.fetch = fetch as never
  const copy = vi.fn(async () => {})
  Object.defineProperty(w.navigator, 'clipboard', { value: { writeText: copy } })
  const downloads: Blob[] = []
  w.URL.createObjectURL = vi.fn((blob) => {
    downloads.push(blob)
    return 'blob:https://fixture.invalid/receipt'
  })
  w.URL.revokeObjectURL = vi.fn()
  w.HTMLAnchorElement.prototype.click = vi.fn()
  await flush()
  for (const file of [
    'homeward-common.js',
    mode === 'clue' ? 'homeward-clue.js' : 'homeward-receipt.js',
  ]) {
    if (existsSync(resolve(assetRoot, file))) w.eval(readFileSync(resolve(assetRoot, file), 'utf8'))
  }
  if (mode === 'clue')
    w.Homeward.mountClues(w.document.getElementById('root'), {
      clueSubmissionEnabled: true,
      minContentLength: 10,
    })
  else w.document.dispatchEvent(new w.Event('DOMContentLoaded'))
  await flush()
  return { w, doc: w.document, fetch, copy, downloads }
}

async function submitClue(w: JSDOM['window']) {
  const form = w.document.querySelector<HTMLFormElement>('#homeward-clue-form')!
  form.querySelector<HTMLInputElement>('[name="contact"]')!.value = 'fixture-contact'
  form.querySelector<HTMLTextAreaElement>('[name="content"]')!.value =
    '这是一条足够详细的测试线索信息'
  form.dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }))
  await flush()
}

describe('private receipt delivery', () => {
  it('copies/downloads a successful token without putting it in URL or storage', async () => {
    const { w, doc, copy, downloads, fetch } = await mount('clue')
    doc.querySelector<HTMLButtonElement>('[data-homeward-clue="case-a"]')!.click()
    await submitClue(w)
    expect(doc.querySelector<HTMLInputElement>('[data-homeward-receipt-token]')!.value).toBe(token)
    doc.querySelector<HTMLButtonElement>('[data-homeward-receipt-copy]')!.click()
    await flush()
    expect(copy).toHaveBeenCalledWith(token)
    doc.querySelector<HTMLButtonElement>('[data-homeward-receipt-download]')!.click()
    expect(downloads).toHaveLength(1)
    expect(doc.querySelector<HTMLAnchorElement>('[data-homeward-receipt-lookup]')!.href).toBe(
      'https://fixture.invalid/homeward/receipts',
    )
    expect(w.location.href).not.toContain(token)
    expect(w.localStorage.length).toBe(0)
    expect(w.sessionStorage.length).toBe(0)
    expect(JSON.parse(String(fetch.mock.calls[0]?.[1]?.body))).not.toHaveProperty('receiptToken')
  })

  it('keeps successive receipts in the matching case and hides them for other cases', async () => {
    const { w, doc } = await mount('clue')
    doc.querySelector<HTMLButtonElement>('[data-homeward-clue="case-a"]')!.click()
    await submitClue(w)
    await submitClue(w)
    expect(doc.querySelectorAll('[data-homeward-receipt-token]')).toHaveLength(2)
    doc.querySelector<HTMLButtonElement>('.homeward-dialog__close')!.click()
    doc.querySelector<HTMLButtonElement>('[data-homeward-clue="case-b"]')!.click()
    expect(doc.querySelectorAll('[data-homeward-receipt-token]')).toHaveLength(0)
    doc.querySelector<HTMLButtonElement>('.homeward-dialog__close')!.click()
    doc.querySelector<HTMLButtonElement>('[data-homeward-clue="case-a"]')!.click()
    expect(doc.querySelectorAll('[data-homeward-receipt-token]')).toHaveLength(2)
  })
})

describe('public receipt lookup', () => {
  it('POSTs the token and renders only status/timestamps/public message', async () => {
    const { w, doc, fetch } = await mount('lookup', () => ({
      ok: true,
      json: async () => ({
        status: 'CONTACTED',
        submittedAt: '2026-10-11T04:00:00Z',
        updatedAt: '2026-10-11T05:00:00Z',
        message: '已进入核验流程',
        contact: 'must-not-appear',
        handler: 'private-admin',
      }),
    }))
    doc.querySelector<HTMLInputElement>('[name="receiptToken"]')!.value = token
    doc.querySelector('form')!.dispatchEvent(new w.Event('submit', { cancelable: true }))
    await flush()
    expect(fetch).toHaveBeenCalledWith(
      '/apis/public.homeward.muyin.site/v1alpha1/clue-receipts/lookup',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ receiptToken: token }),
        cache: 'no-store',
      }),
    )
    expect(doc.querySelector<HTMLElement>('[data-homeward-receipt-result]')!.hidden).toBe(false)
    expect(doc.body.textContent).toContain('已联系')
    expect(doc.body.textContent).not.toContain('must-not-appear')
    expect(doc.body.textContent).not.toContain('private-admin')
    expect(w.location.href).not.toContain(token)
  })

  it('rejects malformed credentials without requesting the API', async () => {
    const { w, doc, fetch } = await mount('lookup')
    doc.querySelector<HTMLInputElement>('[name="receiptToken"]')!.value = 'short'
    doc.querySelector('form')!.dispatchEvent(new w.Event('submit', { cancelable: true }))
    await flush()
    expect(fetch).not.toHaveBeenCalled()
    expect(doc.querySelector('[data-homeward-receipt-message]')!.textContent).toContain('43')
  })

  it.each([404, 429])('uses a safe, actionable message for HTTP %i', async (status) => {
    const { w, doc } = await mount('lookup', () => ({
      ok: false,
      status,
      text: async () =>
        JSON.stringify({
          detail: status === 404 ? 'internal receipt identifier' : '查询太频繁，请稍后再试',
        }),
    }))
    doc.querySelector<HTMLInputElement>('[name="receiptToken"]')!.value = token
    doc.querySelector('form')!.dispatchEvent(new w.Event('submit', { cancelable: true }))
    await flush()
    const text = doc.querySelector('[data-homeward-receipt-message]')!.textContent
    expect(text).toContain(status === 404 ? '无效或已过期' : '查询太频繁')
    expect(text).not.toContain('internal')
    expect(doc.querySelector<HTMLElement>('[data-homeward-receipt-result]')!.hidden).toBe(true)
  })
})

describe('receipt lifetime and request isolation', () => {
  it('clears delivered credentials and drafts on pagehide', async () => {
    const { w, doc } = await mount('clue')
    doc.querySelector<HTMLButtonElement>('[data-homeward-clue="case-a"]')!.click()
    await submitClue(w)
    w.dispatchEvent(new w.PageTransitionEvent('pagehide'))
    expect(doc.querySelectorAll('[data-homeward-receipt-token]')).toHaveLength(0)
    doc.querySelector<HTMLButtonElement>('[data-homeward-clue="case-a"]')!.click()
    expect(doc.querySelectorAll('[data-homeward-receipt-token]')).toHaveLength(0)
    expect(doc.querySelector<HTMLTextAreaElement>('[name="content"]')!.value).toBe('')
  })

  it('ignores an obsolete response after the credential is edited', async () => {
    let resolveResponse!: (value: unknown) => void
    const pending = new Promise((resolve) => {
      resolveResponse = resolve
    })
    const { w, doc } = await mount('lookup', () => pending)
    const input = doc.querySelector<HTMLInputElement>('[name="receiptToken"]')!
    input.value = token
    doc.querySelector('form')!.dispatchEvent(new w.Event('submit', { cancelable: true }))
    input.value = 'different'
    input.dispatchEvent(new w.Event('input', { bubbles: true }))
    resolveResponse({ ok: true, json: async () => ({ status: 'RESOLVED', message: 'obsolete' }) })
    await flush()
    expect(doc.querySelector<HTMLElement>('[data-homeward-receipt-result]')!.hidden).toBe(true)
    expect(doc.body.textContent).not.toContain('obsolete')
  })
})
