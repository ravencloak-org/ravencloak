package com.keeplearning.auth.scim

import com.keeplearning.auth.scim.common.ScimListResponse
import com.keeplearning.auth.scim.common.ScimPatchRequest
import com.keeplearning.auth.scim.common.ScimUserResource
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/scim/v2/realms/{realmName}/Users")
class ScimUserController(
    private val scimUserService: ScimUserService
) {

    @GetMapping(version = "1.0+")
    suspend fun listUsers(
        @PathVariable realmName: String,
        @RequestParam(required = false) filter: String?,
        @RequestParam(defaultValue = "1") startIndex: Int,
        @RequestParam(defaultValue = "100") count: Int,
        request: ServerHttpRequest
    ): ScimListResponse {
        val baseUrl = extractBaseUrl(request)
        return scimUserService.listUsers(realmName, filter, startIndex, count, baseUrl)
    }

    @GetMapping("/{userId}", version = "1.0+")
    suspend fun getUser(
        @PathVariable realmName: String,
        @PathVariable userId: UUID,
        request: ServerHttpRequest
    ): ScimUserResource {
        val baseUrl = extractBaseUrl(request)
        return scimUserService.getUser(realmName, userId, baseUrl)
    }

    @PostMapping(version = "1.0+")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createUser(
        @PathVariable realmName: String,
        @RequestBody resource: ScimUserResource,
        request: ServerHttpRequest
    ): ScimUserResource {
        val baseUrl = extractBaseUrl(request)
        return scimUserService.createUser(realmName, resource, baseUrl)
    }

    @PutMapping("/{userId}", version = "1.0+")
    suspend fun replaceUser(
        @PathVariable realmName: String,
        @PathVariable userId: UUID,
        @RequestBody resource: ScimUserResource,
        request: ServerHttpRequest
    ): ScimUserResource {
        val baseUrl = extractBaseUrl(request)
        return scimUserService.replaceUser(realmName, userId, resource, baseUrl)
    }

    @PatchMapping("/{userId}", version = "1.0+")
    suspend fun patchUser(
        @PathVariable realmName: String,
        @PathVariable userId: UUID,
        @RequestBody request: ScimPatchRequest,
        httpRequest: ServerHttpRequest
    ): ScimUserResource {
        val baseUrl = extractBaseUrl(httpRequest)
        return scimUserService.patchUser(realmName, userId, request, baseUrl)
    }

    @DeleteMapping("/{userId}", version = "1.0+")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteUser(
        @PathVariable realmName: String,
        @PathVariable userId: UUID
    ) {
        scimUserService.deleteUser(realmName, userId)
    }

    private fun extractBaseUrl(request: ServerHttpRequest): String {
        val uri = request.uri
        return "${uri.scheme}://${uri.authority}"
    }
}
