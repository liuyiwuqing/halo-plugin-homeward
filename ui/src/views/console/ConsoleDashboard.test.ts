import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ConsoleDashboard from './ConsoleDashboard.vue'
import CaseEditorModal from '@/components/CaseEditorModal.vue'

vi.mock('@halo-dev/components', () => {
  const passthrough = (tag: string) =>
    defineComponent({
      inheritAttrs: false,
      setup(_, { attrs, slots }) {
        return () => h(tag, attrs, slots.default?.())
      },
    })

  return {
    VButton: passthrough('button'),
    VEmpty: passthrough('div'),
    VLoading: passthrough('div'),
    VPagination: defineComponent({
      props: { page: { default: 1 }, size: { default: 30 }, total: { default: 0 } },
      emits: ['change'],
      setup(props, { emit }) {
        return () =>
          h('div', [
            h('span', `共 ${props.total} 项数据`),
            h(
              'button',
              {
                class: 'pagination__btn--next',
                disabled: props.page >= Math.ceil(props.total / props.size),
                onClick: () => emit('change', { page: props.page + 1, size: props.size }),
              },
              '下一页',
            ),
            h('select', { class: 'pagination__select', value: props.page }),
            h(
              'select',
              {
                class: 'pagination__select',
                value: props.size,
                onChange: (event: Event) =>
                  emit('change', {
                    page: 1,
                    size: Number((event.target as HTMLSelectElement).value),
                  }),
              },
              [10, 30, 50].map((size) => h('option', { value: size }, size)),
            ),
          ])
      },
    }),
    VModal: defineComponent({
      inheritAttrs: false,
      props: ['title', 'visible'],
      setup(props, { attrs, slots }) {
        return () =>
          props.visible === false
            ? null
            : h('div', { ...attrs, 'data-modal-title': props.title }, [
                slots.default?.(),
                slots.footer?.(),
              ])
      },
    }),
    VTabbar: passthrough('div'),
    VTag: passthrough('span'),
  }
})

vi.mock('@halo-dev/ui-shared', () => ({
  utils: {
    date: {
      format: vi.fn(() => '2026-07-21 17:20'),
    },
  },
}))

const FormKitStub = defineComponent({
  inheritAttrs: false,
  props: ['modelValue', 'type', 'name', 'label'],
  emits: ['update:modelValue'],
  setup(props, { attrs, emit }) {
    return () =>
      h(props.type === 'textarea' ? 'textarea' : 'input', {
        ...attrs,
        name: props.name,
        value: props.modelValue ?? '',
        'aria-label': props.label,
        onInput: (event: Event) => {
          emit('update:modelValue', (event.target as HTMLInputElement).value)
        },
      })
  },
})

const SearchInputStub = defineComponent({
  inheritAttrs: false,
  props: ['modelValue'],
  emits: ['update:modelValue'],
  setup(props, { attrs, emit }) {
    return () =>
      h('input', {
        ...attrs,
        value: props.modelValue ?? '',
        onKeydown: (event: KeyboardEvent) => {
          if (event.key === 'Enter') {
            emit('update:modelValue', (event.target as HTMLInputElement).value)
          }
        },
      })
  },
})

const api = vi.hoisted(() => ({
  deleteCase: vi.fn(),
  deleteClue: vi.fn(),
  getDashboardStats: vi.fn(),
  listCases: vi.fn(),
  listClues: vi.fn(),
  previewCaseImportBatch: vi.fn(),
  previewCaseImport: vi.fn(),
  previewCaseImportDocument: vi.fn(),
  previewCaseImportDocuments: vi.fn(),
  saveCase: vi.fn(),
  updateCaseStatus: vi.fn(),
  updateClueStatus: vi.fn(),
}))

vi.mock('@/api', () => api)
vi.mock('@/api/applications', () => ({ listApplications: vi.fn(), reviewApplication: vi.fn() }))

