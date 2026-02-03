package com.keeplearning.auth.realm.dto

data class IntegrationSnippetsResponse(
    val keycloakUrl: String,
    val realmName: String,
    val clientId: String,
    val isPublicClient: Boolean,
    val snippets: IntegrationSnippets? = null,
    val backendSnippets: BackendIntegrationSnippets? = null
)

data class IntegrationSnippets(
    val vanillaJs: String,
    val react: String,
    val vue: String
)

data class BackendIntegrationSnippets(
    val applicationYml: String,
    val securityConfig: String,
    val authClient: String,
    val buildGradle: String
)

object IntegrationSnippetGenerator {

    fun generateFrontend(keycloakUrl: String, realmName: String, clientId: String): IntegrationSnippets {
        return IntegrationSnippets(
            vanillaJs = generateVanillaJs(keycloakUrl, realmName, clientId),
            react = generateReact(keycloakUrl, realmName, clientId),
            vue = generateVue(keycloakUrl, realmName, clientId)
        )
    }

    fun generateBackend(keycloakUrl: String, realmName: String, clientId: String, authBackendUrl: String): BackendIntegrationSnippets {
        return BackendIntegrationSnippets(
            applicationYml = generateApplicationYml(keycloakUrl, realmName, clientId),
            securityConfig = generateSecurityConfig(keycloakUrl, realmName),
            authClient = generateAuthClient(authBackendUrl, clientId),
            buildGradle = generateBuildGradle()
        )
    }

    private fun generateVanillaJs(keycloakUrl: String, realmName: String, clientId: String): String {
        return """
import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
  url: "$keycloakUrl",
  realm: "$realmName",
  clientId: "$clientId"
});

// Initialize Keycloak
keycloak.init({
  onLoad: "login-required",
  checkLoginIframe: false,
  pkceMethod: "S256"
}).then(authenticated => {
  if (authenticated) {
    console.log("User authenticated");
    console.log("Token:", keycloak.token);

    // Access user info
    keycloak.loadUserProfile().then(profile => {
      console.log("User:", profile.email);
    });
  }
}).catch(error => {
  console.error("Authentication failed:", error);
});

// Token refresh
keycloak.onTokenExpired = () => {
  keycloak.updateToken(30).catch(() => {
    console.log("Token refresh failed, re-authenticating...");
    keycloak.login();
  });
};

// Use token in API calls
async function fetchWithAuth(url, options = {}) {
  await keycloak.updateToken(30);
  return fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      Authorization: `Bearer ${'$'}{keycloak.token}`
    }
  });
}
        """.trimIndent()
    }

    private fun generateReact(keycloakUrl: String, realmName: String, clientId: String): String {
        return """
// useKeycloak.ts
import { useState, useEffect, useCallback } from "react";
import Keycloak from "keycloak-js";

const keycloakConfig = {
  url: "$keycloakUrl",
  realm: "$realmName",
  clientId: "$clientId"
};

const keycloak = new Keycloak(keycloakConfig);

export function useKeycloak() {
  const [authenticated, setAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [token, setToken] = useState<string | undefined>();
  const [user, setUser] = useState<any>(null);

  useEffect(() => {
    keycloak.init({
      onLoad: "login-required",
      checkLoginIframe: false,
      pkceMethod: "S256"
    }).then(auth => {
      setAuthenticated(auth);
      setToken(keycloak.token);
      if (auth) {
        keycloak.loadUserProfile().then(setUser);
      }
    }).finally(() => {
      setLoading(false);
    });

    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).then(refreshed => {
        if (refreshed) setToken(keycloak.token);
      });
    };
  }, []);

  const login = useCallback(() => keycloak.login(), []);
  const logout = useCallback(() => keycloak.logout(), []);

  const fetchWithAuth = useCallback(async (url: string, options: RequestInit = {}) => {
    await keycloak.updateToken(30);
    return fetch(url, {
      ...options,
      headers: {
        ...options.headers,
        Authorization: `Bearer ${'$'}{keycloak.token}`
      }
    });
  }, []);

  return { authenticated, loading, token, user, login, logout, fetchWithAuth };
}

// Usage in component:
// const { authenticated, user, fetchWithAuth } = useKeycloak();
        """.trimIndent()
    }

