import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  initKeycloak,
  login as keycloakLogin,
  logout as keycloakLogout,
  isAuthenticated as keycloakIsAuthenticated,
  hasSuperAdminRole,
  getTokenParsed,
  type KeycloakTokenParsed
} from '@/services/keycloak'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const initialized = ref(false)
  const loading = ref(true)
  const user = ref<User | null>(null)
  const error = ref<string | null>(null)

  const isAuthenticated = computed(() => keycloakIsAuthenticated())
  const isSuperAdmin = computed(() => isAuthenticated.value && hasSuperAdminRole())

  async function init(): Promise<boolean> {
    if (initialized.value) {
      return isAuthenticated.value
    }

    loading.value = true
    error.value = null

    try {
      const authenticated = await initKeycloak()

      if (authenticated) {
        const tokenParsed = getTokenParsed()
        if (tokenParsed) {
          user.value = mapTokenToUser(tokenParsed)
        }
      }

      initialized.value = true
      return authenticated
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to initialize authentication'
      return false
    } finally {
      loading.value = false
    }
  }

  async function login(idpHint?: string): Promise<void> {
    loading.value = true
    error.value = null

    try {
      await keycloakLogin(idpHint)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Login failed'
      loading.value = false
      throw err
    }
  }

  async function logout(): Promise<void> {
    loading.value = true

    try {
      user.value = null
      await keycloakLogout()
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Logout failed'
      loading.value = false
      throw err
    }
  }

  function mapTokenToUser(token: KeycloakTokenParsed): User {
    return {
      id: token.sub ?? '',
      email: token.email ?? token.preferred_username ?? '',
      displayName: token.name,
      firstName: token.given_name,
      lastName: token.family_name,
      roles: token.realm_access?.roles ?? []
    }
  }

  return {
    initialized,
    loading,
    user,
    error,
    isAuthenticated,
    isSuperAdmin,
    init,
    login,
    logout
  }
})