describe('CaseEditorModal 分享链接保护', () => {
  it.each(['PUBLISHED', 'FOUND'] as const)(
    '%s 旧案例即使没有时间戳，也保留 slug 并允许修改其他资料',
    async (status) => {
      const wrapper = mount(CaseEditorModal, {
        props: {
          visible: true,
          caseData: { name: 'case-a', slug: 'stable-link', personName: '测试人员', status },
        },
        global: { components: { FormKit: FormKitStub } },
      })
      expect(wrapper.get('[name="slug"]').element).toHaveProperty('disabled', true)
      await wrapper.get('[name="personName"]').setValue('已核对姓名')
      await wrapper.get('form').trigger('submit')
      expect(wrapper.emitted('save')?.[0]?.[0]).toEqual(
        expect.objectContaining({ slug: 'stable-link', personName: '已核对姓名', status }),
      )
    },
  )

  it('未公开草稿仍可调整 slug', async () => {
    const wrapper = mount(CaseEditorModal, {
      props: {
        visible: true,
        caseData: { name: 'draft-a', slug: 'draft-link', personName: '测试人员', status: 'DRAFT' },
      },
      global: { components: { FormKit: FormKitStub } },
    })
    expect(wrapper.get('[name="slug"]').element).toHaveProperty('disabled', false)
    await wrapper.get('[name="slug"]').setValue('updated-draft-link')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('save')?.[0]?.[0]).toEqual(
      expect.objectContaining({ slug: 'updated-draft-link' }),
    )
  })
})

