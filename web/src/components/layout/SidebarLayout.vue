<script setup lang="ts">
import { ref } from 'vue'
import {
  Dialog,
  DialogPanel,
  TransitionChild,
  TransitionRoot,
} from '@headlessui/vue'
import { Bars3Icon, XMarkIcon } from '@heroicons/vue/24/outline'
import RealmSwitcher from './RealmSwitcher.vue'
import SidebarNav from './SidebarNav.vue'
import UserMenu from './UserMenu.vue'

const mobileMenuOpen = ref(false)
</script>

<template>
  <div class="min-h-screen">
    <!-- Mobile sidebar overlay -->
    <TransitionRoot as="template" :show="mobileMenuOpen">
      <Dialog as="div" class="relative z-50 lg:hidden" @close="mobileMenuOpen = false">
        <!-- Backdrop -->
        <TransitionChild
          as="template"
          enter="transition-opacity ease-linear duration-300"
          enter-from="opacity-0"
          enter-to="opacity-100"
          leave="transition-opacity ease-linear duration-300"
          leave-from="opacity-100"
          leave-to="opacity-0"
        >
          <div class="fixed inset-0 bg-black/60" />
        </TransitionChild>

        <div class="fixed inset-0 flex">
          <TransitionChild
            as="template"
            enter="transition ease-in-out duration-300 transform"
            enter-from="-translate-x-full"
            enter-to="translate-x-0"
            leave="transition ease-in-out duration-300 transform"
            leave-from="translate-x-0"
            leave-to="-translate-x-full"
          >
            <DialogPanel class="relative mr-16 flex w-full max-w-64 flex-1">
              <!-- Close button -->
              <TransitionChild
                as="template"
                enter="ease-in-out duration-300"
                enter-from="opacity-0"
                enter-to="opacity-100"
                leave="ease-in-out duration-300"
                leave-from="opacity-100"
                leave-to="opacity-0"
              >
                <div class="absolute left-full top-0 flex w-16 justify-center pt-5">
                  <button
                    type="button"
                    class="-m-2.5 p-2.5"
                    @click="mobileMenuOpen = false"
                  >
                    <span class="sr-only">Close sidebar</span>
                    <XMarkIcon class="h-6 w-6 text-white" />
                  </button>
                </div>
              </TransitionChild>

              <!-- Mobile sidebar content -->
              <div class="flex grow flex-col overflow-y-auto bg-zinc-900">
                <div class="px-3 pt-4 pb-2">
                  <RealmSwitcher />
                </div>
                <SidebarNav />
                <UserMenu />
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </TransitionRoot>

    <!-- Desktop sidebar -->
    <div class="hidden lg:fixed lg:inset-y-0 lg:z-40 lg:flex lg:w-64 lg:flex-col">
      <div class="flex grow flex-col overflow-y-auto bg-zinc-900">
        <div class="px-3 pt-4 pb-2">
          <RealmSwitcher />
        </div>
        <SidebarNav />
        <UserMenu />
      </div>
    </div>

    <!-- Mobile top bar -->
    <div class="sticky top-0 z-40 flex items-center gap-x-4 bg-zinc-900 px-4 py-3 shadow-sm lg:hidden">
      <button
        type="button"
        class="-m-2 p-2 text-zinc-400 hover:text-white"
        @click="mobileMenuOpen = true"
      >
        <span class="sr-only">Open sidebar</span>
        <Bars3Icon class="h-6 w-6" />
      </button>
      <div class="flex-1 text-sm font-semibold text-white">Ravencloak</div>
    </div>

    <!-- Main content area -->
    <main class="min-h-screen bg-white dark:bg-zinc-950 lg:pl-64">
      <div class="px-4 py-6 sm:px-6 lg:px-8">
        <slot />
      </div>
    </main>
  </div>
</template>
