package com.keeplearning.auth.audit.service

import com.keeplearning.auth.audit.domain.EntityActionLog
import com.keeplearning.auth.audit.domain.EntityActionLogRepository
import com.keeplearning.auth.audit.domain.EntityType
import com.keeplearning.auth.audit.dto.AuditLogResponse
import com.keeplearning.auth.audit.dto.AuditPageResponse
import com.keeplearning.auth.audit.dto.toResponse
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service
class AuditQueryService(
    private val auditRepository: EntityActionLogRepository,
    private val objectMapper: ObjectMapper
) {

    /**
     * Get actions by the current user
     */
    suspend fun getMyActions(
        actorKeycloakId: String,
        page: Int,
        size: Int
    ): AuditPageResponse {
        val pageable = PageRequest.of(page, size)

        val logs = auditRepository.findByActorKeycloakIdOrderByCreatedAtDesc(actorKeycloakId, pageable)
            .collectList()
            .awaitSingle()

        val total = auditRepository.countByActorKeycloakId(actorKeycloakId).awaitSingle()

        return AuditPageResponse(
            content = logs.map { it.toResponseWithRevertCheck() },
            page = page,
            size = size,
            totalElements = total,
            totalPages = ((total + size - 1) / size).toInt()
        )
    }

    /**
     * Get all actions in a realm (for realm admins)
     */
    suspend fun getRealmActions(
        realmName: String,
        page: Int,
        size: Int
    ): AuditPageResponse {
        val pageable = PageRequest.of(page, size)

        val logs = auditRepository.findByRealmNameOrderByCreatedAtDesc(realmName, pageable)
            .collectList()
            .awaitSingle()

        val total = auditRepository.countByRealmName(realmName).awaitSingle()

        return AuditPageResponse(
            content = logs.map { it.toResponseWithRevertCheck() },
            page = page,
            size = size,
            totalElements = total,
            totalPages = ((total + size - 1) / size).toInt()
        )
    }

    /**
     * Get actions for a specific entity
     */
    suspend fun getEntityActions(
        entityType: EntityType,
        entityId: UUID,
        page: Int,
        size: Int
    ): AuditPageResponse {
        val pageable = PageRequest.of(page, size)

        val logs = auditRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable)
            .collectList()
            .awaitSingle()

        val total = auditRepository.countByEntityTypeAndEntityId(entityType, entityId).awaitSingle()

        return AuditPageResponse(
            content = logs.map { it.toResponseWithRevertCheck() },
            page = page,
            size = size,
            totalElements = total,
            totalPages = ((total + size - 1) / size).toInt()
        )
    }

    /**
     * Get all actions (for super admin)
     */
    suspend fun getAllActions(
        page: Int,
        size: Int
    ): AuditPageResponse {
        val pageable = PageRequest.of(page, size)

        val logs = auditRepository.findAllByOrderByCreatedAtDesc(pageable)
            .collectList()
            .awaitSingle()

        val total = auditRepository.count().awaitSingle()

        return AuditPageResponse(
            content = logs.map { it.toResponseWithRevertCheck() },
            page = page,
            size = size,
            totalElements = total,
            totalPages = ((total + size - 1) / size).toInt()
        )
    }

    /**
     * Get a single action by ID
     */
    suspend fun getAction(actionId: UUID): EntityActionLog? {
        return auditRepository.findById(actionId).awaitSingleOrNull()
    }

    /**
     * Check if an action can be reverted
     */
    suspend fun canRevert(actionId: UUID): Boolean {
        val action = auditRepository.findById(actionId).awaitSingleOrNull() ?: return false

        // Already reverted actions cannot be reverted again
        if (action.reverted) return false

        // Check if there are subsequent non-reverted actions on the same entity
        return auditRepository.canRevertAction(
            action.entityType.name,
            action.entityId,
            action.createdAt
        ).awaitSingle()
    }

    private suspend fun EntityActionLog.toResponseWithRevertCheck(): AuditLogResponse {
        val canRevert = !reverted && auditRepository.canRevertAction(
            entityType.name,
            entityId,
            createdAt
        ).awaitSingle()
        return toResponse(objectMapper, canRevert)
    }
}
