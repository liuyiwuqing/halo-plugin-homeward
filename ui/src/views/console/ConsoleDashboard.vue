<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { VButton, VLoading } from '@halo-dev/components'
import RiAddLine from '~icons/ri/add-line'
import RiChatCheckLine from '~icons/ri/chat-check-line'
import RiDashboard3Line from '~icons/ri/dashboard-3-line'
import RiDownloadCloudLine from '~icons/ri/download-cloud-line'
import RiExternalLinkLine from '~icons/ri/external-link-line'
import RiFileUserLine from '~icons/ri/file-user-line'
import RiHeartPulseLine from '~icons/ri/heart-pulse-line'
import RiRefreshLine from '~icons/ri/refresh-line'
import RiWindowLine from '~icons/ri/window-line'
import {
  deleteCase,
  deleteClue,
  getDashboardStats,
  listCases,
  listClues,
  previewCaseImport,
  previewCaseImportBatch,
  previewCaseImportDocument,
  previewCaseImportDocuments,
  saveCase,
  updateCaseStatus,
  updateClueStatus,
} from '@/api'
import type {
  CaseImportBatchItem,
  CaseImportBatchPreview,
  CaseImportBatchRequest,
  CaseImportRequest,
  CaseStatus,
  ClueReportVO,
  ClueStatus,
  DashboardStats,
  MissingPersonCasePayload,
  MissingPersonCaseVO,
} from '@/type'
import CaseEditorModal from '@/components/CaseEditorModal.vue'
import CaseBatchImportModal from '@/components/CaseBatchImportModal.vue'
import CaseImportModal from '@/components/CaseImportModal.vue'
import ConfirmActionModal from '@/components/ConfirmActionModal.vue'
import ClueDetailModal from '@/components/ClueDetailModal.vue'
import DashboardOverview from '@/components/DashboardOverview.vue'
import CaseListPanel from '@/components/CaseListPanel.vue'
import ClueListPanel from '@/components/ClueListPanel.vue'
import CaseApplicationPanel from '@/components/CaseApplicationPanel.vue'

type TabKey = 'overview' | 'cases' | 'clues' | 'applications' | 'preview'

const activeTab = ref<TabKey>('overview')
const loadError = ref('')
const caseLoading = ref(false)
const clueLoading = ref(false)
const statsLoading = ref(false)
const loading = computed(() => caseLoading.value || clueLoading.value || statsLoading.value)
const caseLoadError = ref('')
const clueLoadError = ref('')
const statsLoadError = ref('')
const displayedLoadError = computed(
  () => loadError.value || caseLoadError.value || clueLoadError.value || statsLoadError.value,
)
const message = ref('')
const caseKeyword = ref('')
const clueKeyword = ref('')
const caseStatusFilter = ref<'ALL' | CaseStatus>('ALL')
const clueStatusFilter = ref<'ALL' | ClueStatus>('ALL')
const casePage = ref(1)
const cluePage = ref(1)
const caseSize = ref(30)
const clueSize = ref(30)
const caseTotal = ref(0)
const clueTotal = ref(0)
const caseQueryKeyword = ref('')
const clueQueryKeyword = ref('')
let caseRequestId = 0
let clueRequestId = 0
let statsRequestId = 0
const cases = ref<MissingPersonCaseVO[]>([])
const clues = ref<ClueReportVO[]>([])
const stats = ref<DashboardStats>({
  totalCases: 0,
  publishedCases: 0,
  foundCases: 0,
  pendingClues: 0,
})

const caseEditorVisible = ref(false)
const editingCase = ref<MissingPersonCaseVO | null>(null)
const caseSaving = ref(false)
const caseSaveError = ref('')
const caseImportVisible = ref(false)
const caseImporting = ref(false)
const caseImportError = ref('')
const caseImportAiSuggestion = ref('')
const caseImportWarnings = ref<string[]>([])
const caseBatchImportVisible = ref(false)
const caseBatchImporting = ref(false)
const caseBatchImportError = ref('')
const caseBatchImportResult = ref<CaseImportBatchPreview | null>(null)
const caseBatchReviewed = ref<number[]>([])
const batchResetConfirmation = ref(false)
const batchReviewingIndex = ref<number | null>(null)
const pendingDeleteCase = ref<MissingPersonCaseVO | null>(null)
const deletingCase = ref(false)
const caseActionName = ref('')
const selectedClue = ref<ClueReportVO | null>(null)
const clueSaving = ref(false)
const clueSaveError = ref('')
const pendingDeleteClue = ref<ClueReportVO | null>(null)
const deletingClue = ref(false)

