<script setup lang="ts">
import { Menu, MenuButton, MenuItems } from '@headlessui/vue'
</script>

<template>
  <Menu
    as="div"
    class="relative inline-block text-left"
  >
    <MenuButton as="template">
      <slot name="trigger" />
    </MenuButton>

    <transition
      enter-active-class="transition ease-out duration-100"
      enter-from-class="opacity-0 scale-95"
      enter-to-class="opacity-100 scale-100"
      leave-active-class="transition ease-in duration-75"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95"
    >
      <MenuItems
        class="absolute right-0 z-10 mt-2 w-56 origin-top-right rounded-lg bg-white py-1 shadow-lg ring-1 ring-zinc-200 focus:outline-none dark:bg-zinc-900 dark:ring-zinc-700"
      >
        <slot />
      </MenuItems>
    </transition>
  </Menu>
</template>
