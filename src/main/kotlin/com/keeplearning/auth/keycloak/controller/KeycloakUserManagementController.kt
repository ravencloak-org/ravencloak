package com.keeplearning.auth.keycloak.controller

import com.keeplearning.auth.keycloak.client.dto.AssignClientRolesRequest
import com.keeplearning.auth.keycloak.client.dto.SetUserAttributeRequest
import com.keeplearning.auth.keycloak.client.dto.UserAttributeResponse
import com.keeplearning.auth.keycloak.client.dto.UserRoleMappingResponse
import com.keeplearning.auth.keycloak.service.KeycloakUserManagementService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/account/realms/{realmName}/users/{userId}")
class KeycloakUserManagementController(
    private val service: KeycloakUserManagementService
) {

    @GetMapping("/client-roles/{clientId}")
    suspend fun getUserClientRoles(
        @PathVariable realmName: String,
        @PathVariable userId: String,
        @PathVariable clientId: String
    ): UserRoleMappingResponse {
        return service.getUserClientRoles(realmName, userId, clientId)
    }

    @PostMapping("/client-roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun assignClientRoles(
        @PathVariable realmName: String,
        @PathVariable userId: String,
        @RequestBody request: AssignClientRolesRequest
    ) {
        service.assignClientRoles(realmName, userId, request)
    }

    @DeleteMapping("/client-roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun removeClientRoles(
        @PathVariable realmName: String,
        @PathVariable userId: String,
        @RequestBody request: AssignClientRolesRequest
    ) {
        service.removeClientRoles(realmName, userId, request)
    }

    @GetMapping("/attributes")
    suspend fun getUserAttributes(
        @PathVariable realmName: String,
        @PathVariable userId: String
    ): UserAttributeResponse {
        return service.getUserAttributes(realmName, userId)
    }

    @PutMapping("/attributes/{attributeName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun setUserAttribute(
        @PathVariable realmName: String,
        @PathVariable userId: String,
        @PathVariable attributeName: String,
        @RequestBody request: SetUserAttributeRequest
    ) {
        service.setUserAttribute(realmName, userId, attributeName, request.values)
    }

    @DeleteMapping("/attributes/{attributeName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun removeUserAttribute(
        @PathVariable realmName: String,
        @PathVariable userId: String,
        @PathVariable attributeName: String
    ) {
        service.removeUserAttribute(realmName, userId, attributeName)
    }
}
