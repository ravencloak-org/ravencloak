package com.keeplearning.auth.audit.controller

import com.keeplearning.auth.audit.domain.EntityType
import com.keeplearning.auth.audit.dto.AuditPageResponse
import com.keeplearning.auth.audit.dto.RevertRequest
import com.keeplearning.auth.audit.dto.RevertResponse
import com.keeplearning.auth.audit.service.AuditQueryService
import com.keeplearning.auth.audit.service.RevertService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class AuditController(
    private val auditQueryService: AuditQueryService,
    private val revertService: RevertService
) {

    /**
     * Get the current user's own action history
     */
    @GetMapping("/api/audit/my-actions")
    suspend fun getMyActions(
        @AuthenticationPrincipal actor: JwtAuthenticationToken,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): AuditPageResponse {
        val actorKeycloakId = actor.token.subject
        return auditQueryService.getMyActions(actorKeycloakId, page, size)
    }

    /**
     * Get all actions in a realm (requires super admin)
     */
    @GetMapping("/api/super/realms/{realmName}/audit")
    suspend fun getRealmAuditLog(
        @PathVariable realmName: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): AuditPageResponse {
        return auditQueryService.getRealmActions(realmName, page, size)
    }

    /**
     * Get all actions (super admin only)
     */
    @GetMapping("/api/super/audit")
    suspend fun getAllAuditLogs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): AuditPageResponse {
        return auditQueryService.getAllActions(page, size)
    }

    /**
     * Get entity-specific action history
     */
    @GetMapping("/api/super/realms/{realmName}/audit/entities/{entityType}/{entityId}")
    suspend fun getEntityAuditLog(
        @PathVariable realmName: String,
        @PathVariable entityType: EntityType,
        @PathVariable entityId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): AuditPageResponse {
        return auditQueryService.getEntityActions(entityType, entityId, page, size)
    }

    /**
     * Revert an action (restores entity in both Keycloak and local DB)
     */
    @PostMapping("/api/super/realms/{realmName}/audit/{actionId}/revert")
    @ResponseStatus(HttpStatus.OK)
    suspend fun revertAction(
        @PathVariable realmName: String,
        @PathVariable actionId: UUID,
        @RequestBody request: RevertRequest,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ): RevertResponse {
        return revertService.revertAction(actionId, request.reason, actor)
    }

    /**
     * Check if an action can be reverted
     */
    @GetMapping("/api/super/realms/{realmName}/audit/{actionId}/can-revert")
    suspend fun canRevertAction(
        @PathVariable realmName: String,
        @PathVariable actionId: UUID
    ): Map<String, Boolean> {
        return mapOf("canRevert" to auditQueryService.canRevert(actionId))
    }
}
