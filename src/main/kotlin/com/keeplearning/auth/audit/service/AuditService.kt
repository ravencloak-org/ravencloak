package com.keeplearning.auth.audit.service

import com.keeplearning.auth.audit.domain.ActionType
import com.keeplearning.auth.audit.domain.EntityActionLog
import com.keeplearning.auth.audit.domain.EntityType
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.UUID

interface AuditService {
    /**
     * Log an entity action (CREATE, UPDATE, DELETE)
     *
     * @param actor The JWT authentication token containing actor information
     * @param actionType The type of action being performed
     * @param entityType The type of entity being acted upon
     * @param entityId The local database ID of the entity
     * @param entityName A human-readable name for the entity
     * @param realmName The name of the realm this action belongs to
     * @param realmId The local database ID of the realm (optional)
     * @param entityKeycloakId The Keycloak ID of the entity (optional)
     * @param beforeState The entity state before the action (for UPDATE/DELETE)
     * @param afterState The entity state after the action (for CREATE/UPDATE)
     * @return The created audit log entry
     */
    suspend fun logAction(
        actor: JwtAuthenticationToken,
        actionType: ActionType,
        entityType: EntityType,
        entityId: UUID,
        entityName: String,
        realmName: String,
        realmId: UUID? = null,
        entityKeycloakId: String? = null,
        beforeState: Any? = null,
        afterState: Any? = null
    ): EntityActionLog

    /**
     * Log an entity action without actor context (for system operations)
     */
    suspend fun logSystemAction(
        actionType: ActionType,
        entityType: EntityType,
        entityId: UUID,
        entityName: String,
        realmName: String,
        realmId: UUID? = null,
        entityKeycloakId: String? = null,
        beforeState: Any? = null,
        afterState: Any? = null
    ): EntityActionLog
}
