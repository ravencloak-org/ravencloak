package com.keeplearning.auth.realm.dto

import java.time.Instant
import java.util.UUID

enum class ApplicationType {
    FRONTEND_ONLY,
    BACKEND_ONLY,
    FULL_STACK
}

data class CreateClientRequest(
    val clientId: String,
    val name: String? = null,
    val description: String? = null,
    val publicClient: Boolean = false,
    val standardFlowEnabled: Boolean = true,
    val directAccessGrantsEnabled: Boolean = false,
    val serviceAccountsEnabled: Boolean = false,
    val rootUrl: String? = null,
    val baseUrl: String? = null,
    val redirectUris: List<String> = emptyList(),
    val webOrigins: List<String> = emptyList()
)

data class CreateApplicationRequest(
    val applicationName: String,
    val displayName: String? = null,
    val description: String? = null,
    val applicationType: ApplicationType = ApplicationType.FULL_STACK,
    val rootUrl: String? = null,
    val baseUrl: String? = null,
    val redirectUris: List<String> = emptyList(),
    val webOrigins: List<String> = emptyList()
)

data class UpdateClientRequest(
    val name: String? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
    val publicClient: Boolean? = null,
    val standardFlowEnabled: Boolean? = null,
    val directAccessGrantsEnabled: Boolean? = null,
    val serviceAccountsEnabled: Boolean? = null,
    val rootUrl: String? = null,
    val baseUrl: String? = null,
    val redirectUris: List<String>? = null,
    val webOrigins: List<String>? = null
)

data class ClientDetailResponse(
    val id: UUID,
    val clientId: String,
    val name: String?,
    val description: String?,
    val enabled: Boolean,
    val publicClient: Boolean,
    val standardFlowEnabled: Boolean,
    val directAccessGrantsEnabled: Boolean,
    val serviceAccountsEnabled: Boolean,
    val rootUrl: String?,
    val baseUrl: String?,
    val redirectUris: List<String>,
    val webOrigins: List<String>,
    val pairedClientId: UUID?,
    val pairedClientClientId: String?,
    val createdAt: Instant
)

data class ApplicationResponse(
    val frontendClient: ClientDetailResponse?,
    val backendClient: ClientDetailResponse?
)

data class ClientSecretResponse(
    val secret: String
)
