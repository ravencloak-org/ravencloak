package com.keeplearning.auth.audit.dto

import com.keeplearning.auth.audit.domain.ActionType
import com.keeplearning.auth.audit.domain.EntityActionLog
import com.keeplearning.auth.audit.domain.EntityType
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

data class AuditLogResponse(
    val id: UUID,
    val actorKeycloakId: String,
    val actorEmail: String?,
    val actorDisplayName: String?,
    val actorIssuer: String?,
    val actionType: ActionType,
    val entityType: EntityType,
    val entityId: UUID,
    val entityKeycloakId: String?,
    val entityName: String,
    val realmName: String,
    val realmId: UUID?,
    val beforeState: Map<String, Any?>?,
    val afterState: Map<String, Any?>?,
    val changedFields: List<String>?,
    val reverted: Boolean,
    val revertedAt: Instant?,
    val revertedByKeycloakId: String?,
    val revertReason: String?,
    val revertOfActionId: UUID?,
    val createdAt: Instant,
    val canRevert: Boolean = false
)

data class AuditPageResponse(
    val content: List<AuditLogResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class RevertRequest(
    val reason: String
)

data class RevertResponse(
    val success: Boolean,
    val message: String,
    val newActionId: UUID?
)

fun EntityActionLog.toResponse(objectMapper: ObjectMapper, canRevert: Boolean = false): AuditLogResponse {
    val beforeStateMap = beforeState?.let {
        try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(it.asString(), Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            null
        }
    }

    val afterStateMap = afterState?.let {
        try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(it.asString(), Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            null
        }
    }

    return AuditLogResponse(
        id = id!!,
        actorKeycloakId = actorKeycloakId,
        actorEmail = actorEmail,
        actorDisplayName = actorDisplayName,
        actorIssuer = actorIssuer,
        actionType = actionType,
        entityType = entityType,
        entityId = entityId,
        entityKeycloakId = entityKeycloakId,
        entityName = entityName,
        realmName = realmName,
        realmId = realmId,
        beforeState = beforeStateMap,
        afterState = afterStateMap,
        changedFields = changedFields?.toList(),
        reverted = reverted,
        revertedAt = revertedAt,
        revertedByKeycloakId = revertedByKeycloakId,
        revertReason = revertReason,
        revertOfActionId = revertOfActionId,
        createdAt = createdAt,
        canRevert = canRevert
    )
}
