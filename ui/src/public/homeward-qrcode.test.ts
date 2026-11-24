import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { JSDOM } from 'jsdom'
import { afterEach, describe, expect, it } from 'vitest'

interface QrCode {
  addData(data: string, mode: 'Byte'): void
  make(): void
  getModuleCount(): number
  isDark(row: number, column: number): boolean
  createSvgTag(options: { cellSize: number; margin: number; scalable: boolean }): string
}

const windows: JSDOM[] = []
const vendorSource = readFileSync(
  resolve(process.cwd(), '../src/main/resources/static/homeward/vendor/qrcode.js'),
  'utf8',
)
const canonicalUrl = 'https://example.test/homeward/cases/person-a'

afterEach(() => windows.splice(0).forEach((dom) => dom.window.close()))

function encoder(url: string) {
  const dom = new JSDOM('<!doctype html><html><body></body></html>', {
    url: canonicalUrl,
    runScripts: 'outside-only',
  })
  windows.push(dom)
  dom.window.eval(vendorSource)
  const factory = dom.window.qrcode as (typeNumber: number, level: 'M') => QrCode
  const qr = factory(0, 'M')
  qr.addData(url, 'Byte')
  qr.make()
  return { qr, document: dom.window.document }
}

function matrix(qr: QrCode) {
  return Array.from({ length: qr.getModuleCount() }, (_, row) =>
    Array.from({ length: qr.getModuleCount() }, (_, column) =>
      qr.isDark(row, column) ? '1' : '0',
    ).join(''),
  ).join('\n')
}

describe('bundled QR encoder', () => {
  it('encodes a canonical case URL into a real QR matrix with three finder patterns', () => {
    const { qr } = encoder(canonicalUrl)
    const count = qr.getModuleCount()
    expect(count).toBeGreaterThanOrEqual(21)
    expect(count).toBeLessThanOrEqual(177)
    expect((count - 21) % 4).toBe(0)
    const finder = ['1111111', '1000001', '1011101', '1011101', '1011101', '1000001', '1111111']
    for (const [top, left] of [[0, 0], [0, count - 7], [count - 7, 0]]) {
      const actual = Array.from({ length: 7 }, (_, row) =>
        Array.from({ length: 7 }, (_, column) =>
          qr.isDark(top! + row, left! + column) ? '1' : '0',
        ).join(''),
      )
      expect(actual).toEqual(finder)
    }
  })

  it('renders a local SVG with a four-module quiet zone and no external image request', () => {
    const { qr, document } = encoder(canonicalUrl)
    document.body.innerHTML = qr.createSvgTag({ cellSize: 4, margin: 16, scalable: true })
    const svg = document.querySelector('svg')!
    const size = (qr.getModuleCount() + 8) * 4
    expect(svg.getAttribute('viewBox')).toBe(`0 0 ${size} ${size}`)
    expect(svg.querySelector('rect')?.getAttribute('fill')).toBe('white')
    expect(svg.querySelector('path')?.getAttribute('fill')).toBe('black')
    expect(svg.querySelector('path')?.getAttribute('d')).toContain('M16,16')
    expect(svg.querySelectorAll('script, image, foreignObject, [href], [src]')).toHaveLength(0)
  })

  it('encodes the supplied URL instead of returning a fixed placeholder matrix', () => {
    const first = encoder(canonicalUrl).qr
    const same = encoder(canonicalUrl).qr
    const second = encoder('https://example.test/homeward/cases/person-b').qr
    expect(matrix(first)).toBe(matrix(same))
    expect(matrix(first)).not.toBe(matrix(second))
  })
})
