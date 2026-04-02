<script setup lang="ts">
import { MenuItem } from '@headlessui/vue'
import { cn } from '@/lib/utils'
import { useAttrs } from 'vue'

defineProps<{
  href?: string
  disabled?: boolean
  class?: string
}>()

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const attrs = useAttrs()

const itemClasses =
  cn(
    // Base styles
    'group cursor-default rounded-lg px-3.5 py-2.5 focus:outline-none sm:px-3 sm:py-1.5',
    // Text styles
    'text-left text-base/6 text-zinc-950 sm:text-sm/6 dark:text-white forced-colors:text-[CanvasText]',
    // Focus
    'data-[focus]:bg-blue-500 data-[focus]:text-white',
    // Disabled state
    'data-[disabled]:opacity-50',
    // Forced colors mode
    'forced-color-adjust-none forced-colors:data-[focus]:bg-[Highlight] forced-colors:data-[focus]:text-[HighlightText]',
    // Use subgrid when available
    'col-span-full grid grid-cols-[auto_1fr_1.5rem_0.5rem_auto] items-center supports-[grid-template-columns:subgrid]:grid-cols-subgrid',
    // Icons
    '[&>[data-slot=icon]]:col-start-1 [&>[data-slot=icon]]:row-start-1 [&>[data-slot=icon]]:mr-2.5 [&>[data-slot=icon]]:-ml-0.5 [&>[data-slot=icon]]:size-5 sm:[&>[data-slot=icon]]:mr-2 sm:[&>[data-slot=icon]]:size-4',
    '[&>[data-slot=icon]]:text-zinc-500 data-[focus]:[&>[data-slot=icon]]:text-white dark:[&>[data-slot=icon]]:text-zinc-400 dark:data-[focus]:[&>[data-slot=icon]]:text-white',
    // Avatar
    '[&>[data-slot=avatar]]:mr-2.5 [&>[data-slot=avatar]]:-ml-1 [&>[data-slot=avatar]]:size-6 sm:[&>[data-slot=avatar]]:mr-2 sm:[&>[data-slot=avatar]]:size-5',
  )
</script>

<template>
  <MenuItem v-slot="{ active, disabled: isDisabled }" :disabled="disabled">
    <a
      v-if="href"
      :href="href"
      :class="cn(itemClasses, props.class)"
      :data-focus="active ? '' : undefined"
      :data-disabled="isDisabled ? '' : undefined"
      v-bind="attrs"
    >
      <slot />
    </a>
    <button
      v-else
      type="button"
      :class="cn(itemClasses, props.class)"
      :data-focus="active ? '' : undefined"
      :data-disabled="isDisabled ? '' : undefined"
      v-bind="attrs"
      @click="emit('click', $event)"
    >
      <slot />
    </button>
  </MenuItem>
</template>
