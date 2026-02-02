<script setup lang="ts">
import Card from 'primevue/card'
import Tag from 'primevue/tag'
import type { Realm } from '@/types'

defineProps<{
  realm: Realm
}>()

defineEmits<{
  click: []
}>()

function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}
</script>

<template>
  <Card class="realm-card" @click="$emit('click')">
    <template #header>
      <div class="card-header">
        <div class="realm-icon">
          <i class="pi pi-globe" />
        </div>
      </div>
    </template>

    <template #title>
      <div class="card-title">
        <span class="realm-name">{{ realm.displayName || realm.name }}</span>
        <Tag
          :value="realm.enabled ? 'Active' : 'Inactive'"
          :severity="realm.enabled ? 'success' : 'danger'"
          class="status-tag"
        />
      </div>
    </template>

    <template #subtitle>
      <span class="realm-id">{{ realm.name }}</span>
    </template>

    <template #content>
      <div class="card-content">
        <div class="info-row">
          <span class="info-label">SPI Status</span>
          <Tag
            :value="realm.spiEnabled ? 'Enabled' : 'Disabled'"
            :severity="realm.spiEnabled ? 'info' : 'warn'"
            class="spi-tag"
          />
        </div>
        <div class="info-row">
          <span class="info-label">Created</span>
          <span class="info-value">{{ formatDate(realm.createdAt) }}</span>
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
.realm-card {
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.realm-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.card-header {
  padding: 1.5rem;
  background: linear-gradient(135deg, var(--p-primary-500) 0%, var(--p-primary-600) 100%);
  display: flex;
  justify-content: center;
}

.realm-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.realm-icon i {
  font-size: 1.5rem;
  color: white;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.realm-name {
  font-weight: 600;
  color: var(--p-text-color);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.realm-id {
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

.info-value {
  font-size: 0.875rem;
  color: var(--p-text-color);
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

.realm-card:hover .view-details i {
  transform: translateX(4px);
}
</style>
