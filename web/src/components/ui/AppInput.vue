<script setup lang="ts">
import { computed, useAttrs } from 'vue'
import { cn } from '@/lib/utils'

const dateTypes = ['date', 'datetime-local', 'month', 'time', 'week']

const props = withDefaults(
  defineProps<{
    modelValue?: string | number
    type?: 'email' | 'number' | 'password' | 'search' | 'tel' | 'text' | 'url' | 'date' | 'datetime-local' | 'month' | 'time' | 'week'
    placeholder?: string
    disabled?: boolean
    invalid?: boolean
    class?: string
  }>(),
  {
    type: 'text',
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
    'relative block w-full',
    // Background color + shadow applied to inset pseudo element, so shadow blends with border in light mode
    'before:absolute before:inset-px before:rounded-[calc(theme(borderRadius.lg)-1px)] before:bg-white before:shadow-sm',
    // Background color is moved to control and shadow is removed in dark mode so hide `before` pseudo
    'dark:before:hidden',
    // Focus ring
    'after:pointer-events-none after:absolute after:inset-0 after:rounded-lg after:ring-transparent after:ring-inset sm:after:has-[:focus]:ring-2 sm:after:has-[:focus]:ring-blue-500',
    // Disabled state
    'has-[:disabled]:opacity-50 has-[:disabled]:before:bg-zinc-950/5 has-[:disabled]:before:shadow-none',
  ),
)

const inputClasses = computed(() =>
  cn(
    // Date classes
    props.type && dateTypes.includes(props.type) && [
      '[&::-webkit-datetime-edit-fields-wrapper]:p-0',
      '[&::-webkit-date-and-time-value]:min-h-[1.5em]',
      '[&::-webkit-datetime-edit]:inline-flex',
      '[&::-webkit-datetime-edit]:p-0',
      '[&::-webkit-datetime-edit-year-field]:p-0',
      '[&::-webkit-datetime-edit-month-field]:p-0',
      '[&::-webkit-datetime-edit-day-field]:p-0',
      '[&::-webkit-datetime-edit-hour-field]:p-0',
      '[&::-webkit-datetime-edit-minute-field]:p-0',
      '[&::-webkit-datetime-edit-second-field]:p-0',
      '[&::-webkit-datetime-edit-millisecond-field]:p-0',
      '[&::-webkit-datetime-edit-meridiem-field]:p-0',
    ],
    // Basic layout
    'relative block w-full appearance-none rounded-lg px-[calc(theme(spacing[3.5])-1px)] py-[calc(theme(spacing[2.5])-1px)] sm:px-[calc(theme(spacing.3)-1px)] sm:py-[calc(theme(spacing[1.5])-1px)]',
    // Typography
    'text-base/6 text-zinc-950 placeholder:text-zinc-500 sm:text-sm/6 dark:text-white',
    // Border
    'border border-zinc-950/10 hover:border-zinc-950/20 dark:border-white/10 dark:hover:border-white/20',
    // Background color
    'bg-transparent dark:bg-white/5',
    // Hide default focus styles
    'focus:outline-none',
    // Invalid state
    props.invalid && 'border-red-500 hover:border-red-500 dark:border-red-600 dark:hover:border-red-600',
    // Disabled state
    'disabled:border-zinc-950/20 dark:disabled:border-white/15 dark:disabled:bg-white/[2.5%] dark:hover:disabled:border-white/15',
    // System icons
    'dark:[color-scheme:dark]',
  ),
)

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <span data-slot="control" :class="wrapperClasses">
    <input
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :data-invalid="invalid ? '' : undefined"
      :class="inputClasses"
      v-bind="attrs"
      @input="onInput"
    />
  </span>
</template>
