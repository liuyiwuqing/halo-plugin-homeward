<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { VButton, VModal } from '@halo-dev/components'
import type { MissingPersonCaseVO } from '@/type'

const props = defineProps<{
  visible: boolean
  caseData?: MissingPersonCaseVO | null
  saving?: boolean
  error?: string
  reviewWarnings?: string[]
}>()

const emit = defineEmits<{
  close: []
  save: [value: MissingPersonCaseVO]
}>()

const title = computed(() => (props.caseData?.name ? '编辑寻亲案例' : '新增寻亲案例'))
const validationError = ref('')
const displayedError = computed(() => props.error || validationError.value)
const form = reactive<MissingPersonCaseVO>({
  slug: '',
  personName: '',
  title: '',
  gender: '',
  birthDate: '',
  missingDate: '',
  missingAge: undefined,
  currentAge: undefined,
  missingLocation: '',
  hometown: '',
  coverImage: '',
  physicalFeatures: '',
  description: '',
  contactName: '',
  contactPhone: '',
  sourceName: '',
  sourceUrl: '',
  status: 'DRAFT',
  priority: 0,
})

watch(
  () => [props.visible, props.caseData] as const,
  () => {
    validationError.value = ''
    Object.assign(form, {
      slug: '',
      personName: '',
      title: '',
      gender: '',
      birthDate: '',
      missingDate: '',
      missingAge: undefined,
      currentAge: undefined,
      missingLocation: '',
      hometown: '',
      coverImage: '',
      physicalFeatures: '',
      description: '',
      contactName: '',
      contactPhone: '',
      sourceName: '',
      sourceUrl: '',
      status: 'DRAFT',
      priority: 0,
      ...props.caseData,
    })
  },
  { immediate: true },
)

