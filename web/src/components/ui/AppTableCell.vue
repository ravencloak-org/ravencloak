<script setup lang="ts">
import { inject, ref, useAttrs } from 'vue'
import { cn } from '@/lib/utils'
import { tableContextKey, tableRowContextKey } from './AppTable.vue'
import type { TableContext, TableRowContext } from './AppTable.vue'

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

const rowCtx = inject<TableRowContext>(tableRowContextKey, {
  href: undefined,
  target: undefined,
  title: undefined,
})

const cellRef = ref<HTMLElement | null>(null)
</script>

<template>
  <td
    :ref="rowCtx.href ? (el) => (cellRef = el as HTMLElement) : undefined"
    :class="
      cn(
        props.class,
        'relative px-4 first:pl-[var(--gutter,theme(spacing.2))] last:pr-[var(--gutter,theme(spacing.2))]',
        !tableCtx.striped && 'border-b border-zinc-950/5 dark:border-white/5',
        tableCtx.grid && 'border-l border-l-zinc-950/5 first:border-l-0 dark:border-l-white/5',
        tableCtx.dense ? 'py-2.5' : 'py-4',
        !tableCtx.bleed && 'sm:first:pl-1 sm:last:pr-1',
      )
    "
    v-bind="attrs"
  >
    <a
      v-if="rowCtx.href"
      data-row-link
      :href="rowCtx.href"
      :target="rowCtx.target"
      :aria-label="rowCtx.title"
      :tabindex="cellRef?.previousElementSibling === null ? 0 : -1"
      class="absolute inset-0 focus:outline-none"
    />
    <slot />
  </td>
</template>
