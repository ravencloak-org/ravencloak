<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Divider from 'primevue/divider'
import Message from 'primevue/message'

const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const errorMessage = ref<string | null>(null)

onMounted(() => {
  const error = route.query.error as string | undefined
  if (error === 'insufficient_permissions') {
    errorMessage.value = 'You do not have permission to access this application. Super Admin role is required.'
  }
})

async function handleLogin(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    await authStore.login()
  } catch {
    errorMessage.value = 'Failed to initiate login. Please try again.'
    loading.value = false
  }
}

async function handleGitHubLogin(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    await authStore.login('github')
  } catch {
    errorMessage.value = 'Failed to initiate GitHub login. Please try again.'
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <Card class="login-card">
      <template #header>
        <div class="login-header">
          <h1>KOS Auth Admin</h1>
          <p>Sign in to manage your authentication realms</p>
        </div>
      </template>

      <template #content>
        <Message
          v-if="errorMessage"
          severity="error"
          :closable="false"
          class="login-error"
        >
          {{ errorMessage }}
        </Message>

        <div class="login-actions">
          <Button
            label="Sign in with Keycloak"
            icon="pi pi-sign-in"
            :loading="loading"
            class="login-button"
            @click="handleLogin"
          />

          <Divider align="center">
            <span class="divider-text">or</span>
          </Divider>

          <Button
            label="Sign in with GitHub"
            icon="pi pi-github"
            severity="secondary"
            :loading="loading"
            class="login-button github-button"
            @click="handleGitHubLogin"
          />
        </div>
      </template>

      <template #footer>
        <div class="login-footer">
          <p>Only users with Super Admin role can access this application.</p>
        </div>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 1rem;
  background: linear-gradient(135deg, var(--p-surface-ground) 0%, var(--p-surface-100) 100%);
}

.login-card {
  width: 100%;
  max-width: 420px;
}

.login-header {
  text-align: center;
  padding: 2rem 1rem 1rem;
}

.login-header h1 {
  margin: 0 0 0.5rem;
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--p-text-color);
}

.login-header p {
  margin: 0;
  color: var(--p-text-muted-color);
}

.login-error {
  margin-bottom: 1rem;
}

.login-actions {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.login-button {
  width: 100%;
  justify-content: center;
}

.github-button {
  background-color: #24292e;
  border-color: #24292e;
}

.github-button:hover {
  background-color: #2f363d;
  border-color: #2f363d;
}

.divider-text {
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}

.login-footer {
  text-align: center;
  padding-top: 0.5rem;
}

.login-footer p {
  margin: 0;
  font-size: 0.75rem;
  color: var(--p-text-muted-color);
}
</style>
