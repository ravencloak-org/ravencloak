<script setup lang="ts">
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import type { AuditLog } from '@/api/audit'

defineProps<{
  logs: AuditLog[]
  showRealmName?: boolean
}>()

const emit = defineEmits<{
  viewDetails: [log: AuditLog]
  revert: [log: AuditLog]
}>()

function formatDate(dateString: string): string {
  const date = new Date(dateString)
  return date.toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function getActionColor(actionType: string): string {
  switch (actionType) {
    case 'CREATE':
      return 'success'
    case 'UPDATE':
      return 'warn'
    case 'DELETE':
      return 'danger'
    default:
      return 'secondary'
  }
}

function getEntityIcon(entityType: string): string {
  switch (entityType) {
    case 'CLIENT':
      return 'pi pi-desktop'
    case 'REALM':
      return 'pi pi-globe'
    case 'ROLE':
      return 'pi pi-id-card'
    case 'GROUP':
      return 'pi pi-users'
    case 'IDP':
      return 'pi pi-link'
    case 'USER':
      return 'pi pi-user'
    default:
      return 'pi pi-circle'
  }
}

function getInitials(name?: string, email?: string): string {
  if (name) {
    return name.split(' ').map(n => n[0] || '').join('').toUpperCase().slice(0, 2) || '?'
  }
  if (email) {
    return (email[0] || '?').toUpperCase()
  }
  return '?'
}

function getActionVerb(actionType: string): string {
  switch (actionType) {
    case 'CREATE':
      return 'created'
    case 'UPDATE':
      return 'updated'
    case 'DELETE':
      return 'deleted'
    default:
      return 'modified'
  }
}
</script>

<template>
  <div class="audit-timeline">
    <div v-if="logs.length === 0" class="empty-state">
      <i class="pi pi-history empty-icon" />
      <p>No audit history found</p>
    </div>

    <div v-else class="timeline">
      <div
        v-for="log in logs"
        :key="log.id"
        class="timeline-item"
        :class="{ reverted: log.reverted }"
      >
        <div class="timeline-marker">
          <Avatar
            :label="getInitials(log.actorDisplayName, log.actorEmail)"
            shape="circle"
            size="normal"
            :style="{
              backgroundColor: log.reverted ? 'var(--p-surface-400)' : 'var(--p-primary-500)',
              color: 'white'
            }"
          />
        </div>

        <div class="timeline-content">
          <div class="timeline-header">
            <div class="actor-info">
              <span class="actor-name">{{ log.actorDisplayName || log.actorEmail || 'Unknown' }}</span>
              <span class="action-verb">{{ getActionVerb(log.actionType) }}</span>
              <Tag
                :value="log.entityType"
                severity="secondary"
                class="entity-type-tag"
              />
              <span class="entity-name">{{ log.entityName }}</span>
              <span v-if="showRealmName" class="realm-badge">
                in {{ log.realmName }}
              </span>
            </div>
            <div class="timeline-meta">
              <span class="timestamp">{{ formatDate(log.createdAt) }}</span>
            </div>
          </div>

          <div class="timeline-body">
            <div class="action-badge">
              <Tag
                :value="log.actionType"
                :severity="getActionColor(log.actionType)"
              />
              <i :class="getEntityIcon(log.entityType)" class="entity-icon" />
            </div>

            <div v-if="log.changedFields && log.changedFields.length > 0" class="changed-fields">
              <span class="changed-label">Changed:</span>
              <Tag
                v-for="field in log.changedFields.slice(0, 5)"
                :key="field"
                :value="field"
                severity="secondary"
                class="field-tag"
              />
              <span v-if="log.changedFields.length > 5" class="more-fields">
                +{{ log.changedFields.length - 5 }} more
              </span>
            </div>

            <div v-if="log.reverted" class="reverted-badge">
              <Tag value="Reverted" severity="secondary" />
              <span class="revert-reason" v-if="log.revertReason">
                Reason: {{ log.revertReason }}
              </span>
            </div>
          </div>

          <div class="timeline-actions">
            <Button
              label="Details"
              icon="pi pi-eye"
              text
              size="small"
              @click="emit('viewDetails', log)"
            />
            <Button
              v-if="log.canRevert && !log.reverted"
              label="Revert"
              icon="pi pi-undo"
              text
              size="small"
              severity="danger"
              @click="emit('revert', log)"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.audit-timeline {
  padding: 1rem 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 3rem;
  color: var(--p-text-muted-color);
}

.empty-icon {
  font-size: 2.5rem;
  margin-bottom: 1rem;
  opacity: 0.5;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.timeline-item {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background-color: var(--p-surface-card);
  border-radius: 8px;
  transition: box-shadow 0.2s;
}

.timeline-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.timeline-item.reverted {
  opacity: 0.7;
  background-color: var(--p-surface-100);
}

.timeline-marker {
  flex-shrink: 0;
}

.timeline-content {
  flex: 1;
  min-width: 0;
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 0.5rem;
}

.actor-info {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.actor-name {
  font-weight: 600;
  color: var(--p-text-color);
}

.action-verb {
  color: var(--p-text-muted-color);
}

.entity-type-tag {
  font-size: 0.7rem;
}

.entity-name {
  font-weight: 500;
  color: var(--p-primary-500);
  font-family: monospace;
}

.realm-badge {
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}

.timeline-meta {
  flex-shrink: 0;
}

.timestamp {
  font-size: 0.75rem;
  color: var(--p-text-muted-color);
}

.timeline-body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

.action-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.entity-icon {
  font-size: 1rem;
  color: var(--p-text-muted-color);
}

.changed-fields {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.25rem;
}

.changed-label {
  font-size: 0.75rem;
  color: var(--p-text-muted-color);
}

.field-tag {
  font-size: 0.65rem;
}

.more-fields {
  font-size: 0.75rem;
  color: var(--p-text-muted-color);
}

.reverted-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.revert-reason {
  font-size: 0.75rem;
  color: var(--p-text-muted-color);
  font-style: italic;
}

.timeline-actions {
  display: flex;
  gap: 0.25rem;
}
</style>
