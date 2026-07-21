<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { VButton, VEmpty, VLoading, VTabbar, VTag } from '@halo-dev/components'
import {
  deleteCase,
  deleteClue,
  getDashboardStats,
  listCases,
  listClues,
  saveCase,
  updateCaseStatus,
  updateClueStatus,
} from '@/api'
import type {
  CaseStatus,
  ClueReportVO,
  ClueStatus,
  DashboardStats,
  MissingPersonCasePayload,
  MissingPersonCaseVO,
} from '@/type'

type TabKey = 'overview' | 'cases' | 'clues' | 'preview'

const activeTab = ref<TabKey>('overview')
const loading = ref(false)
const message = ref('')
const caseKeyword = ref('')
const clueKeyword = ref('')
const cases = ref<MissingPersonCaseVO[]>([])
const clues = ref<ClueReportVO[]>([])
const stats = ref<DashboardStats>({
  totalCases: 0,
  publishedCases: 0,
  foundCases: 0,
  pendingClues: 0,
})

const caseForm = reactive<MissingPersonCaseVO>({
  status: 'DRAFT',
  priority: 0,
})

const editingCaseName = ref('')

const tabs: Array<{ id: TabKey; label: string }> = [
  { id: 'overview', label: '概览' },
  { id: 'cases', label: '寻亲案例' },
  { id: 'clues', label: '线索处理' },
  { id: 'preview', label: '404 预览' },
]

const statusLabels: Record<CaseStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  FOUND: '已找到',
  ARCHIVED: '已归档',
}

const clueStatusLabels: Record<ClueStatus, string> = {
  PENDING: '待处理',
  CONTACTED: '已联系',
  INVALID: '无效',
  RESOLVED: '已解决',
}

const statCards = computed(() => [
  { label: '全部案例', value: stats.value.totalCases },
  { label: '已发布', value: stats.value.publishedCases },
  { label: '已团圆', value: stats.value.foundCases },
  { label: '待处理线索', value: stats.value.pendingClues },
])

function setMessage(value: string) {
  message.value = value
  window.setTimeout(() => {
    if (message.value === value) {
      message.value = ''
    }
  }, 2600)
}

async function refreshAll() {
  loading.value = true
  try {
    const [statsResult, caseResult, clueResult] = await Promise.all([
      getDashboardStats(),
      listCases({ page: 1, size: 30, keyword: caseKeyword.value || undefined }),
      listClues({ page: 1, size: 30, keyword: clueKeyword.value || undefined }),
    ])
    stats.value = statsResult
    cases.value = caseResult.items || []
    clues.value = clueResult.items || []
  } finally {
    loading.value = false
  }
}

function editCase(item: MissingPersonCaseVO) {
  editingCaseName.value = item.name || ''
  Object.assign(caseForm, item)
  activeTab.value = 'cases'
}

