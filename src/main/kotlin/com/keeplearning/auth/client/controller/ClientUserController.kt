package com.keeplearning.auth.client.controller

import com.keeplearning.auth.client.dto.*
import com.keeplearning.auth.client.service.ClientUserService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/clients/{clientId}/users")
class ClientUserController(
    private val clientUserService: ClientUserService
) {

    /**
     * Add authorized users to a client.
     * Authenticated via client credentials (service account) or super admin.
     */
    @PostMapping
    suspend fun addUsers(
        @PathVariable clientId: UUID,
        @RequestBody request: AddUsersRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): AddUsersResponse {
        verifyAccess(jwt, clientId)

        val actorId = jwt.subject
        return clientUserService.addUsers(clientId, request, actorId)
    }

    /**
     * Remove authorized users from a client.
     * Authenticated via client credentials (service account) or super admin.
     */
    @DeleteMapping
    suspend fun removeUsers(
        @PathVariable clientId: UUID,
        @RequestBody request: RemoveUsersRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): RemoveUsersResponse {
        verifyAccess(jwt, clientId)

        return clientUserService.removeUsers(clientId, request)
    }

    /**
     * List all authorized users for a client.
     * Authenticated via client credentials (service account) or super admin.
     */
    @GetMapping
    suspend fun listUsers(
        @PathVariable clientId: UUID,
        @AuthenticationPrincipal jwt: Jwt
    ): List<ClientUserResponse> {
        verifyAccess(jwt, clientId)

        return clientUserService.listUsers(clientId)
    }

    /**
     * Check if a user is authorized for a client.
     * This endpoint is more permissive - any authenticated caller can check.
     */
    @GetMapping("/{email}/authorized")
    suspend fun checkAuthorization(
        @PathVariable clientId: UUID,
        @PathVariable email: String,
        @AuthenticationPrincipal jwt: Jwt
    ): AuthorizationCheckResponse {
        // For authorization checks, we allow any authenticated caller
        // This is useful for the SPI to check user authorization
        return clientUserService.isUserAuthorized(clientId, email)
    }

    private suspend fun verifyAccess(jwt: Jwt, clientId: UUID) {
        val hasAccess = clientUserService.verifyClientAccess(jwt, clientId)
        if (!hasAccess) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not authorized to manage users for this client"
            )
        }
    }
}
