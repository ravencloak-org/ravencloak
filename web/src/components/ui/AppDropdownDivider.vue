<script setup lang="ts">
import { MenuSeparator } from '@headlessui/vue'
import { cn } from '@/lib/utils'

defineProps<{
  class?: string
}>()
</script>

<template>
  <MenuSeparator
    :class="
      cn(
        props.class,
        'col-span-full mx-3.5 my-1 h-px border-0 bg-zinc-950/5 sm:mx-3 dark:bg-white/10 forced-colors:bg-[CanvasText]',
      )
    "
  />
</template>
