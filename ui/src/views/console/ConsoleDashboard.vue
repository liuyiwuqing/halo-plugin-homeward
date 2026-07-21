<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { VButton, VLoading } from '@halo-dev/components'
import RiAddLine from '~icons/ri/add-line'
import RiChatCheckLine from '~icons/ri/chat-check-line'
import RiDashboard3Line from '~icons/ri/dashboard-3-line'
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
import CaseEditorModal from '@/components/CaseEditorModal.vue'
import ConfirmActionModal from '@/components/ConfirmActionModal.vue'
import ClueDetailModal from '@/components/ClueDetailModal.vue'
import DashboardOverview from '@/components/DashboardOverview.vue'
import CaseListPanel from '@/components/CaseListPanel.vue'
import ClueListPanel from '@/components/ClueListPanel.vue'

type TabKey = 'overview' | 'cases' | 'clues' | 'preview'

const activeTab = ref<TabKey>('overview')
const loading = ref(false)
const loadError = ref('')
const message = ref('')
const caseKeyword = ref('')
const clueKeyword = ref('')
const caseStatusFilter = ref<'ALL' | CaseStatus>('ALL')
const clueStatusFilter = ref<'ALL' | ClueStatus>('ALL')
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

async function refreshAll() {
  loading.value = true
  loadError.value = ''
  try {
    const [statsResult, caseResult, clueResult] = await Promise.all([
      getDashboardStats(),
      listCases({ page: 1, size: 30, keyword: caseKeyword.value || undefined }),
      listClues({ page: 1, size: 30, keyword: clueKeyword.value || undefined }),
    ])
    stats.value = statsResult
    cases.value = caseResult.items || []
    clues.value = clueResult.items || []
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '数据加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function editCase(item: MissingPersonCaseVO) {
  editingCase.value = item
  caseSaveError.value = ''
  caseEditorVisible.value = true
}

function createCase() {
  editingCase.value = null
  caseSaveError.value = ''
  caseEditorVisible.value = true
}

function closeCaseEditor() {
  if (caseSaving.value) return
  caseEditorVisible.value = false
}

async function submitCase(formValue: MissingPersonCaseVO) {
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
    await refreshAll()
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
    await updateClueStatus(selectedClue.value.name, {
      ...value,
      handler: 'admin',
    })
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
    await refreshAll()
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
      <div v-if="loadError" class="homeward-error-banner" role="alert">
        <div>
          <strong>暂时无法完成操作</strong>
          <span>{{ loadError }}</span>
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
            @navigate="activeTab = $event"
          />

          <CaseListPanel
            v-if="activeTab === 'cases'"
            v-model:keyword="caseKeyword"
            v-model:status-filter="caseStatusFilter"
            :cases="cases"
            :action-name="caseActionName"
            @search="refreshAll"
            @create="createCase"
            @edit="editCase"
            @action="changeCaseStatus"
            @delete="requestCaseDelete"
          />

          <ClueListPanel
            v-if="activeTab === 'clues'"
            v-model:keyword="clueKeyword"
            v-model:status-filter="clueStatusFilter"
            :clues="clues"
            @search="refreshAll"
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
        @close="closeCaseEditor"
        @save="submitCase"
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
