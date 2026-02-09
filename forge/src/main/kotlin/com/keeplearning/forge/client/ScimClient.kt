package com.keeplearning.forge.client

import com.keeplearning.auth.scim.common.*
import com.keeplearning.forge.config.AuthProperties
import com.keeplearning.forge.exception.AuthException
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.util.UUID

class ScimClient(
    private val webClient: WebClient,
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
        return webClient.get()
            .uri { builder ->
                builder.path(basePath)
                    .queryParam("startIndex", startIndex)
                    .queryParam("count", count)
                filter?.let { builder.queryParam("filter", it) }
                builder.build()
            }
            .header("API-Version", properties.apiVersion)
            .retrieve()
            .onStatus({ it.isError }) { response -> handleError(response) }
            .bodyToMono<ScimListResponse>()
            .awaitSingle()
    }

    suspend fun getUser(userId: UUID): ScimUserResource {
        return webClient.get()
            .uri("$basePath/{userId}", userId)
            .header("API-Version", properties.apiVersion)
            .retrieve()
            .onStatus({ it.isError }) { response -> handleError(response) }
            .bodyToMono<ScimUserResource>()
            .awaitSingle()
    }

    suspend fun createUser(resource: ScimUserResource): ScimUserResource {
        return webClient.post()
            .uri(basePath)
            .header("API-Version", properties.apiVersion)
            .bodyValue(resource)
            .retrieve()
            .onStatus({ it.isError }) { response -> handleError(response) }
            .bodyToMono<ScimUserResource>()
            .awaitSingle()
    }

    suspend fun replaceUser(userId: UUID, resource: ScimUserResource): ScimUserResource {
        return webClient.put()
            .uri("$basePath/{userId}", userId)
            .header("API-Version", properties.apiVersion)
            .bodyValue(resource)
            .retrieve()
            .onStatus({ it.isError }) { response -> handleError(response) }
            .bodyToMono<ScimUserResource>()
            .awaitSingle()
    }

    suspend fun patchUser(userId: UUID, patchRequest: ScimPatchRequest): ScimUserResource {
        return webClient.patch()
            .uri("$basePath/{userId}", userId)
            .header("API-Version", properties.apiVersion)
            .bodyValue(patchRequest)
            .retrieve()
            .onStatus({ it.isError }) { response -> handleError(response) }
            .bodyToMono<ScimUserResource>()
            .awaitSingle()
    }

    suspend fun deleteUser(userId: UUID) {
        webClient.delete()
            .uri("$basePath/{userId}", userId)
            .header("API-Version", properties.apiVersion)
            .retrieve()
            .onStatus({ it.isError }) { response -> handleError(response) }
            .bodyToMono<Void>()
            .awaitSingleOrNull()
    }

    suspend fun bulkRequest(request: ScimBulkRequest): ScimBulkResponse {
        return webClient.post()
            .uri("$realmBasePath/Bulk")
            .header("API-Version", properties.apiVersion)
            .bodyValue(request)
            .retrieve()
            .onStatus({ it.isError }) { response -> handleError(response) }
            .bodyToMono<ScimBulkResponse>()
            .awaitSingle()
    }

    suspend fun getChecksum(): ScimChecksumResponse {
        return webClient.get()
            .uri("$basePath/checksum")
            .header("API-Version", properties.apiVersion)
            .retrieve()
            .onStatus({ it.isError }) { response -> handleError(response) }
            .bodyToMono<ScimChecksumResponse>()
            .awaitSingle()
    }

    private fun handleError(response: ClientResponse): Mono<Throwable> {
        return response.bodyToMono<ScimErrorResponse>()
            .map<Throwable> { error ->
                AuthException(
                    status = response.statusCode().value(),
                    scimError = error
                )
            }
            .onErrorResume {
                Mono.just(
                    AuthException(
                        status = response.statusCode().value(),
                        message = "Forge SDK error (HTTP ${response.statusCode().value()})"
                    )
                )
            }
    }
}