const tabs = [
  { id: 'overview' as const, label: '概览', icon: RiDashboard3Line },
  { id: 'cases' as const, label: '寻亲案例', icon: RiFileUserLine },
  { id: 'clues' as const, label: '线索处理', icon: RiChatCheckLine },
  { id: 'applications' as const, label: '寻亲申请', icon: RiFileUserLine },
  { id: 'preview' as const, label: '公益页预览', icon: RiWindowLine },
]

function setMessage(value: string) {
  message.value = value
  window.setTimeout(() => {
    if (message.value === value) {
      message.value = ''
    }
  }, 2600)
}

async function refreshCases(deletedName?: string) {
  const requestId = ++caseRequestId
  caseLoading.value = true
  caseLoadError.value = ''
  try {
    const result = await listCases({
      page: casePage.value,
      size: caseSize.value,
      keyword: caseQueryKeyword.value || undefined,
      ...(caseStatusFilter.value !== 'ALL' ? { status: caseStatusFilter.value } : {}),
    })
    if (requestId !== caseRequestId) return
    const items = result.items || []
    // Halo 索引可能短暂返回已删除项；提交列表前排除，避免等待其他刷新请求时重新展示。
    const staleCount = deletedName ? items.filter((item) => item.name === deletedName).length : 0
    caseTotal.value = Math.max(0, result.total - staleCount)
    const lastPage = Math.max(1, Math.ceil(caseTotal.value / caseSize.value))
    if (casePage.value > lastPage) {
      casePage.value = lastPage
      await refreshCases(deletedName)
      return
    }
    cases.value = deletedName ? items.filter((item) => item.name !== deletedName) : items
  } catch (error) {
    if (requestId !== caseRequestId) return
    cases.value = []
    caseLoadError.value = error instanceof Error ? error.message : '案例加载失败，请重试'
  } finally {
    if (requestId === caseRequestId) caseLoading.value = false
  }
}

async function refreshClues(deletedName?: string) {
  const requestId = ++clueRequestId
  clueLoading.value = true
  clueLoadError.value = ''
  try {
    const result = await listClues({
      page: cluePage.value,
      size: clueSize.value,
      keyword: clueQueryKeyword.value || undefined,
      ...(clueStatusFilter.value !== 'ALL' ? { status: clueStatusFilter.value } : {}),
    })
    if (requestId !== clueRequestId) return
    const items = result.items || []
    const staleCount = deletedName ? items.filter((item) => item.name === deletedName).length : 0
    clueTotal.value = Math.max(0, result.total - staleCount)
    const lastPage = Math.max(1, Math.ceil(clueTotal.value / clueSize.value))
    if (cluePage.value > lastPage) {
      cluePage.value = lastPage
      await refreshClues(deletedName)
      return
    }
    clues.value = deletedName ? items.filter((item) => item.name !== deletedName) : items
  } catch (error) {
    if (requestId !== clueRequestId) return
    clues.value = []
    clueLoadError.value = error instanceof Error ? error.message : '线索加载失败，请重试'
  } finally {
    if (requestId === clueRequestId) clueLoading.value = false
  }
}

async function refreshStats() {
  const requestId = ++statsRequestId
  statsLoading.value = true
  statsLoadError.value = ''
  try {
    const result = await getDashboardStats()
    if (requestId === statsRequestId) stats.value = result
  } catch (error) {
    if (requestId === statsRequestId) {
      statsLoadError.value = error instanceof Error ? error.message : '统计加载失败，请重试'
    }
  } finally {
    if (requestId === statsRequestId) statsLoading.value = false
  }
}

async function refreshAll() {
  loadError.value = ''
  await Promise.all([refreshStats(), refreshCases(), refreshClues()])
}

function searchCases() {
  caseQueryKeyword.value = caseKeyword.value.trim()
  casePage.value = 1
  return refreshCases()
}