    private fun generateVue(keycloakUrl: String, realmName: String, clientId: String): String {
        return """
// keycloak.ts
import Keycloak from "keycloak-js";
import { ref, readonly } from "vue";
import type { App } from "vue";

const keycloakConfig = {
  url: "$keycloakUrl",
  realm: "$realmName",
  clientId: "$clientId"
};

const keycloak = new Keycloak(keycloakConfig);
const authenticated = ref(false);
const loading = ref(true);
const token = ref<string>();
const user = ref<any>(null);

export const useKeycloak = () => ({
  keycloak,
  authenticated: readonly(authenticated),
  loading: readonly(loading),
  token: readonly(token),
  user: readonly(user),
  login: () => keycloak.login(),
  logout: () => keycloak.logout(),
  fetchWithAuth: async (url: string, options: RequestInit = {}) => {
    await keycloak.updateToken(30);
    return fetch(url, {
      ...options,
      headers: {
        ...options.headers,
        Authorization: `Bearer ${'$'}{keycloak.token}`
      }
    });
  }
});

export const keycloakPlugin = {
  install(app: App) {
    keycloak.init({
      onLoad: "login-required",
      checkLoginIframe: false,
      pkceMethod: "S256"
    }).then(auth => {
      authenticated.value = auth;
      token.value = keycloak.token;
      if (auth) {
        keycloak.loadUserProfile().then(profile => {
          user.value = profile;
        });
      }
    }).finally(() => {
      loading.value = false;
    });

    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).then(refreshed => {
        if (refreshed) token.value = keycloak.token;
      });
    };

    app.provide("keycloak", useKeycloak());
  }
};

// main.ts usage:
// import { keycloakPlugin } from "./keycloak";
// app.use(keycloakPlugin);
//
// In component:
// const { authenticated, user, fetchWithAuth } = inject("keycloak");
        """.trimIndent()
    }

    private fun generateApplicationYml(keycloakUrl: String, realmName: String, clientId: String): String {
        return """
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: $keycloakUrl/realms/$realmName
          jwk-set-uri: $keycloakUrl/realms/$realmName/protocol/openid-connect/certs
      client:
        registration:
          keycloak:
            client-id: $clientId
            client-secret: ${'$'}{CLIENT_SECRET}  # Set via environment variable
            authorization-grant-type: client_credentials
            scope: openid
        provider:
          keycloak:
            issuer-uri: $keycloakUrl/realms/$realmName

# Auth backend configuration
auth:
  backend:
    url: ${'$'}{AUTH_BACKEND_URL:http://localhost:8080}
    client-id: $clientId
        """.trimIndent()
    }

    private fun generateSecurityConfig(keycloakUrl: String, realmName: String): String {
        return """
package com.example.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/public/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
            .build()
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val grantedAuthoritiesConverter = JwtGrantedAuthoritiesConverter()
        // Extract roles from Keycloak's realm_access.roles claim
        grantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access")
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_")

        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt ->
            // Custom role extraction from Keycloak token
            @Suppress("UNCHECKED_CAST")
            val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access")
            val roles = (realmAccess?.get("roles") as? List<String>) ?: emptyList()
            roles.map { org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_${'$'}it") }
        }
        return converter
    }
}
        """.trimIndent()
    }

    private fun generateAuthClient(authBackendUrl: String, clientId: String): String {
        return """
package com.example.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.UUID

/**
 * Client for communicating with the auth backend to manage authorized users.
 * Uses client credentials flow for authentication.
 */
@Component
class AuthBackendClient(
    @Value("${'$'}{auth.backend.url}") private val authBackendUrl: String,
    private val webClientBuilder: WebClient.Builder
) {
    private val webClient = webClientBuilder
        .baseUrl(authBackendUrl)
        .build()

    /**
     * Add users to the authorized list for this client.
     * These users will be able to log in via Keycloak.
     */
    suspend fun addAuthorizedUsers(clientUuid: UUID, users: List<UserRef>, token: String): AddUsersResponse {
        return webClient.post()
            .uri("/api/clients/{clientId}/users", clientUuid)
            .header("Authorization", "Bearer ${'$'}token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(AddUsersRequest(users))
            .retrieve()
            .bodyToMono(AddUsersResponse::class.java)
            .block()!!
    }

    /**
     * Remove users from the authorized list.
     */
    suspend fun removeAuthorizedUsers(clientUuid: UUID, emails: List<String>, token: String): RemoveUsersResponse {
        return webClient.method(org.springframework.http.HttpMethod.DELETE)
            .uri("/api/clients/{clientId}/users", clientUuid)
            .header("Authorization", "Bearer ${'$'}token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RemoveUsersRequest(emails))
            .retrieve()
            .bodyToMono(RemoveUsersResponse::class.java)
            .block()!!
    }

    /**
     * Check if a user is authorized for this client.
     */
    suspend fun isUserAuthorized(clientUuid: UUID, email: String, token: String): Boolean {
        return webClient.get()
            .uri("/api/clients/{clientId}/users/{email}/authorized", clientUuid, email)
            .header("Authorization", "Bearer ${'$'}token")
            .retrieve()
            .bodyToMono(AuthorizationCheckResponse::class.java)
            .map { it.authorized }
            .block() ?: false
    }
}

data class UserRef(val email: String, val keycloakId: String? = null)
data class AddUsersRequest(val users: List<UserRef>)
data class RemoveUsersRequest(val emails: List<String>)
data class AddUsersResponse(val added: List<String>, val alreadyExists: List<String>, val failed: List<String>)
data class RemoveUsersResponse(val removed: List<String>, val notFound: List<String>)
data class AuthorizationCheckResponse(val authorized: Boolean, val email: String, val clientId: String)
        """.trimIndent()
    }

    private fun generateBuildGradle(): String {
        return """
// build.gradle.kts

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")  // For WebClient

    // Security with OAuth2/JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")  // For client credentials

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Coroutines (if using suspend functions)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
}
        """.trimIndent()
    }
}
