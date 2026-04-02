<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useRealmStore } from '@/stores/realm'
import {
  Menu,
  MenuButton,
  MenuItems,
  MenuItem,
} from '@headlessui/vue'
import { ChevronUpDownIcon } from '@heroicons/vue/20/solid'

const router = useRouter()
const realmStore = useRealmStore()

const currentRealmName = computed(() => {
  return realmStore.currentRealm?.displayName || realmStore.currentRealm?.realmName || 'Select Realm'
})

onMounted(async () => {
  if (realmStore.realms.length === 0) {
    await realmStore.fetchRealms()
  }
})

async function switchRealm(realmName: string): Promise<void> {
  await realmStore.fetchRealm(realmName)
  router.push(`/realms/${realmName}`)
}
</script>

<template>
  <Menu as="div" class="relative">
    <MenuButton
      class="flex w-full items-center justify-between rounded-lg bg-zinc-800 px-3 py-2.5 text-left text-sm font-semibold text-white hover:bg-zinc-700 focus:outline-none focus:ring-2 focus:ring-white/20"
    >
      <span class="truncate">{{ currentRealmName }}</span>
      <ChevronUpDownIcon class="ml-2 h-5 w-5 shrink-0 text-zinc-400" />
    </MenuButton>

    <transition
      enter-active-class="transition duration-100 ease-out"
      enter-from-class="transform scale-95 opacity-0"
      enter-to-class="transform scale-100 opacity-100"
      leave-active-class="transition duration-75 ease-in"
      leave-from-class="transform scale-100 opacity-100"
      leave-to-class="transform scale-95 opacity-0"
    >
      <MenuItems
        class="absolute left-0 right-0 z-50 mt-1 max-h-60 overflow-auto rounded-lg bg-zinc-800 py-1 shadow-lg ring-1 ring-white/10 focus:outline-none"
      >
        <MenuItem
          v-for="realm in realmStore.realms"
          :key="realm.id"
          v-slot="{ active }"
        >
          <button
            :class="[
              'flex w-full items-center px-3 py-2 text-sm',
              active ? 'bg-zinc-700 text-white' : 'text-zinc-300',
              realm.realmName === realmStore.currentRealm?.realmName
                ? 'font-semibold text-white'
                : '',
            ]"
            @click="switchRealm(realm.realmName)"
          >
            <span class="truncate">{{ realm.displayName || realm.realmName }}</span>
            <span
              v-if="realm.realmName === realmStore.currentRealm?.realmName"
              class="ml-auto text-zinc-400"
            >
              <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                <path
                  fill-rule="evenodd"
                  d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.05-.143z"
                  clip-rule="evenodd"
                />
              </svg>
            </span>
          </button>
        </MenuItem>

        <div v-if="realmStore.realms.length === 0" class="px-3 py-2 text-sm text-zinc-500">
          No realms available
        </div>
      </MenuItems>
    </transition>
  </Menu>
</template>
