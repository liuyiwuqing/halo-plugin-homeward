import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CaseApplicationPanel from './CaseApplicationPanel.vue'

const api = vi.hoisted(() => ({ listApplications: vi.fn(), reviewApplication: vi.fn() }))
vi.mock('@/api/applications', () => api)
vi.mock('@halo-dev/ui-shared', () => ({ utils: { date: { format: () => '2026-10-11 16:00' } } }))
vi.mock('@halo-dev/components', () => ({
  VButton: defineComponent({
    setup(_, { attrs, slots }) {
      return () => h('button', attrs, slots.default?.())
    },
  }),
  VModal: defineComponent({
    setup(_, { slots }) {
      return () => h('section', [slots.default?.(), slots.footer?.()])
    },
  }),
  VPagination: defineComponent({
    props: ['page', 'size'],
    emits: ['change'],
    setup(props, { emit }) {
      return () =>
        h(
          'button',
          {
            'data-test-next': '',
            onClick: () => emit('change', { page: props.page + 1, size: props.size }),
          },
          '下一页',
        )
    },
  }),
}))
const application = {
  metadata: { name: 'homeward-application-test' },
  spec: {
    personName: '隔离测试人员',
    missingLocation: '测试城市',
    description: '测试说明',
    applicantName: '私密申请人',
    relationship: '亲属',
    contact: 'test-contact',
    status: 'PENDING',
    submittedAt: '2026-10-11',
    consentAt: '2026-10-11',
  },
}
function panel() {
  return mount(CaseApplicationPanel, {
    global: { stubs: { FormKit: true }, directives: { permission: () => {} } },
  })
}
beforeEach(() => {
  api.listApplications.mockReset()
  api.reviewApplication.mockReset()
  api.listApplications.mockResolvedValue({ page: 1, size: 20, total: 21, items: [application] })
})
describe('application review', () => {
  it('uses server pagination and resets to page one when the filter changes', async () => {
    const wrapper = panel()
    await flushPromises()
    await wrapper.get('[data-test-next]').trigger('click')
    await flushPromises()
    expect(api.listApplications).toHaveBeenLastCalledWith({ page: 2, size: 20, status: 'PENDING' })
    await wrapper.get('select').setValue('ACCEPTED')
    await flushPromises()
    expect(api.listApplications).toHaveBeenLastCalledWith({ page: 1, size: 20, status: 'ACCEPTED' })
    wrapper.unmount()
  })
  it('turns a reviewed application into a draft and asks the dashboard to refresh', async () => {
    const wrapper = panel()
    api.reviewApplication.mockResolvedValue({
      ...application,
      spec: { ...application.spec, status: 'ACCEPTED' },
    })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '查看申请')!
      .trigger('click')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '已核实，转为草稿')!
      .trigger('click')
    await flushPromises()
    expect(api.reviewApplication).toHaveBeenCalledWith('homeward-application-test', 'accept', '')
    expect(wrapper.emitted('created')).toHaveLength(1)
    expect(wrapper.text()).toContain('核对内容、隐私和授权后发布')
    wrapper.unmount()
  })
  it('keeps the review open with an actionable reason when optimistic locking rejects the action', async () => {
    const wrapper = panel()
    api.reviewApplication.mockRejectedValue({
      response: { data: { detail: '申请已由其他管理员处理，请刷新' } },
    })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '查看申请')!
      .trigger('click')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '已核实，转为草稿')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.get('[role=alert]').text()).toContain('其他管理员处理')
    expect(wrapper.text()).toContain('私密申请人')
    expect(wrapper.emitted('created')).toBeUndefined()
    wrapper.unmount()
  })
})