function searchClues() {
  clueQueryKeyword.value = clueKeyword.value.trim()
  cluePage.value = 1
  return refreshClues()
}

function filterCases(status: 'ALL' | CaseStatus) {
  caseStatusFilter.value = status
  casePage.value = 1
  return refreshCases()
}

function filterClues(status: 'ALL' | ClueStatus) {
  clueStatusFilter.value = status
  cluePage.value = 1
  return refreshClues()
}

function paginateCases(value: { page: number; size: number }) {
  casePage.value = value.size === caseSize.value ? value.page : 1
  caseSize.value = value.size
  return refreshCases()
}

function paginateClues(value: { page: number; size: number }) {
  cluePage.value = value.size === clueSize.value ? value.page : 1
  clueSize.value = value.size
  return refreshClues()
}

function navigateOverview(
  target:
    { tab: 'cases'; status: 'ALL' | CaseStatus } | { tab: 'clues'; status: 'ALL' | ClueStatus },
) {
  activeTab.value = target.tab
  if (target.tab === 'cases') {
    caseKeyword.value = ''
    caseQueryKeyword.value = ''
    return filterCases(target.status)
  }
  clueKeyword.value = ''
  clueQueryKeyword.value = ''
  return filterClues(target.status)
}

function editCase(item: MissingPersonCaseVO) {
  batchReviewingIndex.value = null
  editingCase.value = item
  caseImportWarnings.value = []
  caseSaveError.value = ''
  caseEditorVisible.value = true
}

function createCase() {
  batchReviewingIndex.value = null
  editingCase.value = null
  caseImportWarnings.value = []
  caseSaveError.value = ''
  caseEditorVisible.value = true
}

function openCaseImport() {
  caseImportError.value = ''
  caseImportAiSuggestion.value = ''
  caseImportVisible.value = true
}

function openCaseBatchImport() {
  caseBatchImportError.value = ''
  caseBatchImportVisible.value = true
}

function closeCaseImport() {
  if (caseImporting.value) return
  caseImportVisible.value = false
  caseImportAiSuggestion.value = ''
}

function closeCaseBatchImport() {
  if (caseBatchImporting.value) return
  caseBatchImportVisible.value = false
  caseBatchImportError.value = ''
}

function requestNewBatch() {
  const unsaved = caseBatchImportResult.value?.items.some(
    (item) => item.preview && !caseBatchReviewed.value.includes(item.index),
  )
  if (unsaved) batchResetConfirmation.value = true
  else startNewBatch()
}

function startNewBatch() {
  caseBatchImportResult.value = null
  caseBatchReviewed.value = []
  caseBatchImportError.value = ''
  batchReviewingIndex.value = null
  batchResetConfirmation.value = false
}

async function previewImport(request: CaseImportRequest) {
  caseImporting.value = true
  caseImportError.value = ''
  caseImportAiSuggestion.value = ''
  try {
    const preview = await previewCaseImport(request)
    if (!request.ai && preview.aiRecommended && preview.aiAvailable) {
      const count = preview.extractedFieldCount || 0
      caseImportAiSuggestion.value = count
        ? `规则解析只识别到 ${count} 个字段，建议使用 AI 智能解析后再核对。`
        : '规则解析未识别到有效寻亲字段，建议使用 AI 智能解析后再核对。'
      return
    }
    editingCase.value = {
      ...preview.caseData,
      status: 'DRAFT',
      priority: preview.caseData.priority ?? 0,
    }
    caseImportWarnings.value = preview.warnings?.length
      ? preview.warnings
      : ['自动识别结果可能存在遗漏，请逐项核对后再保存。']
    caseImportVisible.value = false
    caseEditorVisible.value = true
  } catch (error) {
    caseImportError.value =
      error instanceof Error ? error.message : '网页解析失败，请粘贴正文后重试'
  } finally {
    caseImporting.value = false
  }
}

