<script setup lang="ts">
import {
  Dialog,
  DialogPanel,
  DialogTitle,
  TransitionChild,
  TransitionRoot,
} from '@headlessui/vue'
import { ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import { useConfirm } from '@/composables/useConfirm'
import { cn } from '@/lib/utils'

const { isOpen, options, accept, cancel } = useConfirm()
</script>

<template>
  <TransitionRoot
    :show="isOpen"
    as="template"
  >
    <Dialog
      class="relative z-50"
      @close="cancel"
    >
      <TransitionChild
        as="template"
        enter="ease-out duration-300"
        enter-from="opacity-0"
        enter-to="opacity-100"
        leave="ease-in duration-200"
        leave-from="opacity-100"
        leave-to="opacity-0"
      >
        <div class="fixed inset-0 bg-zinc-950/25 dark:bg-zinc-950/50 transition-opacity" />
      </TransitionChild>

      <div class="fixed inset-0 z-10 overflow-y-auto">
        <div class="flex min-h-full items-center justify-center p-4">
          <TransitionChild
            as="template"
            enter="ease-out duration-300"
            enter-from="opacity-0 scale-95"
            enter-to="opacity-100 scale-100"
            leave="ease-in duration-200"
            leave-from="opacity-100 scale-100"
            leave-to="opacity-0 scale-95"
          >
            <DialogPanel
              class="relative w-full max-w-md rounded-xl bg-white p-6 shadow-xl ring-1 ring-zinc-950/5 dark:bg-zinc-900 dark:ring-white/10"
            >
              <div class="flex gap-4">
                <div
                  v-if="options.destructive"
                  class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-red-100 dark:bg-red-500/10"
                >
                  <ExclamationTriangleIcon class="h-5 w-5 text-red-600 dark:text-red-400" />
                </div>
                <div class="flex-1">
                  <DialogTitle class="text-base font-semibold text-zinc-900 dark:text-white">
                    {{ options.title }}
                  </DialogTitle>
                  <p class="mt-2 text-sm text-zinc-600 dark:text-zinc-400">
                    {{ options.message }}
                  </p>
                </div>
              </div>

              <div class="mt-6 flex justify-end gap-3">
                <button
                  type="button"
                  class="inline-flex items-center justify-center rounded-lg px-3 py-2 text-sm font-semibold bg-white text-zinc-900 ring-1 ring-inset ring-zinc-300 hover:bg-zinc-50 shadow-sm dark:bg-zinc-800 dark:text-zinc-100 dark:ring-zinc-700 dark:hover:bg-zinc-700"
                  @click="cancel"
                >
                  {{ options.cancelLabel || 'Cancel' }}
                </button>
                <button
                  type="button"
                  :class="cn(
                    'inline-flex items-center justify-center rounded-lg px-3 py-2 text-sm font-semibold text-white shadow-sm',
                    'focus-visible:outline-2 focus-visible:outline-offset-2',
                    options.destructive
                      ? 'bg-red-600 hover:bg-red-500 focus-visible:outline-red-600'
                      : 'bg-primary-600 hover:bg-primary-500 focus-visible:outline-primary-600',
                  )"
                  @click="accept"
                >
                  {{ options.confirmLabel || 'Confirm' }}
                </button>
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>
</template>
