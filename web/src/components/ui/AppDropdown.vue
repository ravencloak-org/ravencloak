<script setup lang="ts">
import { Menu } from '@headlessui/vue'
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
