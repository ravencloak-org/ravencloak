<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useRealmStore } from '@/stores/realm'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Tag from 'primevue/tag'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import ClientList from '@/components/ClientList.vue'
import RoleList from '@/components/RoleList.vue'
import GroupList from '@/components/GroupList.vue'

const props = defineProps<{
  name: string
}>()

const router = useRouter()
const realmStore = useRealmStore()
const toast = useToast()
const confirm = useConfirm()

const loading = ref(true)
const syncing = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  await loadRealm()
})

watch(() => props.name, async () => {
  await loadRealm()
})

async function loadRealm(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    await realmStore.fetchRealm(props.name)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load realm'
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

async function handleSync(): Promise<void> {
  syncing.value = true

  try {
    await realmStore.syncRealm(props.name)
    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Realm synchronized successfully',
      life: 3000
    })
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Sync Failed',
      detail: err instanceof Error ? err.message : 'Failed to sync realm',
      life: 5000
    })
  } finally {
    syncing.value = false
  }
}

async function handleEnableSpi(): Promise<void> {
  try {
    await realmStore.enableSpi(props.name)
    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'User Storage SPI enabled',
      life: 3000
    })
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: err instanceof Error ? err.message : 'Failed to enable SPI',
      life: 5000
    })
  }
}

function handleDelete(): void {
  confirm.require({
    message: `Are you sure you want to delete the realm "${props.name}"? This action cannot be undone.`,
    header: 'Delete Realm',
    icon: 'pi pi-exclamation-triangle',
    rejectLabel: 'Cancel',
    acceptLabel: 'Delete',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await realmStore.deleteRealm(props.name)
        toast.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Realm deleted successfully',
          life: 3000
        })
        router.push({ name: 'realms' })
      } catch (err) {
        toast.add({
          severity: 'error',
          summary: 'Error',
          detail: err instanceof Error ? err.message : 'Failed to delete realm',
          life: 5000
        })
      }
    }
  })
}

function navigateBack(): void {
  router.push({ name: 'realms' })
}

function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<template>
  <div class="realm-dashboard">
    <div v-if="loading" class="loading-container">
      <ProgressSpinner />
      <p>Loading realm...</p>
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

    <template v-else-if="realmStore.currentRealm">
      <div class="page-header">
        <Button
          icon="pi pi-arrow-left"
          text
          rounded
          @click="navigateBack"
        />
        <div class="header-content">
          <div class="header-title">
            <h1>{{ realmStore.currentRealm.displayName || realmStore.currentRealm.name }}</h1>
            <Tag
              :value="realmStore.currentRealm.enabled ? 'Enabled' : 'Disabled'"
              :severity="realmStore.currentRealm.enabled ? 'success' : 'danger'"
            />
          </div>
          <p>{{ realmStore.currentRealm.name }}</p>
        </div>
        <div class="header-actions">
          <Button
            label="Sync"
            icon="pi pi-sync"
            severity="secondary"
            :loading="syncing"
            @click="handleSync"
          />
          <Button
            label="Delete"
            icon="pi pi-trash"
            severity="danger"
            outlined
            @click="handleDelete"
          />
        </div>
      </div>

      <div class="dashboard-content">
        <Card class="info-card">
          <template #title>Realm Information</template>
          <template #content>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">Name</span>
                <span class="info-value">{{ realmStore.currentRealm.name }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">Display Name</span>
                <span class="info-value">{{ realmStore.currentRealm.displayName || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">Status</span>
                <Tag
                  :value="realmStore.currentRealm.enabled ? 'Enabled' : 'Disabled'"
                  :severity="realmStore.currentRealm.enabled ? 'success' : 'danger'"
                />
              </div>
              <div class="info-item">
                <span class="info-label">User Storage SPI</span>
                <div class="spi-status">
                  <Tag
                    :value="realmStore.currentRealm.spiEnabled ? 'Enabled' : 'Disabled'"
                    :severity="realmStore.currentRealm.spiEnabled ? 'success' : 'warn'"
                  />
                  <Button
                    v-if="!realmStore.currentRealm.spiEnabled"
                    label="Enable"
                    size="small"
                    text
                    @click="handleEnableSpi"
                  />
                </div>
              </div>
              <div class="info-item">
                <span class="info-label">Created</span>
                <span class="info-value">{{ formatDate(realmStore.currentRealm.createdAt) }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">Last Updated</span>
                <span class="info-value">{{ formatDate(realmStore.currentRealm.updatedAt) }}</span>
              </div>
            </div>
          </template>
        </Card>

        <TabView class="entity-tabs">
          <TabPanel value="clients">
            <template #header>
              <span class="tab-header">
                <i class="pi pi-desktop" />
                <span>Clients</span>
                <Tag
                  :value="String(realmStore.currentRealm.clients?.length || 0)"
                  severity="secondary"
                  rounded
                />
              </span>
            </template>
            <ClientList :clients="realmStore.currentRealm.clients || []" />
          </TabPanel>

          <TabPanel value="roles">
            <template #header>
              <span class="tab-header">
                <i class="pi pi-shield" />
                <span>Roles</span>
                <Tag
                  :value="String(realmStore.currentRealm.roles?.length || 0)"
                  severity="secondary"
                  rounded
                />
              </span>
            </template>
            <RoleList :roles="realmStore.currentRealm.roles || []" />
          </TabPanel>

          <TabPanel value="groups">
            <template #header>
              <span class="tab-header">
                <i class="pi pi-users" />
                <span>Groups</span>
                <Tag
                  :value="String(realmStore.currentRealm.groups?.length || 0)"
                  severity="secondary"
                  rounded
                />
              </span>
            </template>
            <GroupList :groups="realmStore.currentRealm.groups || []" />
          </TabPanel>
        </TabView>
      </div>
    </template>
  </div>
</template>

<style scoped>
.realm-dashboard {
  max-width: 1200px;
  margin: 0 auto;
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

.page-header {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}

.header-content {
  flex: 1;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.header-content h1 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--p-text-color);
}

.header-content p {
  margin: 0.25rem 0 0;
  color: var(--p-text-muted-color);
  font-family: monospace;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
}

.dashboard-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.info-card {
  background-color: var(--p-surface-card);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1.5rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-label {
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
}

.info-value {
  font-weight: 500;
  color: var(--p-text-color);
}

.spi-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.entity-tabs {
  background-color: var(--p-surface-card);
  border-radius: var(--p-border-radius);
}

.tab-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
</style>
