package com.keeplearning.forge.client

import com.keeplearning.auth.scim.common.*
import com.keeplearning.forge.config.AuthProperties
import com.keeplearning.forge.exception.AuthException
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

class ScimClient(
    private val restClient: RestClient,
    private val properties: AuthProperties
) {
    private val basePath: String
        get() = "/api/scim/v2/realms/${properties.realmName}/Users"

    private val realmBasePath: String
        get() = "/api/scim/v2/realms/${properties.realmName}"

    suspend fun listUsers(
        filter: String? = null,
        startIndex: Int = 1,
        count: Int = 100
    ): ScimListResponse {
        return try {
            restClient.get()
                .uri { builder ->
                    builder.path(basePath)
                        .queryParam("startIndex", startIndex)
                        .queryParam("count", count)
                    filter?.let { builder.queryParam("filter", it) }
                    builder.build()
                }
                .header("API-Version", properties.apiVersion)
                .retrieve()
                .body(ScimListResponse::class.java)
                ?: throw AuthException(status = 500, message = "Empty response from SCIM list users")
        } catch (e: RestClientResponseException) {
            throw handleError(e)
        }
    }

    suspend fun getUser(userId: UUID): ScimUserResource {
        return try {
            restClient.get()
                .uri("$basePath/{userId}", userId)
                .header("API-Version", properties.apiVersion)
                .retrieve()
                .body(ScimUserResource::class.java)
                ?: throw AuthException(status = 500, message = "Empty response from SCIM get user")
        } catch (e: RestClientResponseException) {
            throw handleError(e)
        }
    }

    suspend fun createUser(resource: ScimUserResource): ScimUserResource {
        return try {
            restClient.post()
                .uri(basePath)
                .header("API-Version", properties.apiVersion)
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource)
                .retrieve()
                .body(ScimUserResource::class.java)
                ?: throw AuthException(status = 500, message = "Empty response from SCIM create user")
        } catch (e: RestClientResponseException) {
            throw handleError(e)
        }
    }

    suspend fun replaceUser(userId: UUID, resource: ScimUserResource): ScimUserResource {
        return try {
            restClient.put()
                .uri("$basePath/{userId}", userId)
                .header("API-Version", properties.apiVersion)
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource)
                .retrieve()
                .body(ScimUserResource::class.java)
                ?: throw AuthException(status = 500, message = "Empty response from SCIM replace user")
        } catch (e: RestClientResponseException) {
            throw handleError(e)
        }
    }

    suspend fun patchUser(userId: UUID, patchRequest: ScimPatchRequest): ScimUserResource {
        return try {
            restClient.patch()
                .uri("$basePath/{userId}", userId)
                .header("API-Version", properties.apiVersion)
                .contentType(MediaType.APPLICATION_JSON)
                .body(patchRequest)
                .retrieve()
                .body(ScimUserResource::class.java)
                ?: throw AuthException(status = 500, message = "Empty response from SCIM patch user")
        } catch (e: RestClientResponseException) {
            throw handleError(e)
        }
    }

    suspend fun deleteUser(userId: UUID) {
        try {
            restClient.delete()
                .uri("$basePath/{userId}", userId)
                .header("API-Version", properties.apiVersion)
                .retrieve()
                .toBodilessEntity()
        } catch (e: RestClientResponseException) {
            throw handleError(e)
        }
    }

    suspend fun bulkRequest(request: ScimBulkRequest): ScimBulkResponse {
        return try {
            restClient.post()
                .uri("$realmBasePath/Bulk")
                .header("API-Version", properties.apiVersion)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ScimBulkResponse::class.java)
                ?: throw AuthException(status = 500, message = "Empty response from SCIM bulk request")
        } catch (e: RestClientResponseException) {
            throw handleError(e)
        }
    }

    suspend fun getChecksum(): ScimChecksumResponse {
        return try {
            restClient.get()
                .uri("$basePath/checksum")
                .header("API-Version", properties.apiVersion)
                .retrieve()
                .body(ScimChecksumResponse::class.java)
                ?: throw AuthException(status = 500, message = "Empty response from SCIM checksum")
        } catch (e: RestClientResponseException) {
            throw handleError(e)
        }
    }

    private fun handleError(e: RestClientResponseException): AuthException {
        return try {
            val scimError = e.getResponseBodyAs(ScimErrorResponse::class.java)
            AuthException(
                status = e.statusCode.value(),
                scimError = scimError
            )
        } catch (_: Exception) {
            AuthException(
                status = e.statusCode.value(),
                message = "Forge SDK error (HTTP ${e.statusCode.value()})"
            )
        }
    }
}
