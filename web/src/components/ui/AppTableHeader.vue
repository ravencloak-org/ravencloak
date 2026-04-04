<script setup lang="ts">
import { inject, useAttrs } from 'vue'
import { cn } from '@/lib/utils'
import { tableContextKey } from './AppTable.vue'
import type { TableContext } from './AppTable.vue'

const props = defineProps<{
  class?: string
}>()

const attrs = useAttrs()

const tableCtx = inject<TableContext>(tableContextKey, {
  bleed: false,
  dense: false,
  grid: false,
  striped: false,
})
</script>

<template>
  <th
    :class="
      cn(
        props.class,
        'border-b border-b-zinc-950/10 px-4 py-2 font-medium first:pl-[var(--gutter,theme(spacing.2))] last:pr-[var(--gutter,theme(spacing.2))] dark:border-b-white/10',
        tableCtx.grid && 'border-l border-l-zinc-950/5 first:border-l-0 dark:border-l-white/5',
        !tableCtx.bleed && 'sm:first:pl-1 sm:last:pr-1',
      )
    "
    v-bind="attrs"
  >
    <slot />
  </th>
</template>
