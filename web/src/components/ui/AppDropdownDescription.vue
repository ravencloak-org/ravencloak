<script setup lang="ts">
import { cn } from '@/lib/utils'
import { useAttrs } from 'vue'

const props = defineProps<{
  class?: string
}>()

const attrs = useAttrs()
</script>

<template>
  <p
    data-slot="description"
    :class="
      cn(
        props.class,
        'col-span-2 col-start-2 row-start-2 text-sm/5 text-zinc-500 group-data-[focus]:text-white sm:text-xs/5 dark:text-zinc-400 forced-colors:group-data-[focus]:text-[HighlightText]',
      )
    "
    v-bind="attrs"
  >
    <slot />
  </p>
</template>
