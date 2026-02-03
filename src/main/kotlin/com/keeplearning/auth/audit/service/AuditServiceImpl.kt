package com.keeplearning.auth.audit.service

import com.keeplearning.auth.audit.domain.ActionType
import com.keeplearning.auth.audit.domain.EntityActionLog
import com.keeplearning.auth.audit.domain.EntityActionLogRepository
import com.keeplearning.auth.audit.domain.EntityType
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service
class AuditServiceImpl(
    private val auditRepository: EntityActionLogRepository,
    private val objectMapper: ObjectMapper
) : AuditService {

    private val logger = LoggerFactory.getLogger(AuditServiceImpl::class.java)

    override suspend fun logAction(
        actor: JwtAuthenticationToken,
        actionType: ActionType,
        entityType: EntityType,
        entityId: UUID,
        entityName: String,
        realmName: String,
        realmId: UUID?,
        entityKeycloakId: String?,
        beforeState: Any?,
        afterState: Any?
    ): EntityActionLog {
        val jwt = actor.token

        // Extract actor information from JWT claims
        val actorKeycloakId = jwt.subject
        val actorEmail = jwt.claims["email"] as? String
        val actorDisplayName = jwt.claims["name"] as? String
            ?: jwt.claims["preferred_username"] as? String
        val actorIssuer = jwt.issuer?.toString()

        return createAuditLog(
            actorKeycloakId = actorKeycloakId,
            actorEmail = actorEmail,
            actorDisplayName = actorDisplayName,
            actorIssuer = actorIssuer,
            actionType = actionType,
            entityType = entityType,
            entityId = entityId,
            entityName = entityName,
            realmName = realmName,
            realmId = realmId,
            entityKeycloakId = entityKeycloakId,
            beforeState = beforeState,
            afterState = afterState
        )
    }

    override suspend fun logSystemAction(
        actionType: ActionType,
        entityType: EntityType,
        entityId: UUID,
        entityName: String,
        realmName: String,
        realmId: UUID?,
        entityKeycloakId: String?,
        beforeState: Any?,
        afterState: Any?
    ): EntityActionLog {
        return createAuditLog(
            actorKeycloakId = "SYSTEM",
            actorEmail = null,
            actorDisplayName = "System",
            actorIssuer = null,
            actionType = actionType,
            entityType = entityType,
            entityId = entityId,
            entityName = entityName,
            realmName = realmName,
            realmId = realmId,
            entityKeycloakId = entityKeycloakId,
            beforeState = beforeState,
            afterState = afterState
        )
    }

    private suspend fun createAuditLog(
        actorKeycloakId: String,
        actorEmail: String?,
        actorDisplayName: String?,
        actorIssuer: String?,
        actionType: ActionType,
        entityType: EntityType,
        entityId: UUID,
        entityName: String,
        realmName: String,
        realmId: UUID?,
        entityKeycloakId: String?,
        beforeState: Any?,
        afterState: Any?
    ): EntityActionLog {
        // Serialize states to JSON
        val beforeJson = beforeState?.let {
            Json.of(objectMapper.writeValueAsString(it))
        }
        val afterJson = afterState?.let {
            Json.of(objectMapper.writeValueAsString(it))
        }

        // Compute changed fields for UPDATE operations
        val changedFields = if (actionType == ActionType.UPDATE && beforeState != null && afterState != null) {
            computeChangedFields(beforeState, afterState)
        } else {
            null
        }

        val auditLog = EntityActionLog(
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
            beforeState = beforeJson,
            afterState = afterJson,
            changedFields = changedFields
        )

        val saved = auditRepository.save(auditLog).awaitSingle()
        logger.debug(
            "Audit log created: {} {} {} '{}' in realm '{}'",
            actionType, entityType, entityId, entityName, realmName
        )
        return saved
    }

    private fun computeChangedFields(before: Any, after: Any): Array<String> {
        return try {
            val beforeMap = objectMapper.convertValue(before, Map::class.java) as Map<String, Any?>
            val afterMap = objectMapper.convertValue(after, Map::class.java) as Map<String, Any?>

            val changedFields = mutableListOf<String>()

            // Find fields that exist in both and have different values
            for ((key, afterValue) in afterMap) {
                val beforeValue = beforeMap[key]
                if (beforeValue != afterValue) {
                    changedFields.add(key)
                }
            }

            // Find fields that exist only in before (deleted fields)
            for (key in beforeMap.keys) {
                if (!afterMap.containsKey(key)) {
                    changedFields.add(key)
                }
            }

            changedFields.toTypedArray()
        } catch (e: Exception) {
            logger.warn("Failed to compute changed fields: ${e.message}")
            emptyArray()
        }
    }
}
