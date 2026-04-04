<script setup lang="ts">
import { DialogTitle } from '@headlessui/vue'
import { cn } from '@/lib/utils'
import { useAttrs } from 'vue'

defineProps<{
  class?: string
}>()

const attrs = useAttrs()
</script>

<template>
  <DialogTitle
    :class="cn(props.class, 'text-lg/6 font-semibold text-balance text-zinc-950 sm:text-base/6 dark:text-white')"
    v-bind="attrs"
  >
    <slot />
  </DialogTitle>
</template>
