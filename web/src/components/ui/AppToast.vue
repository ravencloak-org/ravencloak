<script setup lang="ts">
import { useToast, type Toast } from '@/composables/useToast'
import { XMarkIcon } from '@heroicons/vue/20/solid'
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
  XCircleIcon,
  InformationCircleIcon,
} from '@heroicons/vue/24/outline'
import { type Component } from 'vue'
import { cn } from '@/lib/utils'

const { toasts, remove } = useToast()

const iconMap: Record<Toast['type'], Component> = {
  success: CheckCircleIcon,
  error: XCircleIcon,
  warning: ExclamationTriangleIcon,
  info: InformationCircleIcon,
}

const borderColorMap: Record<Toast['type'], string> = {
  success: 'border-l-emerald-500',
  error: 'border-l-red-500',
  warning: 'border-l-amber-500',
  info: 'border-l-blue-500',
}

const iconColorMap: Record<Toast['type'], string> = {
  success: 'text-emerald-500',
  error: 'text-red-500',
  warning: 'text-amber-500',
  info: 'text-blue-500',
}
</script>

<template>
  <div
    aria-live="assertive"
    class="pointer-events-none fixed inset-0 z-[100] flex flex-col items-end gap-3 px-4 py-6 sm:p-6"
  >
    <TransitionGroup
      enter-active-class="transition ease-out duration-300"
      enter-from-class="opacity-0 translate-x-8"
      enter-to-class="opacity-100 translate-x-0"
      leave-active-class="transition ease-in duration-200"
      leave-from-class="opacity-100 translate-x-0"
      leave-to-class="opacity-0 translate-x-8"
      move-class="transition-all duration-300"
    >
      <div
        v-for="toast in toasts"
        :key="toast.id"
        :class="cn(
          'pointer-events-auto w-full max-w-sm rounded-lg bg-white shadow-lg ring-1 ring-zinc-950/5',
          'border-l-4',
          'dark:bg-zinc-900 dark:ring-white/10',
          borderColorMap[toast.type],
        )"
      >
        <div class="flex items-start gap-3 p-4">
          <component
            :is="iconMap[toast.type]"
            :class="cn('h-5 w-5 shrink-0 mt-0.5', iconColorMap[toast.type])"
          />
          <div class="flex-1 min-w-0">
            <p class="text-sm font-semibold text-zinc-900 dark:text-white">
              {{ toast.title }}
            </p>
            <p
              v-if="toast.message"
              class="mt-1 text-sm text-zinc-500 dark:text-zinc-400"
            >
              {{ toast.message }}
            </p>
          </div>
          <button
            type="button"
            class="shrink-0 rounded-lg p-1 text-zinc-400 hover:text-zinc-500 hover:bg-zinc-100 dark:hover:text-zinc-300 dark:hover:bg-zinc-800 transition-colors"
            @click="remove(toast.id)"
          >
            <XMarkIcon class="h-4 w-4" />
          </button>
        </div>
      </div>
    </TransitionGroup>
  </div>
</template>
