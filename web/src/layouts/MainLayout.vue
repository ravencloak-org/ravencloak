<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import Menu from 'primevue/menu'
import { ref } from 'vue'
import type { MenuItem } from 'primevue/menuitem'

const router = useRouter()
const authStore = useAuthStore()
const userMenu = ref<InstanceType<typeof Menu> | null>(null)

const userDisplayName = computed(() => {
  if (authStore.user?.displayName) {
    return authStore.user.displayName
  }
  if (authStore.user?.firstName) {
    return `${authStore.user.firstName} ${authStore.user.lastName ?? ''}`.trim()
  }
  return authStore.user?.email ?? 'User'
})

const userInitials = computed(() => {
  const name = userDisplayName.value
  const parts = name.split(' ')
  const first = parts[0]
  const second = parts[1]
  if (parts.length >= 2 && first && second && first[0] && second[0]) {
    return (first[0] + second[0]).toUpperCase()
  }
  return name.substring(0, 2).toUpperCase()
})

const menuItems: MenuItem[] = [
  {
    label: 'Profile',
    icon: 'pi pi-user',
    disabled: true
  },
  {
    separator: true
  },
  {
    label: 'Logout',
    icon: 'pi pi-sign-out',
    command: () => handleLogout()
  }
]

function toggleUserMenu(event: Event): void {
  userMenu.value?.toggle(event)
}

async function handleLogout(): Promise<void> {
  await authStore.logout()
}

function navigateToRealms(): void {
  router.push({ name: 'realms' })
}
</script>

<template>
  <div class="layout">
    <header class="header">
      <div class="header-left">
        <Button
          text
          class="logo-button"
          @click="navigateToRealms"
        >
          <span class="logo-text">KOS Auth Admin</span>
        </Button>
      </div>

      <div class="header-right">
        <div class="user-info" @click="toggleUserMenu">
          <Avatar
            :label="userInitials"
            shape="circle"
            class="user-avatar"
          />
          <span class="user-name">{{ userDisplayName }}</span>
          <i class="pi pi-chevron-down" />
        </div>
        <Menu
          ref="userMenu"
          :model="menuItems"
          :popup="true"
        />
      </div>
    </header>

    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1.5rem;
  background-color: var(--p-surface-card);
  border-bottom: 1px solid var(--p-surface-border);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.logo-button {
  font-size: 1.25rem;
  font-weight: 600;
}

.logo-text {
  background: linear-gradient(90deg, var(--p-primary-500), var(--p-primary-300));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  padding: 0.5rem;
  border-radius: var(--p-border-radius);
  transition: background-color 0.2s;
}

.user-info:hover {
  background-color: var(--p-surface-hover);
}

.user-avatar {
  background-color: var(--p-primary-500);
  color: var(--p-primary-contrast-color);
}

.user-name {
  font-weight: 500;
  color: var(--p-text-color);
}

.main-content {
  flex: 1;
  padding: 1.5rem;
  background-color: var(--p-surface-ground);
}
</style>
