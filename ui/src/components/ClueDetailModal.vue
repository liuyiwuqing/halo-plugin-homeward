<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { VButton, VModal } from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'
import RiCheckboxCircleLine from '~icons/ri/checkbox-circle-line'
import RiFileTextLine from '~icons/ri/file-text-line'
import RiMapPin2Line from '~icons/ri/map-pin-2-line'
import RiPhoneLine from '~icons/ri/phone-line'
import RiTimeLine from '~icons/ri/time-line'
import RiUser3Line from '~icons/ri/user-3-line'
import type { ClueReportVO, ClueStatus } from '@/type'

const props = defineProps<{
  visible: boolean
  clue: ClueReportVO
  saving?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
  save: [value: { status: ClueStatus; processNote?: string }]
}>()

const form = reactive<{ status: ClueStatus; processNote: string }>({
  status: 'PENDING',
  processNote: '',
})
const validationError = ref('')
const displayedError = computed(() => validationError.value || props.error)
const history = computed(() => {
  if (props.clue.processingHistory?.length) return [...props.clue.processingHistory].reverse()
  if (
    props.clue.processNote ||
    props.clue.handler ||
    (props.clue.status && props.clue.status !== 'PENDING')
  ) {
    return [
      {
        status: props.clue.status,
        handler: props.clue.handler,
        processNote: props.clue.processNote,
        processedAt: props.clue.updatedAt,
        legacy: true,
      },
    ]
  }
  return []
})

const statusOptions: Array<{ value: ClueStatus; label: string; hint: string }> = [
  { value: 'PENDING', label: '待处理', hint: '尚未开始核实' },
  { value: 'CONTACTED', label: '已联系', hint: '已与提交人沟通' },
  { value: 'RESOLVED', label: '已解决', hint: '线索已完成闭环' },
  { value: 'INVALID', label: '无效线索', hint: '信息无法采用' },
]

const currentStatus = computed(
  () => statusOptions.find((item) => item.value === form.status) || statusOptions[0],
)

watch(
  () => [props.visible, props.clue] as const,
  () => {
    form.status = props.clue.status || 'PENDING'
    form.processNote = ''
    validationError.value = ''
  },
  { immediate: true },
)

function submit() {
  if (props.saving) return
  if (form.processNote.length > 2000) {
    validationError.value = '处理备注不能超过 2000 个字符'
    return
  }
  validationError.value = ''
  emit('save', { status: form.status, processNote: form.processNote.trim() || undefined })
}

function statusLabel(status?: ClueStatus) {
  return statusOptions.find((item) => item.value === status)?.label || '未知状态'
}

function close() {
  if (!props.saving) emit('close')
}

function formatSubmittedAt(value?: string) {
  return value ? utils.date.format(value, 'YYYY-MM-DD HH:mm') : '未知'
}
</script>

