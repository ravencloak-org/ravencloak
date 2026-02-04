<script setup lang="ts">
import Card from 'primevue/card'
import Tag from 'primevue/tag'
import type { Client } from '@/types'

defineProps<{
  client: Client
}>()

defineEmits<{
  click: []
}>()
</script>

<template>
  <Card class="client-card" @click="$emit('click')">
    <template #header>
      <div class="card-header" :class="{ 'header-public': client.publicClient, 'header-confidential': !client.publicClient }">
        <div class="client-icon">
          <i :class="client.publicClient ? 'pi pi-globe' : 'pi pi-lock'" />
        </div>
      </div>
    </template>

    <template #title>
      <div class="card-title">
        <span class="client-name">{{ client.name || client.clientId }}</span>
        <Tag
          :value="client.enabled ? 'Enabled' : 'Disabled'"
          :severity="client.enabled ? 'success' : 'danger'"
          class="status-tag"
        />
      </div>
    </template>

    <template #subtitle>
      <span class="client-id">{{ client.clientId }}</span>
    </template>

    <template #content>
      <div class="card-content">
        <div class="info-row">
          <span class="info-label">Type</span>
          <Tag
            :value="client.publicClient ? 'Public' : 'Confidential'"
            :severity="client.publicClient ? 'info' : 'warn'"
          />
        </div>
        <div class="info-row">
          <span class="info-label">Flows</span>
          <div class="flow-tags">
            <Tag
              v-if="client.standardFlowEnabled"
              value="Standard"
              severity="secondary"
              class="flow-tag"
            />
            <Tag
              v-if="client.directAccessGrantsEnabled"
              value="Direct"
              severity="secondary"
              class="flow-tag"
            />
            <Tag
              v-if="client.serviceAccountsEnabled"
              value="Service"
              severity="secondary"
              class="flow-tag"
            />
            <span
              v-if="!client.standardFlowEnabled && !client.directAccessGrantsEnabled && !client.serviceAccountsEnabled"
              class="no-flows"
            >
              None
            </span>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="card-footer">
        <span class="view-details">
          View Details
          <i class="pi pi-arrow-right" />
        </span>
      </div>
    </template>
  </Card>
</template>

<style scoped>
.client-card {
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.client-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.card-header {
  padding: 1.5rem;
  display: flex;
  justify-content: center;
}

.header-public {
  background: linear-gradient(135deg, var(--p-blue-500) 0%, var(--p-blue-600) 100%);
}

.header-confidential {
  background: linear-gradient(135deg, var(--p-purple-500) 0%, var(--p-purple-600) 100%);
}

.client-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.client-icon i {
  font-size: 1.5rem;
  color: white;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.client-name {
  font-weight: 600;
  color: var(--p-text-color);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.client-id {
  font-family: monospace;
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
}

.flow-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  justify-content: flex-end;
}

.flow-tag {
  font-size: 0.7rem;
}

.no-flows {
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
}

.card-footer {
  display: flex;
  justify-content: flex-end;
}

.view-details {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--p-primary-500);
  font-weight: 500;
}

.view-details i {
  font-size: 0.75rem;
  transition: transform 0.2s;
}

.client-card:hover .view-details i {
  transform: translateX(4px);
}
</style>
