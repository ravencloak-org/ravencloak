<script setup lang="ts">
import { computed } from 'vue'
import type { AuditLog } from '@/api/audit'

const props = defineProps<{
  log: AuditLog
}>()

interface DiffField {
  key: string
  before: unknown
  after: unknown
  changed: boolean
}

const diffFields = computed<DiffField[]>(() => {
  const before = props.log.beforeState || {}
  const after = props.log.afterState || {}
  const changedFields = new Set(props.log.changedFields || [])

  // Get all keys from both before and after
  const allKeys = new Set([...Object.keys(before), ...Object.keys(after)])

  return Array.from(allKeys)
    .filter(key => key !== 'id' && key !== 'keycloakId') // Skip internal IDs
    .map(key => ({
      key,
      before: before[key],
      after: after[key],
      changed: changedFields.has(key) || before[key] !== after[key]
    }))
    .sort((a, b) => {
      // Show changed fields first
      if (a.changed && !b.changed) return -1
      if (!a.changed && b.changed) return 1
      return a.key.localeCompare(b.key)
    })
})

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'boolean') return value ? 'Yes' : 'No'
  if (Array.isArray(value)) {
    if (value.length === 0) return '(empty)'
    return value.join(', ')
  }
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  return String(value)
}

function formatFieldName(key: string): string {
  // Convert camelCase to Title Case
  return key
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, str => str.toUpperCase())
    .trim()
}
</script>

<template>
  <div class="diff-viewer">
    <div class="diff-header">
      <div class="diff-column before-column">
        <span class="column-label">Before</span>
      </div>
      <div class="diff-column after-column">
        <span class="column-label">After</span>
      </div>
    </div>

    <div v-if="diffFields.length === 0" class="no-changes">
      No changes to display
    </div>

    <div v-else class="diff-rows">
      <div
        v-for="field in diffFields"
        :key="field.key"
        class="diff-row"
        :class="{ changed: field.changed }"
      >
        <div class="field-name">{{ formatFieldName(field.key) }}</div>
        <div class="diff-values">
          <div class="diff-column before-column">
            <span
              class="value"
              :class="{ removed: field.changed && field.before !== undefined }"
            >
              {{ formatValue(field.before) }}
            </span>
          </div>
          <div class="arrow">
            <i v-if="field.changed" class="pi pi-arrow-right" />
          </div>
          <div class="diff-column after-column">
            <span
              class="value"
              :class="{ added: field.changed && field.after !== undefined }"
            >
              {{ formatValue(field.after) }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.diff-viewer {
  font-family: monospace;
  font-size: 0.875rem;
}

.diff-header {
  display: flex;
  gap: 2rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--p-surface-border);
  margin-bottom: 0.5rem;
}

.diff-header .diff-column {
  flex: 1;
}

.column-label {
  font-weight: 600;
  color: var(--p-text-muted-color);
  text-transform: uppercase;
  font-size: 0.7rem;
  letter-spacing: 0.05em;
}

.no-changes {
  padding: 1rem;
  text-align: center;
  color: var(--p-text-muted-color);
}

.diff-rows {
  display: flex;
  flex-direction: column;
}

.diff-row {
  display: flex;
  flex-direction: column;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--p-surface-100);
}

.diff-row:last-child {
  border-bottom: none;
}

.diff-row.changed {
  background-color: var(--p-yellow-50);
}

.field-name {
  font-weight: 500;
  color: var(--p-text-color);
  margin-bottom: 0.25rem;
  font-family: inherit;
}

.diff-values {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.diff-column {
  flex: 1;
  min-width: 0;
}

.arrow {
  flex-shrink: 0;
  width: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--p-text-muted-color);
}

.value {
  display: block;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  background-color: var(--p-surface-100);
  word-break: break-word;
  white-space: pre-wrap;
}

.value.removed {
  background-color: var(--p-red-100);
  color: var(--p-red-700);
  text-decoration: line-through;
}

.value.added {
  background-color: var(--p-green-100);
  color: var(--p-green-700);
}
</style>
