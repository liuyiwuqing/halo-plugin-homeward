<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { VButton, VModal, VPagination } from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'
import {
  listApplications,
  reviewApplication,
  type CaseApplication,
  type ApplicationStatus,
} from '@/api/applications'

const emit = defineEmits<{ created: [] }>()
const items = ref<CaseApplication[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const status = ref('PENDING')
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const reviewError = ref('')
const message = ref('')
const selected = ref<CaseApplication | null>(null)
const note = ref('')
let sequence = 0
const labels: Record<ApplicationStatus, string> = {
  PENDING: '待审核',
  CONVERTING: '待恢复草稿创建',
  ACCEPTED: '已转为草稿',
  REJECTED: '未采纳',
}
function problem(value: unknown) {
  const detail = (value as { response?: { data?: { detail?: string } } })?.response?.data?.detail
  return detail || (value instanceof Error ? value.message : '操作失败，请重试')
}
async function load() {
  const id = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await listApplications({
      page: page.value,
      size: size.value,
      ...(status.value ? { status: status.value } : {}),
    })
    if (id !== sequence) return
    const last = Math.max(1, Math.ceil(result.total / size.value))
    if (page.value > last) {
      page.value = last
      await load()
      return
    }
    items.value = result.items
    total.value = result.total
  } catch (value) {
    if (id === sequence) {
      error.value = problem(value)
      items.value = []
      total.value = 0
    }
  } finally {
    if (id === sequence) loading.value = false
  }
}
function select(item: CaseApplication) {
  selected.value = item
  note.value = item.spec.reviewNote || ''
  reviewError.value = ''
}
async function review(action: 'accept' | 'reject') {
  if (!selected.value || busy.value) return
  busy.value = true
  reviewError.value = ''
  try {
    await reviewApplication(selected.value.metadata.name, action, note.value)
    selected.value = null
    message.value =
      action === 'accept'
        ? '已创建草稿，请到寻亲案例核对内容、隐私和授权后发布。'
        : '申请已标记为未采纳。'
    if (action === 'accept') emit('created')
    await load()
  } catch (value) {
    reviewError.value = problem(value)
  } finally {
    busy.value = false
  }
}
function filter() {
  page.value = 1
  void load()
}
function paginate(value: { page: number; size: number }) {
  page.value = value.page
  size.value = value.size
  void load()
}
onMounted(load)
</script>
<template>
  <section class="homeward-content-panel application-panel" :aria-busy="loading">
    <header class="homeward-panel-header">
      <div>
        <h2>寻亲申请</h2>
        <p>先联系申请人核实资料与发布授权；转成草稿后仍须人工核对。</p>
      </div>
      <VButton @click="load">刷新</VButton>
    </header>
    <label
      >审核状态
      <select v-model="status" @change="filter">
        <option value="">全部</option>
        <option v-for="(label, value) in labels" :key="value" :value="value">{{ label }}</option>
      </select></label
    >
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在加载申请…</p>
    <template v-else-if="!error">
      <p>共 {{ total }} 条申请</p>
      <p v-if="!items.length">当前没有匹配的申请。</p>
      <ul class="application-list">
        <li v-for="item in items" :key="item.metadata.name">
          <div>
            <strong>{{ item.spec.personName }}</strong>
            <p>{{ item.spec.missingLocation }} · {{ labels[item.spec.status] }}</p>
            <small>{{ utils.date.format(item.spec.submittedAt, 'YYYY-MM-DD HH:mm') }}</small>
          </div>
          <VButton @click="select(item)">查看申请</VButton>
        </li>
      </ul>
      <VPagination
        v-if="total > 0"
        :page="page"
        :size="size"
        :total="total"
        :size-options="[10, 20, 50]"
        @change="paginate"
      />
    </template>
    <!-- Halo 2.25 的 VModal 由 v-if 挂载时需省略 visible，使用其挂载即显示模式。 -->
    <VModal
      v-if="selected"
      title="核实寻亲申请"
      :width="760"
      :layer-closable="false"
      @close="!busy && (selected = null)"
    >
      <div class="application-detail">
        <h3>{{ selected.spec.personName }}</h3>
        <p>{{ selected.spec.missingDate || '时间待补充' }} · {{ selected.spec.missingLocation }}</p>
        <p class="application-description">{{ selected.spec.description }}</p>
        <p v-if="selected.spec.sourceUrl">资料来源：{{ selected.spec.sourceUrl }}</p>
        <h4>私密联系信息</h4>
        <p>
          {{ selected.spec.applicantName }} · {{ selected.spec.relationship }} ·
          {{ selected.spec.contact }}
        </p>
        <p>资料授权确认：{{ utils.date.format(selected.spec.consentAt, 'YYYY-MM-DD HH:mm') }}</p>
        <p v-if="selected.spec.handler">处理人：{{ selected.spec.handler }}</p>
        <p v-if="selected.spec.draftName">已创建草稿，请到“寻亲案例”核对后发布。</p>
        <FormKit
          v-model="note"
          type="textarea"
          label="审核备注（仅后台可见）"
          :maxlength="2000"
          :disabled="busy || !['PENDING', 'CONVERTING'].includes(selected.spec.status)"
        />
        <p v-if="reviewError" role="alert">{{ reviewError }}</p>
      </div>
      <template #footer>
        <VButton :disabled="busy" @click="selected = null">关闭</VButton>
        <VButton
          v-if="selected.spec.status === 'PENDING'"
          v-permission="['plugin:homeward:console:manage']"
          :disabled="busy"
          @click="review('reject')"
          >未采纳</VButton
        >
        <VButton
          v-if="['PENDING', 'CONVERTING'].includes(selected.spec.status)"
          v-permission="['plugin:homeward:console:manage']"
          type="primary"
          :loading="busy"
          :disabled="busy"
          @click="review('accept')"
          >{{
            selected.spec.status === 'CONVERTING' ? '重试创建草稿' : '已核实，转为草稿'
          }}</VButton
        >
      </template>
    </VModal>
  </section>
</template>
<style scoped>
.application-panel > label,
.application-panel > p {
  margin: 16px 24px;
  display: block;
}
.application-panel select {
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 6px;
}
.application-list {
  list-style: none;
  padding: 0 24px;
}
.application-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  border-bottom: 1px solid #eee;
  padding: 20px 0;
}
.application-list p {
  margin: 8px 0;
}
.application-detail {
  padding: 20px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.application-description {
  white-space: pre-wrap;
}
</style>
