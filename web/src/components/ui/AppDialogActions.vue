<script setup lang="ts">
import { cn } from '@/lib/utils'
import { useAttrs } from 'vue'

defineProps<{
  class?: string
}>()

const attrs = useAttrs()
</script>

<template>
  <div
    :class="
      cn(
        props.class,
        'mt-8 flex flex-col-reverse items-center justify-end gap-3 *:w-full sm:flex-row sm:*:w-auto',
      )
    "
    v-bind="attrs"
  >
    <slot />
  </div>
</template>
