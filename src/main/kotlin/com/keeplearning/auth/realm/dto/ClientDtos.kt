package com.keeplearning.auth.realm.dto

import java.time.Instant
import java.util.UUID

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
    val createdAt: Instant
)

data class ClientSecretResponse(
    val secret: String
)
