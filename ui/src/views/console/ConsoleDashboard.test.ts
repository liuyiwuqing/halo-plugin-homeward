import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ConsoleDashboard from './ConsoleDashboard.vue'

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

describe('ConsoleDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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

  it('用户点击概览指标后进入对应工作区', async () => {
    const wrapper = mount(ConsoleDashboard, {
      global: { components: { FormKit: FormKitStub } },
    })

    await flushPromises()
    await wrapper.get('[data-testid="metric-cases-ink"]').trigger('click')

    expect(wrapper.get('[data-testid="tab-cases"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[aria-labelledby="case-list-heading"]').text()).toContain('寻亲案例')
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
      handler: 'admin',
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
