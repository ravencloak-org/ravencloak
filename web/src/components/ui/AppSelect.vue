<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

interface SelectOption {
  value: string | number
  label: string
}

const props = withDefaults(
  defineProps<{
    label?: string
    error?: string
    modelValue?: string | number
    options: SelectOption[]
    placeholder?: string
    disabled?: boolean
  }>(),
  {
    placeholder: 'Select an option',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const selectClasses = computed(() =>
  cn(
    'block w-full rounded-lg border-0 px-3 py-2 text-sm',
    'bg-white text-zinc-900 shadow-sm',
    'ring-1 ring-inset',
    'focus:ring-2 focus:ring-inset',
    'dark:bg-zinc-900 dark:text-white',
    'disabled:opacity-50 disabled:cursor-not-allowed',
    props.error
      ? 'ring-red-500 focus:ring-red-500 dark:ring-red-500'
      : 'ring-zinc-300 focus:ring-primary-600 dark:ring-zinc-700 dark:focus:ring-primary-500',
  ),
)

function onChange(event: Event) {
  const target = event.target as HTMLSelectElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <div>
    <label
      v-if="label"
      class="block text-sm font-medium text-zinc-900 dark:text-zinc-100 mb-1.5"
    >
      {{ label }}
    </label>
    <select
      :value="modelValue"
      :disabled="disabled"
      :class="selectClasses"
      @change="onChange"
    >
      <option
        v-if="placeholder"
        value=""
        disabled
        selected
      >
        {{ placeholder }}
      </option>
      <option
        v-for="option in options"
        :key="option.value"
        :value="option.value"
      >
        {{ option.label }}
      </option>
    </select>
    <p
      v-if="error"
      class="mt-1.5 text-xs text-red-600 dark:text-red-400"
    >
      {{ error }}
    </p>
  </div>
</template>
