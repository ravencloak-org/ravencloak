package com.keeplearning.auth.keycloak.controller

import com.keeplearning.auth.keycloak.service.AccountUserRoleService
import org.springframework.web.bind.annotation.*

/**
 * Simplified user role management endpoints for ECS integration.
 * These endpoints use email instead of userId for easier integration.
 */
@RestController
@RequestMapping("/api/account/users")
class AccountUserRoleController(
    private val service: AccountUserRoleService
) {

    @GetMapping("/{email}/roles")
    suspend fun getUserRoles(
        @PathVariable email: String
    ): Map<String, Any> {
        return service.getUserRolesByEmail(email)
    }

    @PutMapping("/{email}/roles")
    suspend fun updateUserRoles(
        @PathVariable email: String,
        @RequestBody request: Map<String, List<String>>
    ): Map<String, Any> {
        val roles = request["roles"] ?: emptyList()
        val approvalScopes = request["approval_scopes"] ?: emptyList()
        return service.updateUserRolesByEmail(email, roles, approvalScopes)
    }
}
