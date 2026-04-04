<script setup lang="ts">
import {
  Dialog,
  DialogPanel,
  DialogTitle,
  TransitionChild,
  TransitionRoot,
} from '@headlessui/vue'
import { XMarkIcon } from '@heroicons/vue/24/outline'

defineProps<{
  open: boolean
  title?: string
}>()

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <TransitionRoot
    :show="open"
    as="template"
  >
    <Dialog
      class="relative z-50"
      @close="emit('close')"
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

      <div class="fixed inset-0 overflow-hidden">
        <div class="absolute inset-0 overflow-hidden">
          <div class="pointer-events-none fixed inset-y-0 right-0 flex max-w-full pl-10">
            <TransitionChild
              as="template"
              enter="transform transition ease-in-out duration-300"
              enter-from="translate-x-full"
              enter-to="translate-x-0"
              leave="transform transition ease-in-out duration-200"
              leave-from="translate-x-0"
              leave-to="translate-x-full"
            >
              <DialogPanel class="pointer-events-auto w-screen max-w-md">
                <div class="flex h-full flex-col bg-white shadow-xl dark:bg-zinc-900 ring-1 ring-zinc-950/5 dark:ring-white/10">
                  <div class="flex items-center justify-between border-b border-zinc-200 dark:border-zinc-800 px-4 py-4 sm:px-6">
                    <DialogTitle class="text-base font-semibold text-zinc-900 dark:text-white">
                      {{ title }}
                    </DialogTitle>
                    <button
                      type="button"
                      class="rounded-lg p-1.5 text-zinc-400 hover:text-zinc-500 hover:bg-zinc-100 dark:hover:text-zinc-300 dark:hover:bg-zinc-800 transition-colors"
                      @click="emit('close')"
                    >
                      <XMarkIcon class="h-5 w-5" />
                    </button>
                  </div>
                  <div class="relative flex-1 overflow-y-auto px-4 py-6 sm:px-6">
                    <slot />
                  </div>
                </div>
              </DialogPanel>
            </TransitionChild>
          </div>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>
</template>
