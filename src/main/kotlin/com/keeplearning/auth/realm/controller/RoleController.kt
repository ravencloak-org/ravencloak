package com.keeplearning.auth.realm.controller

import com.keeplearning.auth.realm.dto.*
import com.keeplearning.auth.realm.service.RoleService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/super/realms/{realmName}")
class RoleController(
    private val roleService: RoleService
) {

    // Realm Roles

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createRealmRole(
        @PathVariable realmName: String,
        @RequestBody request: CreateRoleRequest,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ): RoleResponse {
        return roleService.createRealmRole(realmName, request, actor)
    }

    @GetMapping("/roles")
    suspend fun listRealmRoles(@PathVariable realmName: String): List<RoleResponse> {
        return roleService.listRealmRoles(realmName)
    }

    @GetMapping("/roles/{roleName}")
    suspend fun getRealmRole(
        @PathVariable realmName: String,
        @PathVariable roleName: String
    ): RoleResponse {
        return roleService.getRealmRole(realmName, roleName)
    }

    @PutMapping("/roles/{roleName}")
    suspend fun updateRealmRole(
        @PathVariable realmName: String,
        @PathVariable roleName: String,
        @RequestBody request: UpdateRoleRequest,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ): RoleResponse {
        return roleService.updateRealmRole(realmName, roleName, request, actor)
    }

    @DeleteMapping("/roles/{roleName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteRealmRole(
        @PathVariable realmName: String,
        @PathVariable roleName: String,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ) {
        roleService.deleteRealmRole(realmName, roleName, actor)
    }

    // Client Roles

    @PostMapping("/clients/{clientId}/roles")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createClientRole(
        @PathVariable realmName: String,
        @PathVariable clientId: String,
        @RequestBody request: CreateRoleRequest,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ): RoleResponse {
        return roleService.createClientRole(realmName, clientId, request, actor)
    }

    @GetMapping("/clients/{clientId}/roles")
    suspend fun listClientRoles(
        @PathVariable realmName: String,
        @PathVariable clientId: String
    ): List<RoleResponse> {
        return roleService.listClientRoles(realmName, clientId)
    }

    @DeleteMapping("/clients/{clientId}/roles/{roleName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteClientRole(
        @PathVariable realmName: String,
        @PathVariable clientId: String,
        @PathVariable roleName: String,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ) {
        roleService.deleteClientRole(realmName, clientId, roleName, actor)
    }
}
