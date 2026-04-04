<script setup lang="ts">
import { cn } from '@/lib/utils'
import { useAttrs } from 'vue'

const props = defineProps<{
  class?: string
}>()

const attrs = useAttrs()
</script>

<template>
  <div data-slot="label" :class="cn(props.class, 'col-start-2 row-start-1')" v-bind="attrs">
    <slot />
  </div>
</template>
