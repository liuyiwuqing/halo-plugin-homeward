<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { VButton, VModal } from '@halo-dev/components'
import type { CaseImportBatchItem, CaseImportBatchPreview, CaseImportBatchRequest } from '@/type'

const props = defineProps<{
  visible: boolean
  loading?: boolean
  error?: string
  result?: CaseImportBatchPreview | null
  reviewed?: number[]
}>()

const emit = defineEmits<{
  close: []
  preview: [payload: CaseImportBatchRequest, files: File[]]
  review: [item: CaseImportBatchItem]
  'new-batch': []
}>()

const form = reactive({ sourceText: '' })
const selectedFiles = ref<File[]>([])
const validationError = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const displayedError = computed(() => props.error || validationError.value)
const maxBatchItems = 20
const reviewing = computed(() => Boolean(props.result))
const batchImportPlaceholder = [
  'https://example.org/missing/001',
  'https://example.org/missing/002',
  '---',
  '姓名：林小满',
  '失踪地点：沈阳市和平区',
  '---',
  '粘贴另一份寻亲资料',
].join('\n')

watch(
  () => [props.visible, props.result] as const,
  ([visible]) => {
    if (!visible || props.result) return
    form.sourceText = ''
    selectedFiles.value = []
    validationError.value = ''
    if (fileInput.value) fileInput.value.value = ''
  },
)

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFiles.value = Array.from(input.files || [])
  validationError.value = ''
}

function parseItems() {
  return form.sourceText.split(/\r?\n\s*-{3,}\s*\r?\n/).flatMap((item) => {
    const content = item.trim()
    if (!content) return []

    const lines = content
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
    const entries =
      lines.length > 1 && lines.every((line) => /^https?:\/\//i.test(line)) ? lines : [content]

    return entries.map((entry) =>
      /^https?:\/\//i.test(entry)
        ? { sourceUrl: entry, ai: true }
        : { pageContent: entry, ai: true },
    )
  })
}

function submit() {
  const items = parseItems()
  if (!items.length && !selectedFiles.value.length) {
    validationError.value = '请粘贴至少一条资料，或选择要解析的文档'
    return
  }
  if (items.length + selectedFiles.value.length > maxBatchItems) {
    validationError.value = `单次最多解析 ${maxBatchItems} 条资料，请减少文本段或文档数量`
    return
  }
  validationError.value = ''
  emit('preview', { items }, selectedFiles.value)
}

function isReviewed(index: number) {
  return props.reviewed?.includes(index) || false
}
</script>

<template>
  <VModal
    class="homeward-batch-import-modal"
    :visible="visible"
    :width="760"
    title="批量 AI 导入寻亲资料"
    :centered="false"
    :layer-closable="false"
    :body-class="['homeward-batch-import-modal-body']"
    @close="emit('close')"
  >
    <section class="homeward-batch-import" role="dialog" aria-modal="true">
      <template v-if="!reviewing">
        <p class="homeward-batch-import-intro">
          每行一个网页地址，回车即可继续添加，多条链接无需分隔符；一份文本资料内可直接换行，多份文本请使用单独一行的
          <code>---</code> 分隔。批量任务最多 20 条，文档单个不超过 10 MB。
        </p>
        <textarea
          v-model="form.sourceText"
          class="homeward-batch-import-textarea"
          rows="10"
          :placeholder="batchImportPlaceholder"
          data-testid="batch-import-source-text"
        />
        <label class="homeward-import-file-field homeward-batch-file-field">
          <span>批量上传文档（可选）</span>
          <input
            ref="fileInput"
            type="file"
            multiple
            accept=".pdf,.doc,.docx,.txt,.md,.html,.htm,.json,.rtf"
            :disabled="loading"
            data-testid="batch-import-files"
            @change="handleFileChange"
          />
          <small>
            {{
              selectedFiles.length
                ? `已选择 ${selectedFiles.length} 个文档`
                : '支持 PDF、Word、TXT、Markdown、HTML。'
            }}
          </small>
        </label>
      </template>
      <template v-else>
        <div class="homeward-batch-import-summary">
          <strong>AI 解析完成</strong>
          <span
            >成功 {{ result?.succeeded || 0 }} 条，失败
            {{ result?.failed || 0 }} 条。请逐条核对后保存。</span
          >
          <small>关闭后可在本页继续核对；刷新或离开页面会清除尚未保存的结果。</small>
        </div>
        <ul class="homeward-batch-import-results">
          <li v-for="item in result?.items || []" :key="item.index">
            <div>
              <strong>{{ item.preview?.caseData.personName || '未识别姓名' }}</strong>
              <span>{{
                item.preview?.caseData.title || item.sourceUrl || `第 ${item.index + 1} 条资料`
              }}</span>
              <small v-if="item.error" class="is-error">{{ item.error }}</small>
              <small v-else-if="isReviewed(item.index)" class="is-reviewed">已保存</small>
            </div>
            <VButton
              v-if="item.preview && !isReviewed(item.index)"
              size="sm"
              type="secondary"
              :data-testid="`review-batch-import-${item.index}`"
              @click="emit('review', item)"
            >
              核对并保存
            </VButton>
          </li>
        </ul>
      </template>
      <p v-if="displayedError" class="homeward-form-error" role="alert">{{ displayedError }}</p>
    </section>
    <template #footer>
      <VButton type="secondary" :disabled="loading" @click="emit('close')">关闭</VButton>
      <VButton
        v-if="reviewing"
        data-testid="new-batch-import"
        type="secondary"
        :disabled="loading"
        @click="emit('new-batch')"
        >开始新批次</VButton
      >
      <VButton
        v-if="!reviewing"
        data-testid="preview-batch-import"
        type="primary"
        :disabled="loading"
        :loading="loading"
        @click="submit"
      >
        {{ loading ? '正在批量解析' : '开始 AI 解析' }}
      </VButton>
    </template>
  </VModal>
</template>
