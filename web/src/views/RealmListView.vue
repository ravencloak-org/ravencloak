<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useRealmStore } from '@/stores/realm'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import RealmCard from '@/components/RealmCard.vue'

const router = useRouter()
const realmStore = useRealmStore()
const toast = useToast()

const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  await loadRealms()
})

async function loadRealms(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    await realmStore.fetchRealms()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load realms'
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.value,
      life: 5000
    })
  } finally {
    loading.value = false
  }
}

function navigateToCreateRealm(): void {
  router.push({ name: 'create-realm' })
}

function navigateToRealmDashboard(realmName: string): void {
  router.push({ name: 'realm-dashboard', params: { name: realmName } })
}
</script>

<template>
  <div class="realm-list-view">
    <div class="page-header">
      <div class="header-content">
        <h1>Realms</h1>
        <p>Manage your authentication realms</p>
      </div>
      <Button
        label="Create Realm"
        icon="pi pi-plus"
        @click="navigateToCreateRealm"
      />
    </div>

    <div v-if="loading" class="loading-container">
      <ProgressSpinner />
      <p>Loading realms...</p>
    </div>

    <Message
      v-else-if="error"
      severity="error"
      :closable="false"
    >
      {{ error }}
      <template #icon>
        <i class="pi pi-exclamation-triangle" />
      </template>
    </Message>

    <div v-else-if="!realmStore.hasRealms" class="empty-state">
      <i class="pi pi-inbox empty-icon" />
      <h2>No Realms Found</h2>
      <p>Get started by creating your first authentication realm.</p>
      <Button
        label="Create Your First Realm"
        icon="pi pi-plus"
        @click="navigateToCreateRealm"
      />
    </div>

    <div v-else class="realm-grid">
      <RealmCard
        v-for="realm in realmStore.realms"
        :key="realm.id"
        :realm="realm"
        @click="navigateToRealmDashboard(realm.realmName)"
      />
    </div>
  </div>
</template>

<style scoped>
.realm-list-view {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 2rem;
}

.header-content h1 {
  margin: 0 0 0.25rem;
  font-size: 1.75rem;
  font-weight: 600;
  color: var(--p-text-color);
}

.header-content p {
  margin: 0;
  color: var(--p-text-muted-color);
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem;
  gap: 1rem;
}

.loading-container p {
  color: var(--p-text-muted-color);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem;
  text-align: center;
  background-color: var(--p-surface-card);
  border-radius: var(--p-border-radius);
  border: 1px dashed var(--p-surface-border);
}

.empty-icon {
  font-size: 4rem;
  color: var(--p-text-muted-color);
  margin-bottom: 1rem;
}

.empty-state h2 {
  margin: 0 0 0.5rem;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--p-text-color);
}

.empty-state p {
  margin: 0 0 1.5rem;
  color: var(--p-text-muted-color);
}

.realm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.5rem;
}
</style>
