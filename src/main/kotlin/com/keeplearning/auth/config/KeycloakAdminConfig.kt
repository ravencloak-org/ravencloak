package com.keeplearning.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "keycloak.admin")
data class KeycloakAdminProperties(
    val baseUrl: String = "http://localhost:8088",
    val realm: String = "master",
    val clientId: String = "admin-cli",
    val clientSecret: String = ""
)

@ConfigurationProperties(prefix = "keycloak.spi")
data class KeycloakSpiProperties(
    val providerId: String = "kos-auth-storage",
    val defaultApiUrl: String = "http://auth-backend:8080/api/users"
)

@ConfigurationProperties(prefix = "keycloak.sync")
data class KeycloakSyncProperties(
    val enabled: Boolean = true
)

@Configuration
@EnableConfigurationProperties(
    KeycloakAdminProperties::class,
    KeycloakSpiProperties::class,
    KeycloakSyncProperties::class
)
class KeycloakAdminConfig
