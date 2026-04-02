<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  title: string
  description?: string
  icon?: Component
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center px-6 py-16 text-center">
    <component
      :is="icon"
      v-if="icon"
      class="h-12 w-12 text-zinc-400 dark:text-zinc-500 mb-4"
    />
    <h3 class="text-sm font-semibold text-zinc-900 dark:text-white">
      {{ title }}
    </h3>
    <p
      v-if="description"
      class="mt-1 text-sm text-zinc-500 dark:text-zinc-400 max-w-sm"
    >
      {{ description }}
    </p>
    <div
      v-if="$slots.default"
      class="mt-6"
    >
      <slot />
    </div>
  </div>
</template>
