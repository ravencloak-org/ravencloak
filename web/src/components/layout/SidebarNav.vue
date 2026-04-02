<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRealmStore } from '@/stores/realm'
import {
  HomeIcon,
  RectangleStackIcon,
  UsersIcon,
  KeyIcon,
  UserGroupIcon,
  GlobeAltIcon,
  ShieldCheckIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'

const route = useRoute()
const router = useRouter()
const realmStore = useRealmStore()

const realmName = computed(() => {
  return (route.params.name as string) || realmStore.currentRealm?.realmName || ''
})

const primaryNavItems = computed(() => [
  { name: 'Dashboard', icon: HomeIcon, path: `/realms/${realmName.value}` },
  { name: 'Clients', icon: RectangleStackIcon, path: `/realms/${realmName.value}/clients` },
  { name: 'Users', icon: UsersIcon, path: `/realms/${realmName.value}/users` },
  { name: 'Roles', icon: KeyIcon, path: `/realms/${realmName.value}/roles` },
  { name: 'Groups', icon: UserGroupIcon, path: `/realms/${realmName.value}/groups` },
  {
    name: 'Identity Providers',
    icon: GlobeAltIcon,
    path: `/realms/${realmName.value}/idp`,
  },
])

const secondaryNavItems = computed(() => [
  { name: 'Nebula VPN', icon: ShieldCheckIcon, path: `/realms/${realmName.value}/nebula` },
  { name: 'Audit Trail', icon: ClockIcon, path: `/realms/${realmName.value}/audit` },
])

function isActive(itemPath: string): boolean {
  // Exact match for dashboard
  if (itemPath === `/realms/${realmName.value}`) {
    return route.path === itemPath
  }
  // Prefix match for sub-pages
  return route.path.startsWith(itemPath)
}

function navigate(path: string): void {
  router.push(path)
}
</script>

<template>
  <nav class="flex flex-1 flex-col gap-1 px-3 py-4">
    <template v-for="item in primaryNavItems" :key="item.name">
      <button
        :class="[
          'group flex w-full items-center gap-x-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
          isActive(item.path)
            ? 'bg-zinc-800 text-white'
            : 'text-zinc-400 hover:bg-zinc-800 hover:text-white',
        ]"
        @click="navigate(item.path)"
      >
        <component
          :is="item.icon"
          :class="[
            'h-5 w-5 shrink-0',
            isActive(item.path) ? 'text-white' : 'text-zinc-400 group-hover:text-white',
          ]"
        />
        {{ item.name }}
      </button>
    </template>

    <div class="my-2 border-t border-zinc-800" />

    <template v-for="item in secondaryNavItems" :key="item.name">
      <button
        :class="[
          'group flex w-full items-center gap-x-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
          isActive(item.path)
            ? 'bg-zinc-800 text-white'
            : 'text-zinc-400 hover:bg-zinc-800 hover:text-white',
        ]"
        @click="navigate(item.path)"
      >
        <component
          :is="item.icon"
          :class="[
            'h-5 w-5 shrink-0',
            isActive(item.path) ? 'text-white' : 'text-zinc-400 group-hover:text-white',
          ]"
        />
        {{ item.name }}
      </button>
    </template>
  </nav>
</template>
