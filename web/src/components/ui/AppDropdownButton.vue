<script setup lang="ts">
import { MenuButton } from '@headlessui/vue'
import { useAttrs } from 'vue'

defineProps<{
  class?: string
}>()

const attrs = useAttrs()
</script>

<template>
  <MenuButton as="template" v-bind="attrs">
    <slot />
  </MenuButton>
</template>