describe('ConsoleDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.values(api).forEach((mock) => mock.mockReset())
    api.getDashboardStats.mockResolvedValue({
      totalCases: 0,
      publishedCases: 0,
      foundCases: 0,
      pendingClues: 0,
    })
    api.listCases.mockResolvedValue({ page: 1, size: 30, total: 0, items: [] })
    api.listClues.mockResolvedValue({ page: 1, size: 30, total: 0, items: [] })
  })

  it('用户点击新增案例后打开信息录入弹窗', async () => {
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="create-case"]').trigger('click')

    expect(wrapper.get('[role="dialog"]').text()).toContain('新增寻亲案例')
  })

  it('用户解析网页后在案例编辑器中核对导入草稿', async () => {
    api.previewCaseImport.mockResolvedValue({
      caseData: {
        personName: '陈安',
        title: '寻找陈安',
        missingLocation: '沈阳市和平区',
        sourceUrl: 'https://example.org/missing/chen-an',
        status: 'DRAFT',
      },
      extractedFieldCount: 4,
      warnings: ['请核对日期与联系方式'],
    })
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="import-case"]').trigger('click')
    await wrapper.get('[name="sourceUrl"]').setValue('https://example.org/missing/chen-an')
    await wrapper.get('[data-testid="preview-case-import"]').trigger('click')
    await flushPromises()

    expect(api.previewCaseImport).toHaveBeenCalledWith({
      sourceUrl: 'https://example.org/missing/chen-an',
      pageContent: undefined,
    })
    expect(wrapper.get('[name="personName"]').element).toHaveProperty('value', '陈安')
    expect(wrapper.get('[name="missingLocation"]').element).toHaveProperty('value', '沈阳市和平区')
    expect(wrapper.get('[data-testid="import-review-notice"]').text()).toContain(
      '请核对日期与联系方式',
    )
  })

  it('网页字段不足且 AI 可用时提示重新使用 AI 解析', async () => {
    api.previewCaseImport
      .mockResolvedValueOnce({
        caseData: {
          sourceUrl: 'https://example.org/missing/empty',
          status: 'DRAFT',
        },
        extractedFieldCount: 0,
        warnings: ['未识别到姓名'],
        aiAvailable: true,
        aiRecommended: true,
      })
      .mockResolvedValueOnce({
        caseData: {
          personName: '陈安',
          missingLocation: '沈阳市和平区',
          status: 'DRAFT',
        },
        extractedFieldCount: 2,
        warnings: ['AI 自动提取结果仅供参考'],
        aiAvailable: true,
        aiGenerated: true,
      })
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="import-case"]').trigger('click')
    await wrapper.get('[name="sourceUrl"]').setValue('https://example.org/missing/empty')
    await wrapper.get('[data-testid="preview-case-import"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="case-import-ai-suggestion"]').text()).toContain(
      '规则解析未识别到有效寻亲字段',
    )
    expect(wrapper.find('[name="personName"]').exists()).toBe(false)

    await wrapper.get('[data-testid="use-ai-import"]').trigger('click')
    await flushPromises()

    expect(api.previewCaseImport).toHaveBeenNthCalledWith(2, {
      sourceUrl: 'https://example.org/missing/empty',
      pageContent: undefined,
      ai: true,
    })
    expect(wrapper.get('[name="personName"]').element).toHaveProperty('value', '陈安')
  })

  it('用户开启 AI 后解析自然语言资料', async () => {
    api.previewCaseImport.mockResolvedValue({
      caseData: { personName: '王小北', status: 'DRAFT' },
      extractedFieldCount: 2,
      warnings: ['AI 自动提取结果仅供参考'],
      aiGenerated: true,
    })
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="import-case"]').trigger('click')
    await wrapper.get('[data-testid="case-import-ai"]').setValue(true)
    await wrapper.get('[name="pageContent"]').setValue('王小北在长春火车站附近走失')
    await wrapper.get('[data-testid="preview-case-import"]').trigger('click')
    await flushPromises()

    expect(api.previewCaseImport).toHaveBeenCalledWith({
      sourceUrl: undefined,
      pageContent: '王小北在长春火车站附近走失',
      ai: true,
    })
    expect(wrapper.get('[name="personName"]').element).toHaveProperty('value', '王小北')
  })

  it('用户上传文档后走 AI 文档解析接口', async () => {
    api.previewCaseImportDocument.mockResolvedValue({
      caseData: { personName: '赵远', sourceName: 'case.pdf', status: 'DRAFT' },
      extractedFieldCount: 2,
      warnings: [],
      aiGenerated: true,
    })
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="import-case"]').trigger('click')
    const file = new File(['寻亲资料'], 'case.pdf', { type: 'application/pdf' })
    const fileInput = wrapper.get('[data-testid="case-import-file"]')
    Object.defineProperty(fileInput.element, 'files', { value: [file] })
    await fileInput.trigger('change')
    await wrapper.get('[data-testid="preview-case-import"]').trigger('click')
    await flushPromises()

    expect(api.previewCaseImportDocument).toHaveBeenCalledWith(file)
    expect(wrapper.get('[name="personName"]').element).toHaveProperty('value', '赵远')
  })

  it('批量 AI 解析后逐条进入案例核对', async () => {
    api.previewCaseImportBatch.mockResolvedValue({
      items: [
        {
          index: 0,
          preview: {
            caseData: { personName: '林小满', status: 'DRAFT' },
            extractedFieldCount: 1,
            warnings: ['请核对'],
            aiGenerated: true,
          },
        },
      ],
      succeeded: 1,
      failed: 0,
    })
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="batch-import-case"]').trigger('click')
    await wrapper
      .get('[data-testid="batch-import-source-text"]')
      .setValue('姓名：林小满\n失踪地点：沈阳市和平区')
    await wrapper.get('[data-testid="preview-batch-import"]').trigger('click')
    await flushPromises()

    expect(api.previewCaseImportBatch).toHaveBeenCalledWith({
      items: [{ pageContent: '姓名：林小满\n失踪地点：沈阳市和平区', ai: true }],
    })
    await wrapper.get('[data-testid="review-batch-import-0"]').trigger('click')
    expect(wrapper.get('[name="personName"]').element).toHaveProperty('value', '林小满')
  })

  it('用户可通过回车逐行输入多个网页地址', async () => {
    api.previewCaseImportBatch.mockResolvedValue({ items: [], succeeded: 0, failed: 0 })
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="batch-import-case"]').trigger('click')
    const sourceText = wrapper.get('[data-testid="batch-import-source-text"]')
    expect((sourceText.element as HTMLTextAreaElement).placeholder).toContain('\n---\n')

    await sourceText.setValue('https://example.org/missing/001\nhttps://example.org/missing/002')
    await wrapper.get('[data-testid="preview-batch-import"]').trigger('click')
    await flushPromises()

    expect(api.previewCaseImportBatch).toHaveBeenCalledWith({
      items: [
        { sourceUrl: 'https://example.org/missing/001', ai: true },
        { sourceUrl: 'https://example.org/missing/002', ai: true },
      ],
    })
  })

  it('关闭批次后保留解析结果和已保存标记，只有确认开始新批次才清空', async () => {
    api.previewCaseImportBatch.mockResolvedValue({
      items: [0, 1].map((index) => ({
        index,
        preview: {
          caseData: { personName: `人员${index}`, title: `寻找人员${index}`, status: 'DRAFT' },
          extractedFieldCount: 2,
          warnings: [],
        },
      })),
      succeeded: 2,
      failed: 0,
    })
    api.saveCase.mockResolvedValue({})
    const wrapper = mount(ConsoleDashboard, { global: { components: { FormKit: FormKitStub } } })
    await flushPromises()
    await wrapper.get('[data-testid="batch-import-case"]').trigger('click')
    await wrapper.get('[data-testid="batch-import-source-text"]').setValue('寻亲资料')
    await wrapper.get('[data-testid="preview-batch-import"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="review-batch-import-0"]').trigger('click')
    await wrapper.get('[data-testid="save-case"]').trigger('click')
    await flushPromises()
    const batch = () => wrapper.get('[data-modal-title="批量 AI 导入寻亲资料"]')
    expect(batch().text()).toContain('已保存')
    await batch()
      .findAll('button')
      .find((button) => button.text() === '关闭')!
      .trigger('click')
    await wrapper.get('[data-testid="batch-import-case"]').trigger('click')
    expect(batch().text()).toContain('已保存')
    expect(wrapper.find('[data-testid="review-batch-import-0"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="review-batch-import-1"]').exists()).toBe(true)
    await wrapper.get('[data-testid="new-batch-import"]').trigger('click')
    expect(wrapper.get('[role="alertdialog"]').text()).toContain('仍有未保存')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    expect(wrapper.find('[data-testid="review-batch-import-1"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="batch-import-source-text"]').element).toHaveProperty(
      'value',
      '',
    )
    expect(api.previewCaseImportBatch).toHaveBeenCalledTimes(1)
  })

  it('用户点击概览指标后进入对应工作区', async () => {
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="metric-cases-ink"]').trigger('click')

    expect(wrapper.get('[data-testid="tab-cases"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[aria-labelledby="case-list-heading"]').text()).toContain('寻亲案例')
  })

  it.each([
    ['cases', 'listCases', 'case', 'DRAFT'],
    ['clues', 'listClues', 'clue', 'PENDING'],
  ] as const)(
    '%s 使用服务端总数分页，状态筛选和搜索均从第一页请求',
    async (tab, apiName, kind, status) => {
      api[apiName].mockResolvedValue({ page: 1, size: 30, total: 65, items: [] })
      const wrapper = mount(ConsoleDashboard, { global: { components: { FormKit: FormKitStub } } })
      await flushPromises()
      await wrapper.get(`[data-testid="tab-${tab}"]`).trigger('click')

      expect(wrapper.get(`[data-testid="${kind}-pagination"]`).text()).toContain('共 65 项数据')
      await wrapper.get('.pagination__btn--next').trigger('click')
      await flushPromises()
      expect(api[apiName]).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, size: 30 }))

      await wrapper.get(`[data-testid="${kind}-filter-${status}"]`).trigger('click')
      await flushPromises()
      expect(api[apiName]).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, status }))

      await wrapper.get('.pagination__btn--next').trigger('click')
      await flushPromises()
      await wrapper.get('input[type="search"]').setValue('车站')
      await wrapper.get(`[data-testid="${kind}-search-submit"]`).trigger('click')
      await flushPromises()
      expect(api[apiName]).toHaveBeenLastCalledWith({ page: 1, size: 30, keyword: '车站', status })

      await wrapper.findAll('.pagination__select')[1]!.setValue('10')
      await flushPromises()
      expect(api[apiName]).toHaveBeenLastCalledWith({ page: 1, size: 10, keyword: '车站', status })
    },
  )

  it.each([
    ['metric-cases-blue', 'listCases', 'case', 'PUBLISHED'],
    ['metric-cases-green', 'listCases', 'case', 'FOUND'],
    ['metric-clues-amber', 'listClues', 'clue', 'PENDING'],
  ] as const)('%s 进入对应状态的完整队列', async (metric, apiName, kind, status) => {
    const wrapper = mount(ConsoleDashboard, { global: { components: { FormKit: FormKitStub } } })
    await flushPromises()
    await wrapper.get(`[data-testid="${metric}"]`).trigger('click')
    await flushPromises()
    expect(api[apiName]).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, status }))
    expect(wrapper.get(`[data-testid="${kind}-filter-${status}"]`).classes()).toContain('is-active')
  })

  it.each([
    ['cases', 'listCases', 'case', 'personName'],
    ['clues', 'listClues', 'clue', 'caseTitle'],
  ] as const)(
    '%s 快速连续搜索时只展示最新响应，忽略过期错误',
    async (tab, apiName, kind, titleKey) => {
      const wrapper = mount(ConsoleDashboard, { global: { components: { FormKit: FormKitStub } } })
      await flushPromises()
      await wrapper.get(`[data-testid="tab-${tab}"]`).trigger('click')
      let rejectOld!: (error: Error) => void
      api[apiName].mockImplementationOnce(
        () =>
          new Promise((_, reject) => {
            rejectOld = reject
          }),
      )
      await wrapper.get('input[type="search"]').setValue('旧查询')
      await wrapper.get(`[data-testid="${kind}-search-submit"]`).trigger('click')
      api[apiName].mockResolvedValueOnce({
        page: 1,
        size: 30,
        total: 1,
        items: [{ name: 'new', [titleKey]: '最新结果' }],
      })
      await wrapper.get('input[type="search"]').setValue('新查询')
      await wrapper.get(`[data-testid="${kind}-search-submit"]`).trigger('click')
      await flushPromises()
      rejectOld(new Error('旧查询失败'))
      await flushPromises()
      expect(wrapper.text()).toContain('最新结果')
      expect(wrapper.text()).not.toContain('旧查询失败')
    },
  )

  it.each([
    ['cases', 'listCases', 'case', 'deleteCase', 'personName'],
    ['clues', 'listClues', 'clue', 'deleteClue', 'caseTitle'],
  ] as const)(
    '%s 删除末页唯一一条后返回有效上一页',
    async (tab, apiName, kind, deleteApi, titleKey) => {
      api[apiName]
        .mockResolvedValueOnce({
          page: 1,
          size: 30,
          total: 31,
          items: [{ name: 'first', [titleKey]: '第一页' }],
        })
        .mockResolvedValueOnce({
          page: 2,
          size: 30,
          total: 31,
          items: [{ name: 'last', [titleKey]: '末页唯一记录' }],
        })
        .mockResolvedValue({
          page: 1,
          size: 30,
          total: 30,
          items: [{ name: 'first', [titleKey]: '第一页' }],
        })
      api[deleteApi].mockResolvedValue({})
      const wrapper = mount(ConsoleDashboard, { global: { components: { FormKit: FormKitStub } } })
      await flushPromises()
      await wrapper.get(`[data-testid="tab-${tab}"]`).trigger('click')
      await wrapper.get('.pagination__btn--next').trigger('click')
      await flushPromises()
      await wrapper.get(`[data-testid="delete-${kind}-last"]`).trigger('click')
      await wrapper.get('[data-testid="confirm-action"]').trigger('click')
      await flushPromises()
      expect(api[apiName]).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
      expect(wrapper.text()).toContain('第一页')
      expect(wrapper.get(`[data-testid="${kind}-pagination"]`).text()).toContain('共 30 项数据')
    },
  )

  it.each([
    ['cases', 'listCases', 'case', 'personName'],
    ['clues', 'listClues', 'clue', 'caseTitle'],
  ] as const)(
    '%s 先发出的搜索晚返回时不会覆盖当前结果与总数',
    async (tab, apiName, kind, titleKey) => {
      const wrapper = mount(ConsoleDashboard, { global: { components: { FormKit: FormKitStub } } })
      await flushPromises()
      await wrapper.get(`[data-testid="tab-${tab}"]`).trigger('click')
      let resolveOld!: (value: unknown) => void
      api[apiName].mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve
          }),
      )
      await wrapper.get('input[type="search"]').setValue('旧查询')
      await wrapper.get(`[data-testid="${kind}-search-submit"]`).trigger('click')
      api[apiName].mockResolvedValueOnce({
        page: 1,
        size: 30,
        total: 1,
        items: [{ name: 'new', [titleKey]: '当前记录' }],
      })
      await wrapper.get('input[type="search"]').setValue('新查询')
      await wrapper.get(`[data-testid="${kind}-search-submit"]`).trigger('click')
      await flushPromises()
      resolveOld({ page: 1, size: 30, total: 65, items: [{ name: 'old', [titleKey]: '过期记录' }] })
      await flushPromises()
      expect(wrapper.text()).toContain('当前记录')
      expect(wrapper.text()).not.toContain('过期记录')
      expect(wrapper.get(`[data-testid="${kind}-pagination"]`).text()).toContain('共 1 项数据')
    },
  )

  it.each([
    ['cases', 'listCases', 'case', 'deleteCase', 'personName'],
    ['clues', 'listClues', 'clue', 'deleteClue', 'caseTitle'],
  ] as const)(
    '%s 删除后即使统计仍在加载，也不会展示索引返回的旧记录',
    async (tab, apiName, kind, deleteApi, titleKey) => {
      api[apiName].mockResolvedValue({
        page: 1,
        size: 30,
        total: 1,
        items: [{ name: 'deleted', [titleKey]: '已删除记录' }],
      })
      api[deleteApi].mockResolvedValue({})
      const wrapper = mount(ConsoleDashboard, { global: { components: { FormKit: FormKitStub } } })
      await flushPromises()
      let finishStats!: (value: unknown) => void
      api.getDashboardStats.mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishStats = resolve
          }),
      )
      await wrapper.get(`[data-testid="tab-${tab}"]`).trigger('click')
      await wrapper.get(`[data-testid="delete-${kind}-deleted"]`).trigger('click')
      await wrapper.get('[data-testid="confirm-action"]').trigger('click')
      await flushPromises()

      expect(wrapper.find(`[data-testid="delete-${kind}-deleted"]`).exists()).toBe(false)
      expect(wrapper.get(`[data-testid="${kind}-pagination"]`).text()).toContain('共 0 项数据')
      finishStats({ totalCases: 0, publishedCases: 0, foundCases: 0, pendingClues: 0 })
      await flushPromises()
    },
  )

  it.each([
    ['cases', 'listCases', 'case'],
    ['clues', 'listClues', 'clue'],
  ] as const)('%s 当前查询失败时不展示上一次查询的总数与分页', async (tab, apiName, kind) => {
    api[apiName].mockResolvedValueOnce({ page: 1, size: 30, total: 65, items: [] })
    const wrapper = mount(ConsoleDashboard, { global: { components: { FormKit: FormKitStub } } })
    await flushPromises()
    await wrapper.get(`[data-testid="tab-${tab}"]`).trigger('click')
    api[apiName].mockRejectedValueOnce(new Error('当前查询失败'))
    await wrapper.get('input[type="search"]').setValue('新查询')
    await wrapper.get(`[data-testid="${kind}-search-submit"]`).trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('当前查询失败')
    expect(wrapper.find(`[data-testid="${kind}-pagination"]`).exists()).toBe(false)
    expect(wrapper.text()).not.toContain('共 65')
  })

  it('概览全部案例清除上次关键词与状态，待处理快捷入口直达待处理队列', async () => {
    api.getDashboardStats.mockResolvedValue({
      totalCases: 65,
      publishedCases: 30,
      foundCases: 1,
      pendingClues: 3,
    })
    const wrapper = mount(ConsoleDashboard, { global: { components: { FormKit: FormKitStub } } })
    await flushPromises()
    await wrapper.get('[data-testid="metric-cases-blue"]').trigger('click')
    await wrapper.get('input[type="search"]').setValue('车站')
    await wrapper.get('[data-testid="case-search-submit"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="tab-overview"]').trigger('click')
    await wrapper.get('[data-testid="metric-cases-ink"]').trigger('click')
    await flushPromises()
    expect(api.listCases).toHaveBeenLastCalledWith({ page: 1, size: 30, keyword: undefined })
    expect(wrapper.get('[data-testid="case-filter-ALL"]').classes()).toContain('is-active')
    await wrapper.get('[data-testid="tab-overview"]').trigger('click')
    await wrapper.get('.homeward-focus-card .homeward-text-action').trigger('click')
    await flushPromises()
    expect(api.listClues).toHaveBeenLastCalledWith({
      page: 1,
      size: 30,
      keyword: undefined,
      status: 'PENDING',
    })
  })

  it('用户输入案例关键字并点击搜索后按关键字请求案例', async () => {
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub, SearchInput: SearchInputStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="tab-cases"]').trigger('click')
    await wrapper.get('[placeholder="搜索姓名、地点或标题"]').setValue('林小满')
    await wrapper.get('[data-testid="case-search-submit"]').trigger('click')
    await flushPromises()

    expect(api.listCases).toHaveBeenLastCalledWith({ page: 1, size: 30, keyword: '林小满' })
  })

  it('用户输入案例关键字并按回车后按关键字请求案例', async () => {
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub, SearchInput: SearchInputStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="tab-cases"]').trigger('click')
    const searchInput = wrapper.get('[placeholder="搜索姓名、地点或标题"]')
    await searchInput.setValue('林小满')
    await searchInput.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(api.listCases).toHaveBeenLastCalledWith({ page: 1, size: 30, keyword: '林小满' })
  })

  it('用户输入线索关键字并提交搜索后按关键字请求线索', async () => {
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub, SearchInput: SearchInputStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="tab-clues"]').trigger('click')
    await wrapper.get('[placeholder="搜索案例、联系人或线索内容"]').setValue('车站')
    await wrapper.get('[data-testid="clue-search-submit"]').trigger('click')
    await flushPromises()

    expect(api.listClues).toHaveBeenLastCalledWith({ page: 1, size: 30, keyword: '车站' })
  })

  it('保存新增案例成功后关闭弹窗并刷新数据', async () => {
    api.saveCase.mockResolvedValue({ name: 'case-lin-xiaoman' })
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="create-case"]').trigger('click')
    await wrapper.get('[name="personName"]').setValue('林小满')
    await wrapper.get('[name="title"]').setValue('寻找林小满')
    await wrapper.get('[data-testid="save-case"]').trigger('click')
    await flushPromises()

    expect(api.saveCase).toHaveBeenCalledWith({
      metadata: undefined,
      spec: expect.objectContaining({ personName: '林小满', title: '寻找林小满' }),
    })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(api.getDashboardStats).toHaveBeenCalledTimes(2)
  })

  it('删除案例前要求确认，确认后才调用删除接口', async () => {
    api.listCases.mockResolvedValue({
      page: 1,
      size: 30,
      total: 1,
      items: [
        {
          name: 'case-lin-xiaoman',
          personName: '林小满',
          title: '寻找林小满',
          status: 'DRAFT',
        },
      ],
    })
    api.deleteCase.mockResolvedValue({})
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="tab-cases"]').trigger('click')
    await wrapper.get('[data-testid="delete-case-case-lin-xiaoman"]').trigger('click')

    expect(api.deleteCase).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alertdialog"]').text()).toContain('删除“寻找林小满”')
    expect(
      wrapper.get('.homeward-confirm-modal').find('[data-testid="confirm-action"]').exists(),
    ).toBe(true)

    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    await flushPromises()

    expect(api.deleteCase).toHaveBeenCalledWith('case-lin-xiaoman')
  })

  it('删除案例后不展示仍出现在过期列表响应中的案例', async () => {
    const caseItem = {
      name: 'case-lin-xiaoman',
      personName: '林小满',
      title: '寻找林小满',
      status: 'DRAFT',
    }
    api.listCases.mockResolvedValue({ page: 1, size: 30, total: 1, items: [caseItem] })
    api.deleteCase.mockResolvedValue({})
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="tab-cases"]').trigger('click')
    await wrapper.get('[data-testid="delete-case-case-lin-xiaoman"]').trigger('click')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="delete-case-case-lin-xiaoman"]').exists()).toBe(false)
  })

  it('删除案例后仍提交已返回的新列表，即使统计刷新失败', async () => {
    const caseItem = {
      name: 'case-lin-xiaoman',
      personName: '林小满',
      title: '寻找林小满',
      status: 'DRAFT',
    }
    api.getDashboardStats
      .mockResolvedValueOnce({ totalCases: 1, publishedCases: 0, foundCases: 0, pendingClues: 0 })
      .mockRejectedValueOnce(new Error('统计暂不可用'))
    api.listCases
      .mockResolvedValueOnce({ page: 1, size: 30, total: 1, items: [caseItem] })
      .mockResolvedValueOnce({ page: 1, size: 30, total: 0, items: [] })
    api.deleteCase.mockResolvedValue({})
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="tab-cases"]').trigger('click')
    await wrapper.get('[data-testid="delete-case-case-lin-xiaoman"]').trigger('click')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="delete-case-case-lin-xiaoman"]').exists()).toBe(false)
  })

  it('删除线索后仍提交已返回的新列表，即使统计刷新失败', async () => {
    const clueItem = {
      name: 'clue-001',
      caseTitle: '寻找林小满',
      submitterName: '周女士',
      contact: '13900139000',
      content: '曾在车站附近见到相似人员',
      status: 'PENDING',
    }
    api.getDashboardStats
      .mockResolvedValueOnce({ totalCases: 0, publishedCases: 0, foundCases: 0, pendingClues: 1 })
      .mockRejectedValueOnce(new Error('统计暂不可用'))
    api.listClues
      .mockResolvedValueOnce({ page: 1, size: 30, total: 1, items: [clueItem] })
      .mockResolvedValueOnce({ page: 1, size: 30, total: 0, items: [] })
    api.deleteClue.mockResolvedValue({})
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="tab-clues"]').trigger('click')
    await wrapper.get('[data-testid="delete-clue-clue-001"]').trigger('click')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="delete-clue-clue-001"]').exists()).toBe(false)
  })

  it('删除线索后不展示仍出现在过期列表响应中的线索', async () => {
    const clueItem = {
      name: 'clue-001',
      caseTitle: '寻找林小满',
      submitterName: '周女士',
      contact: '13900139000',
      content: '曾在车站附近见到相似人员',
      status: 'PENDING',
    }
    api.listClues.mockResolvedValue({ page: 1, size: 30, total: 1, items: [clueItem] })
    api.deleteClue.mockResolvedValue({})
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="tab-clues"]').trigger('click')
    await wrapper.get('[data-testid="delete-clue-clue-001"]').trigger('click')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="delete-clue-clue-001"]').exists()).toBe(false)
  })

  it('在线索详情弹窗中记录备注并更新处理状态', async () => {
    api.listClues.mockResolvedValue({
      page: 1,
      size: 30,
      total: 1,
      items: [
        {
          name: 'clue-001',
          caseTitle: '寻找林小满',
          submitterName: '周女士',
          contact: '13900139000',
          content: '曾在车站附近见到相似人员',
          status: 'PENDING',
        },
      ],
    })
    api.updateClueStatus.mockResolvedValue({})
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="tab-clues"]').trigger('click')
    await wrapper.get('[data-testid="open-clue-clue-001"]').trigger('click')
    await wrapper.get('[name="processNote"]').setValue('已电话核实，信息可信')
    await wrapper.get('[data-testid="clue-status-resolved"]').trigger('click')
    await wrapper.get('[data-testid="save-clue"]').trigger('click')
    await flushPromises()

    expect(api.updateClueStatus).toHaveBeenCalledWith('clue-001', {
      status: 'RESOLVED',
      processNote: '已电话核实，信息可信',
    })
    expect(wrapper.find('[data-testid="save-clue"]').exists()).toBe(false)
  })

  it('线索处理备注默认提供四行录入空间', async () => {
    api.listClues.mockResolvedValue({
      page: 1,
      size: 30,
      total: 1,
      items: [
        {
          name: 'clue-001',
          caseTitle: '寻找林小满',
          submitterName: '周女士',
          contact: '13900139000',
          content: '曾在车站附近见到相似人员',
          status: 'PENDING',
        },
      ],
    })
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="tab-clues"]').trigger('click')
    await wrapper.get('[data-testid="open-clue-clue-001"]').trigger('click')

    expect(wrapper.get('textarea[name="processNote"]').attributes('rows')).toBe('4')
  })

  it('展示完整处理历史，本次备注为空且超长备注不会提交', async () => {
    api.listClues.mockResolvedValue({
      page: 1,
      size: 30,
      total: 1,
      items: [
        {
          name: 'history-clue',
          caseTitle: '寻找家人',
          status: 'CONTACTED',
          processNote: '最近一次备注',
          handler: 'staff-b',
          processingHistory: [
            { status: 'PENDING', handler: 'staff-a', processNote: '旧版备注', legacy: true },
            { status: 'CONTACTED', handler: 'staff-b', processNote: '最近一次备注' },
          ],
        },
      ],
    })
    const wrapper = mount(ConsoleDashboard, { global: { components: { FormKit: FormKitStub } } })
    await flushPromises()
    await wrapper.get('[data-testid="tab-clues"]').trigger('click')
    await wrapper.get('[data-testid="open-clue-history-clue"]').trigger('click')
    const records = wrapper.get('[aria-label="处理记录"]')
    expect(records.text()).toContain('staff-a')
    expect(records.text()).toContain('staff-b')
    expect(records.text()).toContain('旧版保留记录')
    expect(records.findAll('li')[0]!.text()).toContain('最近一次备注')
    const note = wrapper.get('textarea[name="processNote"]')
    expect(note.element).toHaveProperty('value', '')
    expect(note.attributes('maxlength')).toBe('2000')
    await note.setValue('字'.repeat(2001))
    await wrapper.get('[data-testid="save-clue"]').trigger('click')
    expect(api.updateClueStatus).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('不能超过 2000')
  })

  it('旧线索缺少历史数组仍展示最后记录', async () => {
    api.listClues.mockResolvedValue({
      page: 1,
      size: 30,
      total: 1,
      items: [
        {
          name: 'old-clue',
          status: 'CONTACTED',
          processNote: '曾经电话核实',
          handler: 'previous-user',
        },
      ],
    })
    const wrapper = mount(ConsoleDashboard, { global: { components: { FormKit: FormKitStub } } })
    await flushPromises()
    await wrapper.get('[data-testid="tab-clues"]').trigger('click')
    await wrapper.get('[data-testid="open-clue-old-clue"]').trigger('click')
    expect(wrapper.get('[aria-label="处理记录"]').text()).toContain('旧版保留记录')
    expect(wrapper.get('[aria-label="处理记录"]').text()).toContain('曾经电话核实')
    expect(wrapper.get('textarea[name="processNote"]').element).toHaveProperty('value', '')
  })

  it('打开线索详情时以可读格式显示提交时间', async () => {
    api.listClues.mockResolvedValue({
      page: 1,
      size: 30,
      total: 1,
      items: [
        {
          name: 'clue-002',
          caseTitle: '寻找林小满',
          submitterName: '周女士',
          contact: '13900139000',
          content: '曾在车站附近见到相似人员',
          submittedAt: '2026-07-21T09:20:39.738589406Z',
          status: 'PENDING',
        },
      ],
    })
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="tab-clues"]').trigger('click')
    await wrapper.get('[data-testid="open-clue-clue-002"]').trigger('click')

    const detailText = wrapper.get('[role="dialog"]').text()
    expect(detailText).toContain('2026-07-21')
    expect(detailText).not.toContain('T09:20:39.738589406Z')
  })
})
