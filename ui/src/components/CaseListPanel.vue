<script setup lang="ts">
import { VButton, VEmpty, VLoading, VPagination } from '@halo-dev/components'
import RiCalendarEventLine from '~icons/ri/calendar-event-line'
import RiMapPin2Line from '~icons/ri/map-pin-2-line'
import RiSearchLine from '~icons/ri/search-line'
import type { CaseStatus, MissingPersonCaseVO } from '@/type'

type CaseFilter = 'ALL' | CaseStatus

defineProps<{
  cases: MissingPersonCaseVO[]
  page: number
  size: number
  total: number
  loading?: boolean
  error?: string
  keyword: string
  statusFilter: CaseFilter
  actionName?: string
}>()

const emit = defineEmits<{
  'update:keyword': [value: string]
  'update:statusFilter': [value: CaseFilter]
  search: []
  paginate: [value: { page: number; size: number }]
  create: []
  edit: [value: MissingPersonCaseVO]
  action: [value: MissingPersonCaseVO, action: 'publish' | 'archive' | 'mark-found']
  delete: [value: MissingPersonCaseVO]
}>()

const filters: Array<{ value: CaseFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '展示中' },
  { value: 'FOUND', label: '已找到' },
  { value: 'ARCHIVED', label: '已归档' },
]

const statusLabels: Record<CaseStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '展示中',
  FOUND: '已找到',
  ARCHIVED: '已归档',
}

function fallbackName(item: MissingPersonCaseVO) {
  return (item.personName || item.title || '寻').slice(0, 1)
}

function updateKeyword(event: Event) {
  emit('update:keyword', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <section class="homeward-content-panel" aria-labelledby="case-list-heading" :aria-busy="loading">
    <header class="homeward-panel-header">
      <div>
        <span class="homeward-eyebrow">案例档案</span>
        <h2 id="case-list-heading">寻亲案例</h2>
        <p>发布前请确认隐私授权和信息来源。</p>
      </div>
      <span v-if="!error" class="homeward-panel-count">{{
        loading ? '正在加载…' : `共 ${total} 条匹配结果`
      }}</span>
    </header>

    <div class="homeward-list-toolbar">
      <div class="homeward-search-box" role="search">
        <RiSearchLine aria-hidden="true" />
        <input
          :value="keyword"
          aria-label="搜索姓名、地点或标题"
          placeholder="搜索姓名、地点或标题"
          type="search"
          @input="updateKeyword"
          @keydown.enter.prevent="emit('search')"
        />
        <button data-testid="case-search-submit" type="button" @click="emit('search')">搜索</button>
      </div>
      <div class="homeward-filter-tabs" role="group" aria-label="按状态筛选案例">
        <button
          v-for="filter in filters"
          :key="filter.value"
          :data-testid="`case-filter-${filter.value}`"
          type="button"
          :class="{ 'is-active': statusFilter === filter.value }"
          :aria-pressed="statusFilter === filter.value"
          @click="emit('update:statusFilter', filter.value)"
        >
          <span>{{ filter.label }}</span>
        </button>
      </div>
    </div>

    <div v-if="loading" class="homeward-loading-state" role="status">
      <VLoading />
      <p>正在加载案例…</p>
    </div>
    <div v-else-if="cases.length" class="homeward-case-list">
      <article v-for="item in cases" :key="item.name" class="homeward-case-row">
        <div class="homeward-case-cover">
          <img
            v-if="item.coverImage"
            :src="item.coverImage"
            :alt="`${item.personName || '寻亲案例'}的案例封面`"
          />
          <span v-else>{{ fallbackName(item) }}</span>
        </div>
        <div class="homeward-case-summary">
          <div class="homeward-case-titleline">
            <h3>{{ item.title || item.personName || '未命名案例' }}</h3>
            <span
              :class="['homeward-status-badge', `is-${(item.status || 'DRAFT').toLowerCase()}`]"
            >
              {{ statusLabels[item.status || 'DRAFT'] }}
            </span>
          </div>
          <p class="homeward-case-person">{{ item.personName || '姓名待补充' }}</p>
          <div class="homeward-case-facts">
            <span><RiMapPin2Line /> {{ item.missingLocation || '失踪地点待补充' }}</span>
            <span><RiCalendarEventLine /> {{ item.missingDate || '失踪日期待补充' }}</span>
          </div>
          <p class="homeward-case-description">
            {{ item.physicalFeatures || item.description || '尚未补充体貌特征和详细描述。' }}
          </p>
        </div>
        <div class="homeward-row-actions">
          <VButton size="sm" @click="emit('edit', item)">编辑资料</VButton>
          <VButton
            v-if="item.status !== 'PUBLISHED'"
            size="sm"
            type="secondary"
            :disabled="actionName === item.name"
            @click="emit('action', item, 'publish')"
          >
            发布
          </VButton>
          <VButton
            v-if="item.status === 'PUBLISHED'"
            size="sm"
            type="secondary"
            :disabled="actionName === item.name"
            @click="emit('action', item, 'mark-found')"
          >
            标记已找到
          </VButton>
          <VButton
            v-if="item.status === 'PUBLISHED' || item.status === 'FOUND'"
            size="sm"
            type="secondary"
            :disabled="actionName === item.name"
            @click="emit('action', item, 'archive')"
          >
            归档
          </VButton>
          <VButton
            :data-testid="`delete-case-${item.name}`"
            size="sm"
            type="danger"
            @click="emit('delete', item)"
          >
            删除
          </VButton>
        </div>
      </article>
    </div>
    <VEmpty
      v-else-if="!error"
      :title="
        keyword
          ? '没有找到匹配的案例'
          : statusFilter === 'ALL'
            ? '暂无寻亲案例'
            : '该状态下没有案例'
      "
      description="录入完整资料后，可以发布到公益展示页面。"
    >
      <template #actions>
        <VButton type="primary" @click="emit('create')">录入第一份案例</VButton>
      </template>
    </VEmpty>
    <VPagination
      v-if="!error"
      data-testid="case-pagination"
      :page="page"
      :size="size"
      :total="total"
      :size-options="[10, 30, 50]"
      @change="emit('paginate', $event)"
    />
  </section>
</template>
