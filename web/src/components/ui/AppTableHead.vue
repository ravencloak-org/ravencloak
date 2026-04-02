<script setup lang="ts">
import { useAttrs } from 'vue'
import { cn } from '@/lib/utils'

defineProps<{
  class?: string
}>()

const attrs = useAttrs()
</script>

<template>
  <thead :class="cn(props.class, 'text-zinc-500 dark:text-zinc-400')" v-bind="attrs">
    <slot />
  </thead>
</template>
