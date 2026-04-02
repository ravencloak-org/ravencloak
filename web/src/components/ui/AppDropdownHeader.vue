<script setup lang="ts">
import { cn } from '@/lib/utils'
import { useAttrs } from 'vue'

defineProps<{
  class?: string
}>()

const attrs = useAttrs()
</script>

<template>
  <div :class="cn(props.class, 'col-span-5 px-3.5 pt-2.5 pb-1 sm:px-3')" v-bind="attrs">
    <slot />
  </div>
</template>
