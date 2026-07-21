import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import CaseEditorModal from './CaseEditorModal.vue'

vi.mock('@halo-dev/components', () => ({
  VButton: defineComponent({
    inheritAttrs: false,
    setup(_, { attrs, slots }) {
      return () => h('button', attrs, slots.default?.())
    },
  }),
  VModal: defineComponent({
    props: ['title', 'visible'],
    setup(props, { slots }) {
      return () =>
        props.visible === false
          ? null
          : h('div', { 'data-modal-title': props.title }, [slots.default?.(), slots.footer?.()])
    },
  }),
}))

const FormKitStub = defineComponent({
  inheritAttrs: false,
  props: ['modelValue', 'type', 'name', 'label'],
  emits: ['update:modelValue'],
  setup(props, { attrs, emit }) {
    return () =>
      h('input', {
        ...attrs,
        name: props.name,
        type: props.type === 'number' ? 'number' : 'text',
        value: props.modelValue ?? '',
        'aria-label': props.label,
        onInput: (event: Event) => {
          emit('update:modelValue', (event.target as HTMLInputElement).value)
        },
      })
  },
})

describe('CaseEditorModal', () => {
  it('由 visible 控制弹窗展示，不依赖反复挂载表单', async () => {
    const wrapper = mount(CaseEditorModal, {
      props: { visible: false },
      global: { components: { FormKit: FormKitStub } },
    })

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    await wrapper.setProps({ visible: true })

    expect(wrapper.get('[role="dialog"]').text()).toContain('新增寻亲案例')
  })

  it('提交有效信息时向页面交付案例数据', async () => {
    const wrapper = mount(CaseEditorModal, {
      props: { visible: true },
      global: { components: { FormKit: FormKitStub } },
    })

    await wrapper.get('[name="personName"]').setValue('林小满')
    await wrapper.get('[name="title"]').setValue('寻找林小满')
    await wrapper.get('[data-testid="save-case"]').trigger('click')

    expect(wrapper.emitted('save')?.[0]).toEqual([
      expect.objectContaining({ personName: '林小满', title: '寻找林小满' }),
    ])
  })

  it('姓名为空时阻止保存并给出明确提示', async () => {
    const wrapper = mount(CaseEditorModal, {
      props: { visible: true },
      global: { components: { FormKit: FormKitStub } },
    })

    await wrapper.get('[data-testid="save-case"]').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.get('[role="alert"]').text()).toContain('请填写失踪人员姓名')
  })

  it('编辑已有案例时回填完整联系信息', () => {
    const wrapper = mount(CaseEditorModal, {
      props: {
        visible: true,
        caseData: {
          name: 'case-lin-xiaoman',
          personName: '林小满',
          title: '寻找林小满',
          contactName: '林海',
          contactPhone: '13800138000',
        },
      },
      global: { components: { FormKit: FormKitStub } },
    })

    expect(wrapper.attributes('data-modal-title')).toBe('编辑寻亲案例')
    expect(wrapper.get('[name="contactName"]').element).toHaveProperty('value', '林海')
    expect(wrapper.get('[name="contactPhone"]').element).toHaveProperty('value', '13800138000')
  })

  it('联系电话格式异常时阻止保存', async () => {
    const wrapper = mount(CaseEditorModal, {
      props: { visible: true },
      global: { components: { FormKit: FormKitStub } },
    })

    await wrapper.get('[name="personName"]').setValue('林小满')
    await wrapper.get('[name="contactPhone"]').setValue('这不是电话号码')
    await wrapper.get('[data-testid="save-case"]').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.get('[role="alert"]').text()).toContain('联系电话格式不正确')
  })

  it('Slug 含空格或特殊字符时阻止保存', async () => {
    const wrapper = mount(CaseEditorModal, {
      props: { visible: true },
      global: { components: { FormKit: FormKitStub } },
    })

    await wrapper.get('[name="personName"]').setValue('林小满')
    await wrapper.get('[name="slug"]').setValue('Lin Xiao Man!')
    await wrapper.get('[data-testid="save-case"]').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.get('[role="alert"]').text()).toContain('Slug 只能包含小写字母、数字和连字符')
  })

  it('来源链接不是 HTTP 地址时阻止保存', async () => {
    const wrapper = mount(CaseEditorModal, {
      props: { visible: true },
      global: { components: { FormKit: FormKitStub } },
    })

    await wrapper.get('[name="personName"]').setValue('林小满')
    await wrapper.get('[name="sourceUrl"]').setValue('not-a-url')
    await wrapper.get('[data-testid="save-case"]').trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
    expect(wrapper.get('[role="alert"]').text()).toContain('来源链接格式不正确')
  })
})
