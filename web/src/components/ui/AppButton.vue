<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'
type ButtonSize = 'sm' | 'md' | 'lg'

const props = withDefaults(
  defineProps<{
    variant?: ButtonVariant
    size?: ButtonSize
    loading?: boolean
    disabled?: boolean
    as?: string | object
  }>(),
  {
    variant: 'primary',
    size: 'md',
    as: 'button',
  },
)

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-primary-600 text-white hover:bg-primary-500 focus-visible:outline-primary-600 shadow-sm',
  secondary:
    'bg-white text-zinc-900 ring-1 ring-inset ring-zinc-300 hover:bg-zinc-50 dark:bg-zinc-800 dark:text-zinc-100 dark:ring-zinc-700 dark:hover:bg-zinc-700 shadow-sm',
  danger:
    'bg-red-600 text-white hover:bg-red-500 focus-visible:outline-red-600 shadow-sm',
  ghost:
    'bg-transparent text-zinc-700 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800',
}

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'px-2.5 py-1.5 text-xs gap-1.5',
  md: 'px-3 py-2 text-sm gap-2',
  lg: 'px-4 py-2.5 text-sm gap-2',
}

const classes = computed(() =>
  cn(
    'inline-flex items-center justify-center font-semibold rounded-lg',
    'focus-visible:outline-2 focus-visible:outline-offset-2',
    'transition-colors duration-150',
    'disabled:opacity-50 disabled:cursor-not-allowed',
    variantClasses[props.variant],
    sizeClasses[props.size],
  ),
)
</script>

<template>
  <component
    :is="as"
    :class="classes"
    :disabled="disabled || loading"
  >
    <svg
      v-if="loading"
      class="animate-spin -ml-0.5 h-4 w-4"
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
    >
      <circle
        class="opacity-25"
        cx="12"
        cy="12"
        r="10"
        stroke="currentColor"
        stroke-width="4"
      />
      <path
        class="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
      />
    </svg>
    <slot />
  </component>
</template>
