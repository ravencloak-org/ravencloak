<script setup lang="ts">
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import ClientCard from '@/components/ClientCard.vue'
import type { Client } from '@/types'

const props = defineProps<{
  clients: Client[]
  realmName: string
}>()

const router = useRouter()

function navigateToCreateClient(): void {
  router.push(`/realms/${props.realmName}/clients/create`)
}

function navigateToClient(clientId: string): void {
  router.push(`/realms/${props.realmName}/clients/${clientId}`)
}
</script>

<template>
  <div class="client-list">
    <div class="list-header">
      <Button
        label="Add Client"
        icon="pi pi-plus"
        size="small"
        @click="navigateToCreateClient"
      />
    </div>

    <div v-if="clients.length === 0" class="empty-state">
      <div class="empty-content">
        <i class="pi pi-desktop empty-icon" />
        <h3 class="empty-title">No clients configured</h3>
        <p class="empty-description">
          Create your first OAuth2 client to get started with authentication.
        </p>
        <Button
          label="Create your first client"
          icon="pi pi-plus"
          @click="navigateToCreateClient"
        />
      </div>
    </div>

    <div v-else class="client-grid">
      <ClientCard
        v-for="client in clients"
        :key="client.id"
        :client="client"
        @click="navigateToClient(client.clientId)"
      />
    </div>
  </div>
</template>

<style scoped>
.client-list {
  padding: 1rem 0;
}

.list-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 1.5rem;
}

.empty-state {
  display: flex;
  justify-content: center;
  padding: 3rem;
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 400px;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
  color: var(--p-text-muted-color);
  opacity: 0.5;
}

.empty-title {
  margin: 0 0 0.5rem 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--p-text-color);
}

.empty-description {
  margin: 0 0 1.5rem 0;
  color: var(--p-text-muted-color);
  line-height: 1.5;
}

.client-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1.5rem;
}
</style>
