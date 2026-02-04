package com.keeplearning.auth.keycloak.client.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("token_type") val tokenType: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RealmRepresentation(
    val id: String? = null,
    val realm: String,
    val displayName: String? = null,
    val enabled: Boolean = true,
    val registrationAllowed: Boolean = false,
    val loginWithEmailAllowed: Boolean = true,
    val duplicateEmailsAllowed: Boolean = false,
    val resetPasswordAllowed: Boolean = true,
    val editUsernameAllowed: Boolean = false,
    val bruteForceProtected: Boolean = true,
    val attributes: Map<String, String>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClientRepresentation(
    val id: String? = null,
    val clientId: String,
    val name: String? = null,
    val description: String? = null,
    val enabled: Boolean = true,
    val publicClient: Boolean = false,
    val protocol: String = "openid-connect",
    val rootUrl: String? = null,
    val baseUrl: String? = null,
    val redirectUris: List<String>? = null,
    val webOrigins: List<String>? = null,
    val directAccessGrantsEnabled: Boolean = false,
    val standardFlowEnabled: Boolean = true,
    val implicitFlowEnabled: Boolean = false,
    val serviceAccountsEnabled: Boolean = false,
    val authorizationServicesEnabled: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RoleRepresentation(
    val id: String? = null,
    val name: String,
    val description: String? = null,
    val composite: Boolean = false,
    val clientRole: Boolean = false,
    val containerId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GroupRepresentation(
    val id: String? = null,
    val name: String,
    val path: String? = null,
    val attributes: Map<String, List<String>>? = null,
    val subGroups: List<GroupRepresentation>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ComponentRepresentation(
    val id: String? = null,
    val name: String,
    val providerId: String,
    val providerType: String = "org.keycloak.storage.UserStorageProvider",
    val parentId: String? = null,
    val config: Map<String, List<String>>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserRepresentation(
    val id: String? = null,
    val username: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val enabled: Boolean = true,
    val emailVerified: Boolean = false,
    val attributes: Map<String, List<String>>? = null,
    val credentials: List<CredentialRepresentation>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CredentialRepresentation(
    val type: String = "password",
    val value: String,
    val temporary: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClientSecretResponse(
    val type: String? = null,
    val value: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IdentityProviderRepresentation(
    val alias: String,
    val displayName: String? = null,
    val providerId: String,
    val enabled: Boolean = true,
    val trustEmail: Boolean = false,
    val storeToken: Boolean = false,
    val addReadTokenRoleOnCreate: Boolean = false,
    val authenticateByDefault: Boolean = false,
    val linkOnly: Boolean = false,
    val firstBrokerLoginFlowAlias: String? = null,
    val postBrokerLoginFlowAlias: String? = null,
    val config: Map<String, String>? = null
)

data class CreateRealmRequest(
    val realmName: String,
    val displayName: String? = null,
    val accountId: String? = null,
    val enableUserStorageSpi: Boolean = false,
    val attributes: Map<String, String>? = null,
    val defaultRoles: List<String>? = null,
    val defaultClients: List<CreateClientRequest>? = null
)

data class CreateClientRequest(
    val clientId: String,
    val name: String? = null,
    val publicClient: Boolean = true,
    val redirectUris: List<String>? = null,
    val webOrigins: List<String>? = null
)

data class UpdateRealmRequest(
    val displayName: String? = null,
    val enabled: Boolean? = null,
    val spiEnabled: Boolean? = null,
    val spiApiUrl: String? = null,
    val attributes: Map<String, String>? = null
)
