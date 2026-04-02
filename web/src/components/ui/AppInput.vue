<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    label?: string
    error?: string
    modelValue?: string | number
    type?: string
    placeholder?: string
    disabled?: boolean
  }>(),
  {
    type: 'text',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const inputClasses = computed(() =>
  cn(
    'block w-full rounded-lg border-0 px-3 py-2 text-sm',
    'bg-white text-zinc-900 shadow-sm',
    'ring-1 ring-inset placeholder:text-zinc-400',
    'focus:ring-2 focus:ring-inset',
    'dark:bg-zinc-900 dark:text-white dark:placeholder:text-zinc-500',
    'disabled:opacity-50 disabled:cursor-not-allowed',
    props.error
      ? 'ring-red-500 focus:ring-red-500 dark:ring-red-500'
      : 'ring-zinc-300 focus:ring-primary-600 dark:ring-zinc-700 dark:focus:ring-primary-500',
  ),
)

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
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
    <input
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :class="inputClasses"
      @input="onInput"
    />
    <p
      v-if="error"
      class="mt-1.5 text-xs text-red-600 dark:text-red-400"
    >
      {{ error }}
    </p>
  </div>
</template>
