<script setup lang="ts">
import { MenuItems } from '@headlessui/vue'
import { cn } from '@/lib/utils'
import { useAttrs } from 'vue'

defineProps<{
  anchor?: string
  class?: string
}>()

const attrs = useAttrs()
</script>

<template>
  <transition
    enter-active-class="transition duration-100 ease-out"
    enter-from-class="opacity-0 scale-95"
    enter-to-class="opacity-100 scale-100"
    leave-active-class="transition duration-100 ease-in"
    leave-from-class="opacity-100 scale-100"
    leave-to-class="opacity-0 scale-95"
  >
    <MenuItems
      :class="
        cn(
          props.class,
          // Anchor positioning
          '[--anchor-gap:theme(spacing.2)] [--anchor-padding:theme(spacing.1)]',
          // Base styles
          'absolute right-0 z-10 mt-2 isolate w-max rounded-xl p-1',
          // Invisible border that is only visible in forced-colors mode for accessibility
          'outline outline-transparent focus:outline-none',
          // Handle scrolling when menu won't fit in viewport
          'overflow-y-auto',
          // Popover background
          'bg-white/75 backdrop-blur-xl dark:bg-zinc-800/75',
          // Shadows
          'shadow-lg ring-1 ring-zinc-950/10 dark:ring-white/10 dark:ring-inset',
          // Define grid at the menu level if subgrid is supported
          'supports-[grid-template-columns:subgrid]:grid supports-[grid-template-columns:subgrid]:grid-cols-[auto_1fr_1.5rem_0.5rem_auto]',
        )
      "
      v-bind="attrs"
    >
      <slot />
    </MenuItems>
  </transition>
</template>