function isHttpUrl(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

function submit() {
  if (!form.personName?.trim()) {
    validationError.value = '请填写失踪人员姓名'
    return
  }
  if (form.slug && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(form.slug.trim())) {
    validationError.value = 'Slug 只能包含小写字母、数字和连字符'
    return
  }
  if (form.contactPhone && !/^[+()\d][+()\d\s-]{5,23}$/.test(form.contactPhone.trim())) {
    validationError.value = '联系电话格式不正确，请填写数字、空格或连字符'
    return
  }
  if (form.sourceUrl && !isHttpUrl(form.sourceUrl.trim())) {
    validationError.value = '来源链接格式不正确，请填写以 http:// 或 https:// 开头的地址'
    return
  }
  validationError.value = ''
  emit('save', { ...form })
}
</script>

<template>
  <VModal
    class="homeward-case-modal"
    :visible="visible"
    :width="860"
    :title="title"
    :centered="false"
    :layer-closable="false"
    :body-class="['homeward-case-editor-body']"
    @close="emit('close')"
  >
    <section
      class="homeward-case-editor"
      role="dialog"
      aria-modal="true"
      aria-labelledby="homeward-case-editor-title"
    >
      <h2 id="homeward-case-editor-title" class="homeward-visually-hidden">{{ title }}</h2>
      <p class="homeward-case-editor-intro">完善基本信息后保存，稍后仍可继续补充资料。</p>
      <div
        v-if="reviewWarnings?.length"
        class="homeward-import-review-notice"
        data-testid="import-review-notice"
        role="status"
      >
        <strong>网页资料已回填，请先核对再保存</strong>
        <ul>
          <li v-for="warning in reviewWarnings" :key="warning">{{ warning }}</li>
        </ul>
      </div>
      <form class="homeward-case-editor-form" @submit.prevent="submit">
        <fieldset class="homeward-form-section">
          <legend>基本信息</legend>
          <p>用于确认失踪人员身份和失踪时间地点。</p>
          <div class="homeward-form-grid">
            <FormKit
              v-model="form.personName"
              outer-class="homeward-form-field"
              type="text"
              name="personName"
              label="姓名"
              validation="required"
            />
            <FormKit
              v-model="form.title"
              outer-class="homeward-form-field"
              type="text"
              name="title"
              label="案例标题"
            />
            <FormKit
              v-model="form.gender"
              outer-class="homeward-form-field"
              type="select"
              name="gender"
              label="性别"
              :options="[
                { label: '未填写', value: '' },
                { label: '男', value: '男' },
                { label: '女', value: '女' },
                { label: '其他', value: '其他' },
              ]"
            />
            <FormKit
              v-model="form.birthDate"
              outer-class="homeward-form-field"
              type="date"
              name="birthDate"
              label="出生日期"
            />
            <FormKit
              v-model="form.missingDate"
              outer-class="homeward-form-field"
              type="date"
              name="missingDate"
              label="失踪日期"
            />
            <FormKit
              v-model.number="form.missingAge"
              outer-class="homeward-form-field"
              type="number"
              name="missingAge"
              label="失踪时年龄"
              min="0"
            />
            <FormKit
              v-model.number="form.currentAge"
              outer-class="homeward-form-field"
              type="number"
              name="currentAge"
              label="当前年龄"
              min="0"
            />
            <FormKit
              v-model="form.hometown"
              outer-class="homeward-form-field"
              type="text"
              name="hometown"
              label="籍贯"
            />
            <FormKit
              v-model="form.missingLocation"
              outer-class="homeward-form-field is-wide"
              type="text"
              name="missingLocation"
              label="失踪地点"
            />
          </div>
        </fieldset>

        <fieldset class="homeward-form-section">
          <legend>展示内容</legend>
          <p>这些信息会用于公益页面展示，请尽量准确、克制地描述。</p>
          <div class="homeward-display-layout">
            <div class="homeward-display-controls">
              <FormKit
                v-model="form.slug"
                outer-class="homeward-form-field"
                type="text"
                name="slug"
                label="Slug"
                help="留空时由系统自动生成"
              />
              <FormKit
                v-model="form.status"
                outer-class="homeward-form-field"
                type="select"
                name="status"
                label="状态"
                :options="[
                  { label: '草稿', value: 'DRAFT' },
                  { label: '已发布', value: 'PUBLISHED' },
                  { label: '已找到', value: 'FOUND' },
                  { label: '已归档', value: 'ARCHIVED' },
                ]"
              />
              <FormKit
                v-model.number="form.priority"
                outer-class="homeward-form-field"
                type="number"
                name="priority"
                label="展示优先级"
                help="数字越大，公开页面中的展示顺序越靠前"
                min="0"
              />
            </div>
            <FormKit
              v-model="form.coverImage"
              outer-class="homeward-form-field homeward-cover-field"
              type="attachment"
              name="coverImage"
              label="封面图"
              help="建议使用横向或接近 4:3 的图片"
              :accepts="['image/*']"
              width="100%"
            />
          </div>
          <div class="homeward-display-copy">
            <FormKit
              v-model="form.physicalFeatures"
              outer-class="homeward-form-field homeward-textarea-field"
              type="textarea"
              name="physicalFeatures"
              label="体貌特征"
              :auto-height="true"
            />
            <FormKit
              v-model="form.description"
              outer-class="homeward-form-field homeward-textarea-field"
              type="textarea"
              name="description"
              label="详细描述"
              :auto-height="true"
            />
          </div>
        </fieldset>

        <fieldset class="homeward-form-section">
          <legend>联系与来源</legend>
          <p>联系方式仅供寻亲核实使用，发布前请确认获得授权。</p>
          <div class="homeward-form-grid">
            <FormKit
              v-model="form.contactName"
              outer-class="homeward-form-field"
              type="text"
              name="contactName"
              label="联系人"
            />
            <FormKit
              v-model="form.contactPhone"
              outer-class="homeward-form-field"
              type="text"
              name="contactPhone"
              label="联系电话"
            />
            <FormKit
              v-model="form.sourceName"
              outer-class="homeward-form-field"
              type="text"
              name="sourceName"
              label="来源名称"
            />
            <FormKit
              v-model="form.sourceUrl"
              outer-class="homeward-form-field"
              type="url"
              name="sourceUrl"
              label="来源链接"
            />
          </div>
        </fieldset>
      </form>
      <p v-if="displayedError" class="homeward-form-error" role="alert">{{ displayedError }}</p>
    </section>
    <template #footer>
      <VButton type="secondary" @click="emit('close')">取消</VButton>
      <VButton
        data-testid="save-case"
        type="primary"
        :disabled="saving"
        :loading="saving"
        @click="submit"
      >
        {{ saving ? '保存中' : '保存案例' }}
      </VButton>
    </template>
  </VModal>
</template>
