<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

type BadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

const props = withDefaults(
  defineProps<{
    variant?: BadgeVariant
    dot?: boolean
  }>(),
  {
    variant: 'neutral',
    dot: false,
  },
)

const variantClasses: Record<BadgeVariant, string> = {
  success: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20 dark:bg-emerald-500/10 dark:text-emerald-400 dark:ring-emerald-500/20',
  warning: 'bg-amber-50 text-amber-700 ring-amber-600/20 dark:bg-amber-500/10 dark:text-amber-400 dark:ring-amber-500/20',
  danger: 'bg-red-50 text-red-700 ring-red-600/20 dark:bg-red-500/10 dark:text-red-400 dark:ring-red-500/20',
  info: 'bg-blue-50 text-blue-700 ring-blue-600/20 dark:bg-blue-500/10 dark:text-blue-400 dark:ring-blue-500/20',
  neutral: 'bg-zinc-100 text-zinc-600 ring-zinc-500/20 dark:bg-zinc-400/10 dark:text-zinc-400 dark:ring-zinc-400/20',
}

const dotColorClasses: Record<BadgeVariant, string> = {
  success: 'bg-emerald-500',
  warning: 'bg-amber-500',
  danger: 'bg-red-500',
  info: 'bg-blue-500',
  neutral: 'bg-zinc-400',
}

const classes = computed(() =>
  cn(
    'inline-flex items-center gap-x-1.5 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset',
    variantClasses[props.variant],
  ),
)
</script>

<template>
  <span :class="classes">
    <span
      v-if="dot"
      :class="cn('h-1.5 w-1.5 rounded-full', dotColorClasses[variant])"
    />
    <slot />
  </span>
</template>
