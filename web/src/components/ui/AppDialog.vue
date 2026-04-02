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
              class="relative w-full max-w-lg rounded-xl bg-white p-6 shadow-xl ring-1 ring-zinc-950/5 dark:bg-zinc-900 dark:ring-white/10"
            >
              <div
                v-if="title"
                class="flex items-center justify-between mb-4"
              >
                <DialogTitle class="text-lg font-semibold text-zinc-900 dark:text-white">
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
              <button
                v-else
                type="button"
                class="absolute top-4 right-4 rounded-lg p-1.5 text-zinc-400 hover:text-zinc-500 hover:bg-zinc-100 dark:hover:text-zinc-300 dark:hover:bg-zinc-800 transition-colors"
                @click="emit('close')"
              >
                <XMarkIcon class="h-5 w-5" />
              </button>
              <slot />
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>
</template>
