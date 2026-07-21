<script setup lang="ts">
import { VButton, VModal } from '@halo-dev/components'

withDefaults(
  defineProps<{
    visible: boolean
    title: string
    description: string
    confirmText?: string
    busy?: boolean
  }>(),
  { confirmText: '确认', busy: false },
)

const emit = defineEmits<{
  close: []
  confirm: []
}>()
</script>

<template>
  <VModal
    :width="460"
    title="确认操作"
    :layer-closable="false"
    @close="emit('close')"
  >
    <section class="homeward-confirm" role="alertdialog" aria-modal="true">
      <div class="homeward-confirm-mark" aria-hidden="true">!</div>
      <div>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
      </div>
    </section>
    <template #footer>
      <VButton type="secondary" :disabled="busy" @click="emit('close')">取消</VButton>
      <VButton
        data-testid="confirm-action"
        type="danger"
        :disabled="busy"
        :loading="busy"
        @click="emit('confirm')"
      >
        {{ busy ? '处理中' : confirmText }}
      </VButton>
    </template>
  </VModal>
</template>
