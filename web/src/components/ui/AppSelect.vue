<script setup lang="ts">
import { computed, useAttrs } from 'vue'
import { cn } from '@/lib/utils'

interface SelectOption {
  value: string | number
  label: string
}

const props = withDefaults(
  defineProps<{
    modelValue?: string | number
    options?: SelectOption[]
    placeholder?: string
    disabled?: boolean
    invalid?: boolean
    multiple?: boolean
    class?: string
  }>(),
  {
    placeholder: 'Select an option',
    multiple: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const attrs = useAttrs()

const wrapperClasses = computed(() =>
  cn(
    props.class,
    // Basic layout
    'group relative block w-full',
    // Background color + shadow applied to inset pseudo element, so shadow blends with border in light mode
    'before:absolute before:inset-px before:rounded-[calc(theme(borderRadius.lg)-1px)] before:bg-white before:shadow-sm',
    // Background color is moved to control and shadow is removed in dark mode so hide `before` pseudo
    'dark:before:hidden',
    // Focus ring
    'after:pointer-events-none after:absolute after:inset-0 after:rounded-lg after:ring-transparent after:ring-inset has-[:focus]:after:ring-2 has-[:focus]:after:ring-blue-500',
    // Disabled state
    'has-[:disabled]:opacity-50 has-[:disabled]:before:bg-zinc-950/5 has-[:disabled]:before:shadow-none',
  ),
)

const selectClasses = computed(() =>
  cn(
    // Basic layout
    'relative block w-full appearance-none rounded-lg py-[calc(theme(spacing[2.5])-1px)] sm:py-[calc(theme(spacing[1.5])-1px)]',
    // Horizontal padding
    props.multiple
      ? 'px-[calc(theme(spacing[3.5])-1px)] sm:px-[calc(theme(spacing.3)-1px)]'
      : 'pr-[calc(theme(spacing.10)-1px)] pl-[calc(theme(spacing[3.5])-1px)] sm:pr-[calc(theme(spacing.9)-1px)] sm:pl-[calc(theme(spacing.3)-1px)]',
    // Options (multi-select)
    '[&_optgroup]:font-semibold',
    // Typography
    'text-base/6 text-zinc-950 placeholder:text-zinc-500 sm:text-sm/6 dark:text-white dark:*:text-white',
    // Border
    'border border-zinc-950/10 hover:border-zinc-950/20 dark:border-white/10 dark:hover:border-white/20',
    // Background color
    'bg-transparent dark:bg-white/5 dark:*:bg-zinc-800',
    // Hide default focus styles
    'focus:outline-none',
    // Invalid state
    props.invalid && 'border-red-500 hover:border-red-500 dark:border-red-600 dark:hover:border-red-600',
    // Disabled state
    'disabled:border-zinc-950/20 disabled:opacity-100 dark:disabled:border-white/15 dark:disabled:bg-white/[2.5%] dark:hover:disabled:border-white/15',
  ),
)

function onChange(event: Event) {
  const target = event.target as HTMLSelectElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <span data-slot="control" :class="wrapperClasses">
    <select
      :value="modelValue"
      :disabled="disabled"
      :multiple="multiple"
      :data-invalid="invalid ? '' : undefined"
      :class="selectClasses"
      v-bind="attrs"
      @change="onChange"
    >
      <option
        v-if="placeholder && !multiple"
        value=""
        disabled
      >
        {{ placeholder }}
      </option>
      <slot>
        <option
          v-for="option in options"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </option>
      </slot>
    </select>
    <span v-if="!multiple" class="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2">
      <svg
        class="size-5 stroke-zinc-500 group-has-[:disabled]:stroke-zinc-600 sm:size-4 dark:stroke-zinc-400 forced-colors:stroke-[CanvasText]"
        viewBox="0 0 16 16"
        aria-hidden="true"
        fill="none"
      >
        <path d="M5.75 10.75L8 13L10.25 10.75" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
        <path d="M10.25 5.25L8 3L5.75 5.25" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </span>
  </span>
</template>