function resetCaseForm() {
  editingCaseName.value = ''
  Object.assign(caseForm, {
    slug: '',
    title: '',
    personName: '',
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
}

async function submitCase() {
  const payload: MissingPersonCasePayload = {
    metadata: editingCaseName.value ? { name: editingCaseName.value } : undefined,
    spec: { ...caseForm },
  }
  await saveCase(payload)
  resetCaseForm()
  await refreshAll()
  setMessage('案例已保存')
}

async function changeCaseStatus(name: string | undefined, action: 'publish' | 'archive' | 'mark-found') {
  if (!name) return
  await updateCaseStatus(name, action)
  await refreshAll()
  setMessage('案例状态已更新')
}

async function removeCase(name: string | undefined) {
  if (!name) return
  await deleteCase(name)
  await refreshAll()
  setMessage('案例已删除')
}

async function changeClueStatus(item: ClueReportVO, status: ClueStatus) {
  if (!item.name) return
  await updateClueStatus(item.name, {
    status,
    handler: 'admin',
    processNote: item.processNote,
  })
  await refreshAll()
  setMessage('线索状态已更新')
}

async function removeClue(name: string | undefined) {
  if (!name) return
  await deleteClue(name)
  await refreshAll()
  setMessage('线索已删除')
}

onMounted(refreshAll)
</script>

<template>
  <main class="homeward-admin-shell">
    <div class="homeward-admin-shell-inner">
      <header class="homeward-admin-header">
        <div>
          <h1 class="homeward-admin-title">归航寻亲</h1>
          <p class="homeward-admin-subtitle">寻亲案例、公益展示和访客线索处理</p>
        </div>
        <VButton type="secondary" @click="refreshAll">刷新</VButton>
      </header>

      <VTabbar v-model:active-id="activeTab" :items="tabs" id-key="id" label-key="label" />

      <p class="homeward-admin-message">{{ message }}</p>
      <VLoading v-if="loading" />

      <section v-if="activeTab === 'overview'" class="homeward-admin-grid">
        <article v-for="item in statCards" :key="item.label" class="homeward-admin-stat">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </section>

      <section v-if="activeTab === 'cases'" class="homeward-admin-panel">
        <div class="homeward-admin-toolbar">
          <h2>寻亲案例</h2>
          <input v-model="caseKeyword" placeholder="搜索姓名、地点、标题" @keydown.enter="refreshAll" />
        </div>

        <form class="homeward-admin-form" @submit.prevent="submitCase">
          <label>姓名<input v-model="caseForm.personName" required /></label>
          <label>标题<input v-model="caseForm.title" /></label>
          <label>Slug<input v-model="caseForm.slug" placeholder="留空自动生成" /></label>
          <label>状态
            <select v-model="caseForm.status">
              <option value="DRAFT">草稿</option>
              <option value="PUBLISHED">已发布</option>
              <option value="FOUND">已找到</option>
              <option value="ARCHIVED">已归档</option>
            </select>
          </label>
          <label>优先级<input v-model.number="caseForm.priority" type="number" /></label>
          <label>失踪日期<input v-model="caseForm.missingDate" /></label>
          <label>失踪地点<input v-model="caseForm.missingLocation" /></label>
          <label>封面图<input v-model="caseForm.coverImage" /></label>
          <label class="is-wide">体貌特征<textarea v-model="caseForm.physicalFeatures" rows="3" /></label>
          <label class="is-wide">详细描述<textarea v-model="caseForm.description" rows="4" /></label>
          <label>联系人<input v-model="caseForm.contactName" /></label>
          <label>联系电话<input v-model="caseForm.contactPhone" /></label>
          <label>来源名称<input v-model="caseForm.sourceName" /></label>
          <label>来源链接<input v-model="caseForm.sourceUrl" /></label>
          <div class="homeward-admin-actions is-wide">
            <VButton type="primary" @click="submitCase">{{ editingCaseName ? '保存修改' : '新增案例' }}</VButton>
            <VButton type="secondary" @click="resetCaseForm">重置</VButton>
          </div>
        </form>

        <div v-if="cases.length" class="homeward-admin-list">
          <article v-for="item in cases" :key="item.name" class="homeward-admin-item">
            <div>
              <h3>{{ item.title || item.personName }}</h3>
              <p>{{ item.personName }} · {{ item.missingLocation || '地点待补充' }}</p>
              <p>{{ item.slug }}</p>
            </div>
            <div class="homeward-admin-actions">
              <VTag>{{ statusLabels[item.status || 'DRAFT'] }}</VTag>
              <VButton size="sm" @click="editCase(item)">编辑</VButton>
              <VButton size="sm" type="secondary" @click="changeCaseStatus(item.name, 'publish')">发布</VButton>
              <VButton size="sm" type="secondary" @click="changeCaseStatus(item.name, 'mark-found')">已找到</VButton>
              <VButton size="sm" type="danger" @click="removeCase(item.name)">删除</VButton>
            </div>
          </article>
        </div>
        <VEmpty v-else title="暂无寻亲案例" description="新增案例后，404 公益展示才有内容。" />
      </section>

      <section v-if="activeTab === 'clues'" class="homeward-admin-panel">
        <div class="homeward-admin-toolbar">
          <h2>线索处理</h2>
          <input v-model="clueKeyword" placeholder="搜索案例、联系人、线索内容" @keydown.enter="refreshAll" />
        </div>

        <div v-if="clues.length" class="homeward-admin-list">
          <article v-for="item in clues" :key="item.name" class="homeward-admin-item">
            <div>
              <h3>{{ item.caseTitle }}</h3>
              <p>{{ item.submitterName || '匿名访客' }} · {{ item.contact }}</p>
              <p>{{ item.content }}</p>
              <p>{{ item.location || '未填写位置' }}</p>
              <textarea
                v-model="item.processNote"
                class="homeward-admin-note"
                rows="3"
                placeholder="处理备注"
              />
            </div>
            <div class="homeward-admin-actions">
              <VTag>{{ clueStatusLabels[item.status || 'PENDING'] }}</VTag>
              <VButton size="sm" type="secondary" @click="changeClueStatus(item, 'CONTACTED')">已联系</VButton>
              <VButton size="sm" type="secondary" @click="changeClueStatus(item, 'RESOLVED')">已解决</VButton>
              <VButton size="sm" type="danger" @click="changeClueStatus(item, 'INVALID')">无效</VButton>
              <VButton size="sm" type="danger" @click="removeClue(item.name)">删除</VButton>
            </div>
          </article>
        </div>
        <VEmpty v-else title="暂无线索" description="访客提交线索后会出现在这里。" />
      </section>

      <section v-if="activeTab === 'preview'" class="homeward-admin-panel">
        <div class="homeward-admin-toolbar">
          <h2>404 展示预览</h2>
          <a href="/homeward" target="_blank">打开公益页</a>
        </div>
        <div class="homeward-admin-preview">
          <iframe src="/homeward" title="归航寻亲公益页预览"></iframe>
        </div>
      </section>
    </div>
  </main>
</template>
