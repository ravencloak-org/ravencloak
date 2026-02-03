<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import { clientsApi } from '@/api'
import Card from 'primevue/card'
import SelectButton from 'primevue/selectbutton'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import Message from 'primevue/message'
import type { IntegrationSnippetsResponse } from '@/types'

interface Props {
  realmName: string
  clientId: string
  isPublicClient: boolean
}

const props = defineProps<Props>()
const toast = useToast()

const loading = ref(false)
const error = ref<string | null>(null)
const snippetsData = ref<IntegrationSnippetsResponse | null>(null)

// Frontend framework selection
type Framework = 'vanillaJs' | 'react' | 'vue'
const selectedFramework = ref<Framework>('vanillaJs')

const frameworkOptions = [
  { label: 'Vanilla JS', value: 'vanillaJs', icon: 'pi pi-code' },
  { label: 'React', value: 'react', icon: 'pi pi-external-link' },
  { label: 'Vue', value: 'vue', icon: 'pi pi-palette' }
]

// Backend file selection
type BackendFile = 'applicationYml' | 'securityConfig' | 'authClient' | 'buildGradle'
const selectedBackendFile = ref<BackendFile>('applicationYml')

const backendFileOptions = [
  { label: 'application.yml', value: 'applicationYml' },
  { label: 'SecurityConfig.kt', value: 'securityConfig' },
  { label: 'AuthClient.kt', value: 'authClient' },
  { label: 'build.gradle.kts', value: 'buildGradle' }
]

const currentSnippet = computed(() => {
  if (!snippetsData.value) return ''
  if (snippetsData.value.isPublicClient && snippetsData.value.snippets) {
    return snippetsData.value.snippets[selectedFramework.value]
  }
  if (!snippetsData.value.isPublicClient && snippetsData.value.backendSnippets) {
    return snippetsData.value.backendSnippets[selectedBackendFile.value]
  }
  return ''
})

async function loadSnippets() {
  loading.value = true
  error.value = null

  try {
    snippetsData.value = await clientsApi.getIntegrationSnippets(props.realmName, props.clientId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load integration snippets'
  } finally {
    loading.value = false
  }
}

async function copyToClipboard() {
  if (!currentSnippet.value) return

  try {
    await navigator.clipboard.writeText(currentSnippet.value)
    toast.add({
      severity: 'success',
      summary: 'Copied',
      detail: 'Code snippet copied to clipboard',
      life: 2000
    })
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to copy to clipboard',
      life: 3000
    })
  }
}

onMounted(() => {
  loadSnippets()
})
</script>

<template>
  <div class="integration-snippets">
    <Card v-if="loading">
      <template #content>
        <Skeleton height="2rem" class="mb-3" />
        <Skeleton height="20rem" />
      </template>
    </Card>

    <Card v-else-if="error" class="error-card">
      <template #content>
        <Message severity="error" :closable="false">
          {{ error }}
        </Message>
      </template>
    </Card>

    <Card v-else-if="snippetsData" class="snippet-card">
      <template #content>
        <!-- Frontend client snippets -->
        <template v-if="snippetsData.isPublicClient">
          <div class="snippet-header">
            <SelectButton
              v-model="selectedFramework"
              :options="frameworkOptions"
              optionLabel="label"
              optionValue="value"
              class="framework-selector"
            />
            <Button
              icon="pi pi-copy"
              label="Copy"
              text
              @click="copyToClipboard"
            />
          </div>

          <div class="config-summary">
            <div class="config-item">
              <span class="config-label">Keycloak URL:</span>
              <code>{{ snippetsData.keycloakUrl }}</code>
            </div>
            <div class="config-item">
              <span class="config-label">Realm:</span>
              <code>{{ snippetsData.realmName }}</code>
            </div>
            <div class="config-item">
              <span class="config-label">Client ID:</span>
              <code>{{ snippetsData.clientId }}</code>
            </div>
          </div>

          <div class="code-container">
            <pre><code>{{ currentSnippet }}</code></pre>
          </div>

          <div class="snippet-footer">
            <Message severity="info" :closable="false" class="install-hint">
              <strong>Install keycloak-js:</strong>
              <code>npm install keycloak-js</code>
            </Message>
          </div>
        </template>

        <!-- Backend client snippets -->
        <template v-else>
          <Message severity="warn" :closable="false" class="security-warning">
            <strong>Security Warning:</strong> Never commit client secrets to version control. Use environment variables or a secrets manager.
          </Message>

          <div class="snippet-header">
            <SelectButton
              v-model="selectedBackendFile"
              :options="backendFileOptions"
              optionLabel="label"
              optionValue="value"
              class="framework-selector"
            />
            <Button
              icon="pi pi-copy"
              label="Copy"
              text
              @click="copyToClipboard"
            />
          </div>

          <div class="config-summary">
            <div class="config-item">
              <span class="config-label">Keycloak URL:</span>
              <code>{{ snippetsData.keycloakUrl }}</code>
            </div>
            <div class="config-item">
              <span class="config-label">Realm:</span>
              <code>{{ snippetsData.realmName }}</code>
            </div>
            <div class="config-item">
              <span class="config-label">Client ID:</span>
              <code>{{ snippetsData.clientId }}</code>
            </div>
          </div>

          <div class="code-container">
            <pre><code>{{ currentSnippet }}</code></pre>
          </div>

          <div class="snippet-footer">
            <Message severity="info" :closable="false" class="install-hint">
              <strong>Required:</strong> Set <code>CLIENT_SECRET</code> environment variable with your client secret
            </Message>
          </div>
        </template>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.integration-snippets {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.snippet-card :deep(.p-card-content) {
  padding: 0;
}

.snippet-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.framework-selector :deep(.p-selectbutton) {
  display: flex;
}

.config-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin-bottom: 1rem;
  padding: 0.75rem;
  background-color: var(--p-surface-100);
  border-radius: 6px;
}

.config-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.config-label {
  font-weight: 500;
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}

.config-item code {
  background-color: var(--p-surface-200);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.875rem;
}

.code-container {
  background-color: #1e1e1e;
  border-radius: 8px;
  overflow: auto;
  max-height: 500px;
}

.code-container pre {
  margin: 0;
  padding: 1rem;
  overflow-x: auto;
}

.code-container code {
  color: #d4d4d4;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 0.875rem;
  line-height: 1.6;
  white-space: pre;
}

.snippet-footer {
  margin-top: 1rem;
}

.install-hint {
  font-size: 0.875rem;
}

.install-hint strong {
  margin-right: 0.5rem;
}

.install-hint code {
  background-color: var(--p-surface-200);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  margin-left: 0.5rem;
}

.security-warning {
  margin-bottom: 1rem;
}

.security-warning strong {
  margin-right: 0.5rem;
}
</style>
