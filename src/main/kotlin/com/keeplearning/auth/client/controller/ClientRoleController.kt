package com.keeplearning.auth.client.controller

import com.keeplearning.auth.client.dto.*
import com.keeplearning.auth.client.service.ClientRoleService
import com.keeplearning.auth.client.service.ClientUserService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/clients/{clientId}/roles")
class ClientRoleController(
    private val roleService: ClientRoleService,
    private val clientUserService: ClientUserService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createRole(
        @PathVariable clientId: UUID,
        @RequestBody request: CreateClientRoleRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ClientRoleResponse {
        verifyAccess(jwt, clientId)
        return roleService.createRole(clientId, request, jwt.subject)
    }

    @GetMapping
    suspend fun listRoles(
        @PathVariable clientId: UUID,
        @AuthenticationPrincipal jwt: Jwt
    ): List<ClientRoleResponse> {
        verifyAccess(jwt, clientId)
        return roleService.listRoles(clientId)
    }

    @GetMapping("/{roleName}")
    suspend fun getRole(
        @PathVariable clientId: UUID,
        @PathVariable roleName: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ClientRoleResponse {
        verifyAccess(jwt, clientId)
        return roleService.getRole(clientId, roleName)
    }

    @PutMapping("/{roleName}")
    suspend fun updateRole(
        @PathVariable clientId: UUID,
        @PathVariable roleName: String,
        @RequestBody request: UpdateClientRoleRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ClientRoleResponse {
        verifyAccess(jwt, clientId)
        return roleService.updateRole(clientId, roleName, request)
    }

    @DeleteMapping("/{roleName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteRole(
        @PathVariable clientId: UUID,
        @PathVariable roleName: String,
        @AuthenticationPrincipal jwt: Jwt
    ) {
        verifyAccess(jwt, clientId)
        roleService.deleteRole(clientId, roleName)
    }

    private suspend fun verifyAccess(jwt: Jwt, clientId: UUID) {
        val hasAccess = clientUserService.verifyClientAccess(jwt, clientId)
        if (!hasAccess) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not authorized to manage roles for this client"
            )
        }
    }
}
