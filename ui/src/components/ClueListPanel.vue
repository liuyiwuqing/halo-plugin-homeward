<script setup lang="ts">
import { computed } from 'vue'
import { VButton, VEmpty } from '@halo-dev/components'
import RiMapPin2Line from '~icons/ri/map-pin-2-line'
import RiSearchLine from '~icons/ri/search-line'
import type { ClueReportVO, ClueStatus } from '@/type'

type ClueFilter = 'ALL' | ClueStatus

const props = defineProps<{
  clues: ClueReportVO[]
  keyword: string
  statusFilter: ClueFilter
}>()

const emit = defineEmits<{
  'update:keyword': [value: string]
  'update:statusFilter': [value: ClueFilter]
  search: []
  open: [value: ClueReportVO]
  delete: [value: ClueReportVO]
}>()

const filters: Array<{ value: ClueFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待处理' },
  { value: 'CONTACTED', label: '已联系' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'INVALID', label: '无效' },
]

const labels: Record<ClueStatus, string> = {
  PENDING: '待处理',
  CONTACTED: '已联系',
  RESOLVED: '已解决',
  INVALID: '无效',
}

const filteredClues = computed(() =>
  props.statusFilter === 'ALL'
    ? props.clues
    : props.clues.filter((item) => (item.status || 'PENDING') === props.statusFilter),
)

function filterCount(filter: ClueFilter) {
  if (filter === 'ALL') return props.clues.length
  return props.clues.filter((item) => (item.status || 'PENDING') === filter).length
}

function updateKeyword(event: Event) {
  emit('update:keyword', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <section class="homeward-content-panel" aria-labelledby="clue-list-heading">
    <header class="homeward-panel-header">
      <div>
        <span class="homeward-eyebrow">访客线索</span>
        <h2 id="clue-list-heading">线索处理</h2>
        <p>先核实、再标记。敏感联系方式不要复制到公开区域。</p>
      </div>
      <span class="homeward-queue-count"
        >{{ clues.filter((item) => item.status === 'PENDING').length }} 条待处理</span
      >
    </header>

    <div class="homeward-list-toolbar">
      <div class="homeward-search-box" role="search">
        <RiSearchLine aria-hidden="true" />
        <input
          :value="keyword"
          aria-label="搜索案例、联系人或线索内容"
          placeholder="搜索案例、联系人或线索内容"
          type="search"
          @input="updateKeyword"
          @keydown.enter.prevent="emit('search')"
        />
        <button data-testid="clue-search-submit" type="button" @click="emit('search')">搜索</button>
      </div>
      <div class="homeward-filter-tabs" role="group" aria-label="按状态筛选线索">
        <button
          v-for="filter in filters"
          :key="filter.value"
          type="button"
          :class="{ 'is-active': statusFilter === filter.value }"
          @click="emit('update:statusFilter', filter.value)"
        >
          <span>{{ filter.label }}</span>
          <small>{{ filterCount(filter.value) }}</small>
        </button>
      </div>
    </div>

    <div v-if="filteredClues.length" class="homeward-clue-list">
      <article v-for="item in filteredClues" :key="item.name" class="homeward-clue-row">
        <div
          class="homeward-clue-state"
          :class="`is-${(item.status || 'PENDING').toLowerCase()}`"
        ></div>
        <div class="homeward-clue-summary">
          <div class="homeward-clue-titleline">
            <h3>{{ item.caseTitle || '未关联案例' }}</h3>
            <span
              :class="['homeward-status-badge', `is-${(item.status || 'PENDING').toLowerCase()}`]"
            >
              {{ labels[item.status || 'PENDING'] }}
            </span>
          </div>
          <p class="homeward-clue-contact">
            {{ item.submitterName || '匿名访客' }} · {{ item.contact || '未填写联系方式' }}
          </p>
          <p class="homeward-clue-copy">{{ item.content || '访客未填写具体内容' }}</p>
          <span class="homeward-clue-location"
            ><RiMapPin2Line /> {{ item.location || '位置未填写' }}</span
          >
        </div>
        <div class="homeward-row-actions">
          <VButton
            :data-testid="`open-clue-${item.name}`"
            size="sm"
            type="primary"
            @click="emit('open', item)"
          >
            查看并处理
          </VButton>
          <VButton size="sm" type="danger" @click="emit('delete', item)">删除</VButton>
        </div>
      </article>
    </div>
    <VEmpty
      v-else
      :title="statusFilter === 'ALL' ? '暂无线索' : '该状态下没有线索'"
      description="访客提交的线索会出现在这里。"
    />
  </section>
</template>
