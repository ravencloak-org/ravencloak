<script setup lang="ts">
import { Menu, MenuButton, MenuItems, MenuItem, MenuSeparator } from '@headlessui/vue'
import { cn } from '@/lib/utils'
import { useAttrs } from 'vue'

defineProps<{
  class?: string
}>()

const attrs = useAttrs()
</script>

<template>
  <Menu as="div" class="relative" v-bind="attrs">
    <slot />
  </Menu>
</template>