async function previewImportDocument(file: File) {
  caseImporting.value = true
  caseImportError.value = ''
  try {
    const preview = await previewCaseImportDocument(file)
    editingCase.value = {
      ...preview.caseData,
      status: 'DRAFT',
      priority: preview.caseData.priority ?? 0,
    }
    caseImportWarnings.value = preview.warnings?.length
      ? preview.warnings
      : ['AI 自动识别结果可能存在遗漏，请逐项核对后再保存。']
    caseImportVisible.value = false
    caseEditorVisible.value = true
  } catch (error) {
    caseImportError.value = error instanceof Error ? error.message : '文档解析失败，请重试'
  } finally {
    caseImporting.value = false
  }
}

async function previewBatchImport(payload: CaseImportBatchRequest, files: File[]) {
  caseBatchImporting.value = true
  caseBatchImportError.value = ''
  try {
    const [textResult, documentResult] = await Promise.all([
      payload.items.length ? previewCaseImportBatch(payload) : Promise.resolve(null),
      files.length ? previewCaseImportDocuments(files) : Promise.resolve(null),
    ])
    const textItems = textResult?.items || []
    const documentItems =
      documentResult?.items.map((item) => ({ ...item, index: item.index + textItems.length })) || []
    const items = [...textItems, ...documentItems]
    caseBatchImportResult.value = {
      items,
      succeeded: items.filter((item) => item.preview).length,
      failed: items.filter((item) => !item.preview).length,
    }
  } catch (error) {
    caseBatchImportError.value = error instanceof Error ? error.message : '批量解析失败，请重试'
  } finally {
    caseBatchImporting.value = false
  }
}

function reviewBatchImport(item: CaseImportBatchItem) {
  if (!item.preview) return
  batchReviewingIndex.value = item.index
  editingCase.value = {
    ...item.preview.caseData,
    status: 'DRAFT',
    priority: item.preview.caseData.priority ?? 0,
  }
  caseImportWarnings.value = item.preview.warnings?.length
    ? item.preview.warnings
    : ['AI 自动识别结果可能存在遗漏，请逐项核对后再保存。']
  caseBatchImportVisible.value = false
  caseEditorVisible.value = true
}

function closeCaseEditor() {
  if (caseSaving.value) return
  caseEditorVisible.value = false
  if (batchReviewingIndex.value !== null) {
    batchReviewingIndex.value = null
    caseBatchImportVisible.value = true
  }
}

async function submitCase(formValue: MissingPersonCaseVO) {
  const savedBatchIndex = batchReviewingIndex.value
  const payload: MissingPersonCasePayload = {
    metadata: editingCase.value?.name ? { name: editingCase.value.name } : undefined,
    spec: { ...formValue },
  }
  caseSaving.value = true
  caseSaveError.value = ''
  try {
    await saveCase(payload)
    caseEditorVisible.value = false
    await refreshAll()
    if (savedBatchIndex !== null) {
      caseBatchReviewed.value = [...new Set([...caseBatchReviewed.value, savedBatchIndex])]
      batchReviewingIndex.value = null
      caseBatchImportVisible.value = true
    }
    setMessage(editingCase.value?.name ? '案例修改已保存' : '新案例已保存')
  } catch (error) {
    caseSaveError.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
  } finally {
    caseSaving.value = false
  }
}

async function changeCaseStatus(
  item: MissingPersonCaseVO,
  action: 'publish' | 'archive' | 'mark-found',
) {
  if (!item.name) return
  caseActionName.value = item.name
  try {
    await updateCaseStatus(item.name, action)
    await refreshAll()
    setMessage('案例状态已更新')
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '状态更新失败，请稍后重试'
  } finally {
    caseActionName.value = ''
  }
}

function requestCaseDelete(item: MissingPersonCaseVO) {
  pendingDeleteCase.value = item
}

async function confirmCaseDelete() {
  const name = pendingDeleteCase.value?.name
  if (!name) return
  deletingCase.value = true
  try {
    await deleteCase(name)
    pendingDeleteCase.value = null
    if (casePage.value > 1 && cases.value.length === 1 && cases.value[0]?.name === name) {
      casePage.value -= 1
    }
    loadError.value = ''
    await Promise.all([refreshStats(), refreshCases(name), refreshClues()])
    setMessage('案例已删除')
  } finally {
    deletingCase.value = false
  }
}

function openClue(item: ClueReportVO) {
  selectedClue.value = item
  clueSaveError.value = ''
}

