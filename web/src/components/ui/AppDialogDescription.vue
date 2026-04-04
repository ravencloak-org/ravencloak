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
    data-slot="text"
    :class="cn(props.class, 'mt-2 text-pretty text-base/6 text-zinc-500 sm:text-sm/6 dark:text-zinc-400')"
    v-bind="attrs"
  >
    <slot />
  </p>
</template>
