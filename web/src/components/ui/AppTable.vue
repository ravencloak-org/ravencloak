<script lang="ts">
import type { InjectionKey } from 'vue'

// --- Table Context ---
export interface TableContext {
  bleed: boolean
  dense: boolean
  grid: boolean
  striped: boolean
}

export const tableContextKey: InjectionKey<TableContext> = Symbol('tableContext')

// --- Table Row Context ---
export interface TableRowContext {
  href?: string
  target?: string
  title?: string
}

export const tableRowContextKey: InjectionKey<TableRowContext> = Symbol('tableRowContext')
</script>

<script setup lang="ts">
import { provide, useAttrs } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    bleed?: boolean
    dense?: boolean
    grid?: boolean
    striped?: boolean
    class?: string
  }>(),
  {
    bleed: false,
    dense: false,
    grid: false,
    striped: false,
  },
)

const attrs = useAttrs()

provide(tableContextKey, {
  bleed: props.bleed,
  dense: props.dense,
  grid: props.grid,
  striped: props.striped,
})
</script>

<template>
  <div class="flow-root">
    <div :class="cn(props.class, '-mx-[--gutter] overflow-x-auto whitespace-nowrap')" v-bind="attrs">
      <div :class="cn('inline-block min-w-full align-middle', !bleed && 'sm:px-[--gutter]')">
        <table class="min-w-full text-left text-sm/6 text-zinc-950 dark:text-white">
          <slot />
        </table>
      </div>
    </div>
  </div>
</template>
