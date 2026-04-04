<script setup lang="ts">
import { inject, provide, useAttrs } from 'vue'
import { cn } from '@/lib/utils'
import { tableContextKey, tableRowContextKey } from './AppTable.vue'
import type { TableContext, TableRowContext } from './AppTable.vue'

const props = defineProps<{
  href?: string
  target?: string
  title?: string
  class?: string
}>()

const attrs = useAttrs()

const tableCtx = inject<TableContext>(tableContextKey, {
  bleed: false,
  dense: false,
  grid: false,
  striped: false,
})

provide<TableRowContext>(tableRowContextKey, {
  href: props.href,
  target: props.target,
  title: props.title,
})
</script>

<template>
  <tr
    :class="
      cn(
        props.class,
        href &&
          'has-[[data-row-link][data-focus]]:outline-2 has-[[data-row-link][data-focus]]:-outline-offset-2 has-[[data-row-link][data-focus]]:outline-blue-500 dark:focus-within:bg-white/[2.5%]',
        tableCtx.striped && 'even:bg-zinc-950/[2.5%] dark:even:bg-white/[2.5%]',
        href && tableCtx.striped && 'hover:bg-zinc-950/5 dark:hover:bg-white/5',
        href && !tableCtx.striped && 'hover:bg-zinc-950/[2.5%] dark:hover:bg-white/[2.5%]',
      )
    "
    v-bind="attrs"
  >
    <slot />
  </tr>
</template>
