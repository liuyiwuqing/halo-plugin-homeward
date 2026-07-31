<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { VButton, VModal } from '@halo-dev/components'
import RiFileCopy2Line from '~icons/ri/file-copy-2-line'
import RiGlobalLine from '~icons/ri/global-line'
import type { CaseImportRequest } from '@/type'

const props = defineProps<{
  visible: boolean
  loading?: boolean
  error?: string
  suggestAi?: boolean
  suggestion?: string
}>()

const emit = defineEmits<{
  close: []
  preview: [value: CaseImportRequest]
  previewDocument: [file: File]
}>()

const form = reactive({
  sourceUrl: '',
  pageContent: '',
  useAi: false,
})
const validationError = ref('')
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const displayedError = computed(() => props.error || validationError.value)

watch(
  () => props.visible,
  (visible) => {
    if (!visible) return
    form.sourceUrl = ''
    form.pageContent = ''
    form.useAi = false
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
    validationError.value = ''
  },
)

function isHttpUrl(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] || null
  if (selectedFile.value) {
    form.sourceUrl = ''
    form.pageContent = ''
    form.useAi = true
  }
  validationError.value = ''
}

function submit() {
  const sourceUrl = form.sourceUrl.trim() || undefined
  const pageContent = form.pageContent.trim() || undefined
  if (selectedFile.value) {
    if (!form.useAi) {
      validationError.value = '文档导入需要开启 AI 智能识别'
      return
    }
    validationError.value = ''
    emit('previewDocument', selectedFile.value)
    return
  }
  if (!sourceUrl && !pageContent) {
    validationError.value = '请填写网页地址，或粘贴网页正文'
    return
  }
  if (sourceUrl && !isHttpUrl(sourceUrl)) {
    validationError.value = '网页地址格式不正确，请填写以 http:// 或 https:// 开头的地址'
    return
  }
  validationError.value = ''
  emit('preview', { sourceUrl, pageContent, ...(form.useAi ? { ai: true } : {}) })
}

function useAiSuggestion() {
  form.useAi = true
  submit()
}
</script>

<template>
  <VModal
    class="homeward-import-modal"
    :visible="visible"
    :width="640"
    title="从网页或文档导入寻亲资料"
    :centered="false"
    :layer-closable="false"
    :body-class="['homeward-import-modal-body']"
    @close="emit('close')"
  >
    <section class="homeward-case-import" role="dialog" aria-modal="true">
      <div class="homeward-import-guide">
        <div>
          <RiGlobalLine aria-hidden="true" />
          <p><strong>直接抓取</strong><span>填写公开网页地址，插件会提取常见寻亲字段。</span></p>
        </div>
        <div>
          <RiFileCopy2Line aria-hidden="true" />
          <p><strong>复制粘贴兜底</strong><span>登录、反爬或动态网页可复制正文后粘贴。</span></p>
        </div>
        <div>
          <RiFileCopy2Line aria-hidden="true" />
          <p><strong>AI 智能提取</strong><span>从自然语言、PDF 或 Word 中识别案例字段。</span></p>
        </div>
      </div>
      <div
        v-if="suggestAi"
        class="homeward-import-ai-suggestion"
        data-testid="case-import-ai-suggestion"
        role="status"
      >
        <div>
          <strong>网页字段不完整</strong>
          <span>{{ suggestion || '规则解析没有识别到足够的寻亲字段，建议交给 AI 重新整理。' }}</span>
        </div>
        <VButton
          data-testid="use-ai-import"
          type="secondary"
          :disabled="loading"
          @click="useAiSuggestion"
        >
          使用 AI 智能解析
        </VButton>
      </div>
      <form class="homeward-import-form" @submit.prevent="submit">
        <label class="homeward-import-ai-toggle">
          <input v-model="form.useAi" type="checkbox" data-testid="case-import-ai" />
          <span>
            <strong>使用 AI 智能识别</strong>
            <small>使用 AI Foundation 配置的默认语言模型；结果仍需人工核对。</small>
          </span>
        </label>
        <FormKit
          v-model="form.sourceUrl"
          outer-class="homeward-form-field"
          type="url"
          name="sourceUrl"
          label="来源网页地址"
          placeholder="https://example.org/missing-person/123"
          help="仅支持公网 HTTP/HTTPS 网页；此地址也会记录为案例来源。"
        />
        <div class="homeward-import-divider"><span>可选兜底</span></div>
        <FormKit
          v-model="form.pageContent"
          outer-class="homeward-form-field homeward-import-content-field"
          type="textarea"
          name="pageContent"
          label="网页正文或 HTML"
          placeholder="在来源页面复制包含姓名、失踪时间、地点等信息的正文并粘贴到这里"
          help="填写后只解析粘贴内容，不会访问上面的网页地址。"
          :rows="10"
        />
        <label class="homeward-import-file-field">
          <span>上传文档（可选）</span>
          <input
            ref="fileInput"
            type="file"
            accept=".pdf,.doc,.docx,.txt,.md,.html,.htm,.json,.rtf"
            :disabled="loading"
            data-testid="case-import-file"
            @change="handleFileChange"
          />
          <small>{{ selectedFile?.name || '支持 PDF、Word、TXT、Markdown、HTML，单个不超过 10 MB。' }}</small>
        </label>
      </form>
      <p class="homeward-import-safety-note">
        导入只生成待核对草稿，不会自动保存或发布。请确认信息来源、隐私授权和字段准确性。
      </p>
      <p v-if="displayedError" class="homeward-form-error" role="alert">{{ displayedError }}</p>
    </section>
    <template #footer>
      <VButton type="secondary" :disabled="loading" @click="emit('close')">取消</VButton>
      <VButton
        data-testid="preview-case-import"
        type="primary"
        :disabled="loading"
        :loading="loading"
        @click="submit"
      >
        {{ loading ? '正在解析' : '解析并核对' }}
      </VButton>
    </template>
  </VModal>
</template>
