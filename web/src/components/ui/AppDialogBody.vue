<script setup lang="ts">
import { cn } from '@/lib/utils'
import { useAttrs } from 'vue'

const props = defineProps<{
  class?: string
}>()

const attrs = useAttrs()
</script>

<template>
  <div :class="cn(props.class, 'mt-6')" v-bind="attrs">
    <slot />
  </div>
</template>
