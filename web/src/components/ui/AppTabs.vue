<script setup lang="ts">
import { TabGroup, TabList, Tab, TabPanels } from '@headlessui/vue'
import { cn } from '@/lib/utils'

defineProps<{
  tabs: string[]
}>()

const emit = defineEmits<{
  change: [index: number]
}>()
</script>

<template>
  <TabGroup @change="(index: number) => emit('change', index)">
    <TabList class="flex border-b border-zinc-200 dark:border-zinc-800">
      <Tab
        v-for="tab in tabs"
        :key="tab"
        v-slot="{ selected }"
        as="template"
      >
        <button
          :class="cn(
            'px-4 py-2.5 text-sm font-semibold outline-none whitespace-nowrap',
            'border-b-2 -mb-px transition-colors',
            selected
              ? 'border-primary-600 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-zinc-500 hover:text-zinc-700 hover:border-zinc-300 dark:text-zinc-400 dark:hover:text-zinc-300 dark:hover:border-zinc-600',
          )"
        >
          {{ tab }}
        </button>
      </Tab>
    </TabList>
    <TabPanels class="mt-4">
      <slot />
    </TabPanels>
  </TabGroup>
</template>
