<script setup lang="ts">
import { computed } from 'vue'
import { VButton } from '@halo-dev/components'
import RiArrowRightLine from '~icons/ri/arrow-right-line'
import RiCheckboxCircleLine from '~icons/ri/checkbox-circle-line'
import RiFileList3Line from '~icons/ri/file-list-3-line'
import RiInboxArchiveLine from '~icons/ri/inbox-archive-line'
import RiRadarLine from '~icons/ri/radar-line'
import type { DashboardStats } from '@/type'

const props = defineProps<{ stats: DashboardStats }>()

const emit = defineEmits<{
  create: []
  navigate: [value: 'cases' | 'clues']
}>()

const publishRate = computed(() => {
  if (!props.stats.totalCases) return 0
  return Math.round((props.stats.publishedCases / props.stats.totalCases) * 100)
})

const metrics = computed(() => [
  {
    label: '案例档案',
    value: props.stats.totalCases,
    detail: '已录入全部寻亲档案',
    icon: RiFileList3Line,
    tone: 'ink',
    target: 'cases' as const,
  },
  {
    label: '公益展示中',
    value: props.stats.publishedCases,
    detail: `发布率 ${publishRate.value}%`,
    icon: RiRadarLine,
    tone: 'blue',
    target: 'cases' as const,
  },
  {
    label: '已经团圆',
    value: props.stats.foundCases,
    detail: '已确认找到失踪人员',
    icon: RiCheckboxCircleLine,
    tone: 'green',
    target: 'cases' as const,
  },
  {
    label: '待处理线索',
    value: props.stats.pendingClues,
    detail: props.stats.pendingClues ? '需要尽快核实跟进' : '当前没有积压线索',
    icon: RiInboxArchiveLine,
    tone: 'amber',
    target: 'clues' as const,
  },
])
</script>

<template>
  <section class="homeward-overview" aria-labelledby="overview-heading">
    <div class="homeward-overview-main">
      <div class="homeward-overview-heading">
        <div>
          <span class="homeward-eyebrow">工作台</span>
          <h2 id="overview-heading">把每一条信息，送到该去的地方</h2>
          <p>维护寻亲档案、核实访客线索，并确认公益页面展示状态。</p>
        </div>
        <VButton type="primary" @click="emit('create')">录入新案例</VButton>
      </div>

      <div class="homeward-metric-grid">
        <button
          v-for="metric in metrics"
          :key="metric.label"
          :data-testid="`metric-${metric.target}-${metric.tone}`"
          type="button"
          class="homeward-metric-card"
          @click="emit('navigate', metric.target)"
        >
          <div :class="['homeward-metric-icon', `is-${metric.tone}`]">
            <component :is="metric.icon" />
          </div>
          <div class="homeward-metric-copy">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.detail }}</small>
          </div>
          <RiArrowRightLine class="homeward-metric-arrow" />
        </button>
      </div>
    </div>

    <aside class="homeward-focus-card">
      <span class="homeward-eyebrow">今日重点</span>
      <h3>{{ stats.pendingClues ? `${stats.pendingClues} 条线索等待核实` : '线索队列已清空' }}</h3>
      <p>
        {{
          stats.pendingClues
            ? '优先核实联系方式和发现地点，处理过程记得写入备注。'
            : '可以继续补全案例资料，检查待发布档案。'
        }}
      </p>
      <button
        v-if="stats.pendingClues"
        type="button"
        class="homeward-text-action"
        @click="emit('navigate', 'clues')"
      >
        处理线索 <RiArrowRightLine />
      </button>
      <button v-else type="button" class="homeward-text-action" @click="emit('navigate', 'cases')">
        查看案例 <RiArrowRightLine />
      </button>
    </aside>
  </section>
</template>