<template>
  <VModal
    class="homeward-clue-modal"
    :width="760"
    title="线索详情"
    :centered="false"
    :layer-closable="false"
    :body-class="['homeward-clue-modal-body']"
    @close="close"
  >
    <section
      class="homeward-clue-detail"
      role="dialog"
      aria-modal="true"
      aria-labelledby="homeward-clue-detail-title"
    >
      <header class="homeward-clue-detail-header">
        <div class="homeward-clue-title-block">
          <span class="homeward-eyebrow">关联案例</span>
          <div class="homeward-clue-title-row">
            <h2 id="homeward-clue-detail-title">{{ clue.caseTitle || '未关联案例' }}</h2>
            <span :class="['homeward-clue-current-status', `is-${form.status.toLowerCase()}`]">
              <span aria-hidden="true"></span>
              {{ currentStatus.label }}
            </span>
          </div>
          <p>核对访客信息与线索内容，再记录本次处理结论。</p>
        </div>
      </header>

      <dl class="homeward-clue-meta" aria-label="线索提交信息">
        <div class="homeward-clue-meta-item">
          <span class="homeward-clue-meta-icon" aria-hidden="true"><RiUser3Line /></span>
          <div>
            <dt>提交人</dt>
            <dd>{{ clue.submitterName || '匿名访客' }}</dd>
          </div>
        </div>
        <div class="homeward-clue-meta-item">
          <span class="homeward-clue-meta-icon" aria-hidden="true"><RiPhoneLine /></span>
          <div>
            <dt>联系方式</dt>
            <dd class="is-contact">{{ clue.contact || '未填写' }}</dd>
          </div>
        </div>
        <div class="homeward-clue-meta-item">
          <span class="homeward-clue-meta-icon" aria-hidden="true"><RiMapPin2Line /></span>
          <div>
            <dt>发现地点</dt>
            <dd>{{ clue.location || '未填写' }}</dd>
          </div>
        </div>
        <div class="homeward-clue-meta-item">
          <span class="homeward-clue-meta-icon" aria-hidden="true"><RiTimeLine /></span>
          <div>
            <dt>提交时间</dt>
            <dd>
              <time :datetime="clue.submittedAt">{{ formatSubmittedAt(clue.submittedAt) }}</time>
            </dd>
          </div>
        </div>
      </dl>

      <section class="homeward-clue-content" aria-labelledby="homeward-clue-content-title">
        <header class="homeward-clue-section-heading">
          <span class="homeward-clue-section-icon" aria-hidden="true"><RiFileTextLine /></span>
          <div>
            <h3 id="homeward-clue-content-title">线索内容</h3>
            <p>访客提交的原始描述</p>
          </div>
        </header>
        <p class="homeward-clue-content-copy">{{ clue.content || '访客未填写具体内容' }}</p>
      </section>

      <section v-if="history.length" class="homeward-clue-history" aria-label="处理记录">
        <h3>处理记录</h3>
        <ol>
          <li v-for="(record, index) in history" :key="index">
            <div>
              <strong>{{ statusLabel(record.status) }}</strong>
              <span>{{ record.handler || '未记录处理人' }}</span>
              <time :datetime="record.processedAt">{{
                formatSubmittedAt(record.processedAt)
              }}</time>
              <small v-if="record.legacy">旧版保留记录</small>
            </div>
            <p>{{ record.processNote || '未填写备注' }}</p>
          </li>
        </ol>
      </section>

      <section class="homeward-clue-workflow" aria-labelledby="homeward-clue-workflow-title">
        <header class="homeward-clue-section-heading">
          <span class="homeward-clue-section-icon" aria-hidden="true"
            ><RiCheckboxCircleLine
          /></span>
          <div>
            <h3 id="homeward-clue-workflow-title">处理结论</h3>
            <p>确认当前状态，并留下核实依据</p>
          </div>
        </header>
        <div class="homeward-status-options" role="group" aria-label="线索处理状态">
          <button
            v-for="option in statusOptions"
            :key="option.value"
            :data-testid="`clue-status-${option.value.toLowerCase()}`"
            type="button"
            :class="{ 'is-active': form.status === option.value }"
            :aria-pressed="form.status === option.value"
            :disabled="saving"
            @click="form.status = option.value"
          >
            <span>{{ option.label }}</span>
            <small>{{ option.hint }}</small>
          </button>
        </div>
        <FormKit
          v-model="form.processNote"
          type="textarea"
          name="processNote"
          label="本次处理备注"
          help="每次保存会新增一条记录，处理人由当前登录账号自动记录；最多 2000 个字符"
          :maxlength="2000"
          :disabled="saving"
          outer-class="homeward-form-field homeward-textarea-field homeward-clue-note-field"
          :rows="4"
          :auto-height="true"
        />
      </section>

      <p v-if="displayedError" class="homeward-form-error" role="alert">{{ displayedError }}</p>
    </section>
    <template #footer>
      <VButton type="secondary" :disabled="saving" @click="close">取消</VButton>
      <VButton
        data-testid="save-clue"
        type="primary"
        :disabled="saving"
        :loading="saving"
        @click="submit"
      >
        {{ saving ? '保存中' : '保存处理结果' }}
      </VButton>
    </template>
  </VModal>
</template>

<style scoped>
.homeward-clue-history {
  border-top: 1px solid #e2e8f0;
  padding-top: 18px;
}
.homeward-clue-history h3 {
  font-weight: 600;
  margin-bottom: 12px;
}
.homeward-clue-history ol {
  display: grid;
  gap: 14px;
  list-style: none;
  margin: 0;
  padding: 0;
}
.homeward-clue-history li {
  border-left: 2px solid #cbd5e1;
  padding-left: 12px;
}
.homeward-clue-history li > div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  font-size: 12px;
  color: #64748b;
}
.homeward-clue-history strong {
  color: #334155;
}
.homeward-clue-history p {
  margin: 6px 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