async function saveClueUpdate(value: { status: ClueStatus; processNote?: string }) {
  if (!selectedClue.value?.name) return
  clueSaving.value = true
  clueSaveError.value = ''
  try {
    await updateClueStatus(selectedClue.value.name, value)
    selectedClue.value = null
    await refreshAll()
    setMessage('线索处理结果已保存')
  } catch (error) {
    clueSaveError.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
  } finally {
    clueSaving.value = false
  }
}

function requestClueDelete(item: ClueReportVO) {
  pendingDeleteClue.value = item
}

async function confirmClueDelete() {
  const name = pendingDeleteClue.value?.name
  if (!name) return
  deletingClue.value = true
  try {
    await deleteClue(name)
    pendingDeleteClue.value = null
    if (cluePage.value > 1 && clues.value.length === 1 && clues.value[0]?.name === name) {
      cluePage.value -= 1
    }
    loadError.value = ''
    await Promise.all([refreshStats(), refreshCases(), refreshClues(name)])
    setMessage('线索已删除')
  } finally {
    deletingClue.value = false
  }
}

onMounted(refreshAll)
</script>

<template>
  <main class="homeward-admin-shell">
    <div class="homeward-admin-shell-inner">
      <header class="homeward-admin-header">
        <div class="homeward-admin-brand">
          <span class="homeward-admin-brandmark"><RiHeartPulseLine /></span>
          <div>
            <span class="homeward-eyebrow">公益信息管理</span>
            <h1 class="homeward-admin-title">归航寻亲</h1>
            <p class="homeward-admin-subtitle">维护寻亲档案，核实每一条可能带人回家的线索</p>
          </div>
        </div>
        <div class="homeward-admin-header-actions">
          <VButton type="secondary" :disabled="loading" @click="refreshAll">
            <RiRefreshLine :class="{ 'is-spinning': loading }" /> 刷新
          </VButton>
          <VButton data-testid="import-case" type="secondary" @click="openCaseImport">
            <RiDownloadCloudLine /> 从网页导入
          </VButton>
          <VButton data-testid="batch-import-case" type="secondary" @click="openCaseBatchImport">
            <RiDownloadCloudLine /> 批量 AI 导入
          </VButton>
          <VButton data-testid="create-case" type="primary" @click="createCase">
            <RiAddLine /> 新增案例
          </VButton>
        </div>
      </header>

      <nav class="homeward-admin-tabs" aria-label="归航寻亲功能">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :data-testid="`tab-${tab.id}`"
          type="button"
          :class="{ 'is-active': activeTab === tab.id }"
          :aria-current="activeTab === tab.id ? 'page' : undefined"
          @click="activeTab = tab.id"
        >
          <component :is="tab.icon" aria-hidden="true" />
          <span class="homeward-tab-label">{{ tab.label }}</span>
          <span v-if="tab.id === 'cases'" class="homeward-tab-count">{{ stats.totalCases }}</span>
          <span v-if="tab.id === 'clues' && stats.pendingClues" class="homeward-tab-count is-alert">
            {{ stats.pendingClues }}
          </span>
        </button>
      </nav>

      <Transition name="homeward-toast">
        <p v-if="message" class="homeward-admin-message" role="status">{{ message }}</p>
      </Transition>
      <div v-if="displayedLoadError" class="homeward-error-banner" role="alert">
        <div>
          <strong>暂时无法完成操作</strong>
          <span>{{ displayedLoadError }}</span>
        </div>
        <button type="button" @click="refreshAll">重新加载</button>
      </div>
      <div v-if="loading && !cases.length && !clues.length" class="homeward-loading-state">
        <VLoading />
        <p>正在同步案例与线索…</p>
      </div>

      <Transition name="homeward-panel" mode="out-in">
        <div :key="activeTab" class="homeward-panel-stage">
          <DashboardOverview
            v-if="activeTab === 'overview'"
            :stats="stats"
            @create="createCase"
            @navigate="navigateOverview"
          />

          <CaseListPanel
            v-if="activeTab === 'cases'"
            v-model:keyword="caseKeyword"
            :status-filter="caseStatusFilter"
            :cases="cases"
            :page="casePage"
            :size="caseSize"
            :total="caseTotal"
            :loading="caseLoading"
            :error="caseLoadError"
            :action-name="caseActionName"
            @update:status-filter="filterCases"
            @search="searchCases"
            @paginate="paginateCases"
            @create="createCase"
            @edit="editCase"
            @action="changeCaseStatus"
            @delete="requestCaseDelete"
          />

          <CaseApplicationPanel v-if="activeTab === 'applications'" @created="refreshAll" />
          <ClueListPanel
            v-if="activeTab === 'clues'"
            v-model:keyword="clueKeyword"
            :status-filter="clueStatusFilter"
            :clues="clues"
            :page="cluePage"
            :size="clueSize"
            :total="clueTotal"
            :pending-count="stats.pendingClues"
            :loading="clueLoading"
            :error="clueLoadError"
            @update:status-filter="filterClues"
            @search="searchClues"
            @paginate="paginateClues"
            @open="openClue"
            @delete="requestClueDelete"
          />

          <section
            v-if="activeTab === 'preview'"
            class="homeward-content-panel homeward-preview-panel"
          >
            <div class="homeward-panel-header">
              <div>
                <span class="homeward-eyebrow">访客视角</span>
                <h2>公益页预览</h2>
                <p>预览访客在 404 页面进入归航寻亲公益页时看到的内容。</p>
              </div>
              <a class="homeward-open-preview" href="/homeward" target="_blank" rel="noopener">
                打开独立页面 <RiExternalLinkLine />
              </a>
            </div>
            <div class="homeward-admin-preview">
              <div class="homeward-preview-browserbar">
                <span></span><span></span><span></span>
                <strong>本站 / homeward</strong>
              </div>
              <iframe src="/homeward" title="归航寻亲公益页预览"></iframe>
            </div>
          </section>
        </div>
      </Transition>

      <CaseEditorModal
        :visible="caseEditorVisible"
        :case-data="editingCase"
        :saving="caseSaving"
        :error="caseSaveError"
        :review-warnings="caseImportWarnings"
        @close="closeCaseEditor"
        @save="submitCase"
      />
      <CaseImportModal
        :visible="caseImportVisible"
        :loading="caseImporting"
        :error="caseImportError"
        :suggest-ai="Boolean(caseImportAiSuggestion)"
        :suggestion="caseImportAiSuggestion"
        @close="closeCaseImport"
        @preview="previewImport"
        @preview-document="previewImportDocument"
      />
      <CaseBatchImportModal
        :visible="caseBatchImportVisible"
        :loading="caseBatchImporting"
        :error="caseBatchImportError"
        :result="caseBatchImportResult"
        :reviewed="caseBatchReviewed"
        @close="closeCaseBatchImport"
        @preview="previewBatchImport"
        @review="reviewBatchImport"
        @new-batch="requestNewBatch"
      />
      <ConfirmActionModal
        v-if="batchResetConfirmation"
        visible
        title="开始新批次"
        description="当前批次仍有未保存的解析结果。开始新批次后将清除这些结果，已保存的案例不受影响。"
        confirm-text="清除结果并继续"
        @close="batchResetConfirmation = false"
        @confirm="startNewBatch"
      />
      <ConfirmActionModal
        v-if="pendingDeleteCase"
        visible
        :title="`删除“${pendingDeleteCase.title || pendingDeleteCase.personName || '未命名案例'}”`"
        description="删除后无法恢复，公益页中的相关展示也会同步移除。"
        confirm-text="确认删除"
        :busy="deletingCase"
        @close="pendingDeleteCase = null"
        @confirm="confirmCaseDelete"
      />
      <ConfirmActionModal
        v-if="pendingDeleteClue"
        visible
        :title="`删除“${pendingDeleteClue.caseTitle || '未关联案例'}”的访客线索`"
        description="删除后将无法继续追踪这条线索，请确认它确实不需要保留。"
        confirm-text="确认删除"
        :busy="deletingClue"
        @close="pendingDeleteClue = null"
        @confirm="confirmClueDelete"
      />
      <ClueDetailModal
        v-if="selectedClue"
        visible
        :clue="selectedClue"
        :saving="clueSaving"
        :error="clueSaveError"
        @close="selectedClue = null"
        @save="saveClueUpdate"
      />
    </div>
  </main>
</template>
