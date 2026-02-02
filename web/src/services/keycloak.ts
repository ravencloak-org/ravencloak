import Keycloak from 'keycloak-js'

const keycloakConfig = {
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID
}

const keycloak = new Keycloak(keycloakConfig)

export interface KeycloakTokenParsed {
  sub?: string
  email?: string
  preferred_username?: string
  given_name?: string
  family_name?: string
  name?: string
  realm_access?: {
    roles: string[]
  }
  resource_access?: Record<string, { roles: string[] }>
}

export function getKeycloak(): Keycloak {
  return keycloak
}

export async function initKeycloak(): Promise<boolean> {
  try {
    const authenticated = await keycloak.init({
      onLoad: 'check-sso',
      silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
      pkceMethod: 'S256',
      checkLoginIframe: false
    })

    if (authenticated) {
      setupTokenRefresh()
    }

    return authenticated
  } catch (error) {
    console.error('Keycloak init failed:', error)
    return false
  }
}

export function login(idpHint?: string): Promise<void> {
  const options: Keycloak.KeycloakLoginOptions = {
    redirectUri: window.location.origin + '/realms'
  }

  if (idpHint) {
    options.idpHint = idpHint
  }

  return keycloak.login(options)
}

export function logout(): Promise<void> {
  return keycloak.logout({
    redirectUri: window.location.origin + '/login'
  })
}

export function getToken(): string | undefined {
  return keycloak.token
}

export function getTokenParsed(): KeycloakTokenParsed | undefined {
  return keycloak.tokenParsed as KeycloakTokenParsed | undefined
}

export function isAuthenticated(): boolean {
  return keycloak.authenticated ?? false
}

export function hasRole(role: string): boolean {
  return keycloak.hasRealmRole(role)
}

export function hasSuperAdminRole(): boolean {
  return hasRole('SUPER_ADMIN')
}

function setupTokenRefresh(): void {
  setInterval(async () => {
    if (keycloak.authenticated) {
      try {
        const refreshed = await keycloak.updateToken(60)
        if (refreshed) {
          console.debug('Token refreshed')
        }
      } catch (error) {
        console.error('Failed to refresh token:', error)
        await keycloak.logout()
      }
    }
  }, 30000)
}

export default keycloak
