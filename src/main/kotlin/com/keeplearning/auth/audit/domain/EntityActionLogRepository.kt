package com.keeplearning.auth.audit.domain

import org.springframework.data.domain.Pageable
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface EntityActionLogRepository : R2dbcRepository<EntityActionLog, UUID> {

    /**
     * Find all actions by the current user, ordered by most recent first
     */
    fun findByActorKeycloakIdOrderByCreatedAtDesc(
        actorKeycloakId: String,
        pageable: Pageable
    ): Flux<EntityActionLog>

    fun countByActorKeycloakId(actorKeycloakId: String): Mono<Long>

    /**
     * Find all actions in a realm, ordered by most recent first
     */
    fun findByRealmNameOrderByCreatedAtDesc(
        realmName: String,
        pageable: Pageable
    ): Flux<EntityActionLog>

    fun countByRealmName(realmName: String): Mono<Long>

    /**
     * Find all actions for a specific entity
     */
    fun findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
        entityType: EntityType,
        entityId: UUID,
        pageable: Pageable
    ): Flux<EntityActionLog>

    fun countByEntityTypeAndEntityId(entityType: EntityType, entityId: UUID): Mono<Long>

    /**
     * Find all actions, ordered by most recent first (for super admin)
     */
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Flux<EntityActionLog>

    /**
     * Find all actions filtered by entity type
     */
    fun findByEntityTypeOrderByCreatedAtDesc(
        entityType: EntityType,
        pageable: Pageable
    ): Flux<EntityActionLog>

    fun countByEntityType(entityType: EntityType): Mono<Long>

    /**
     * Find actions that have not been reverted
     */
    @Query("SELECT * FROM entity_action_log WHERE reverted = false ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    fun findNonRevertedActions(limit: Int, offset: Int): Flux<EntityActionLog>

    /**
     * Check if an action can be reverted (not already reverted and no subsequent actions on same entity)
     */
    @Query("""
        SELECT COUNT(*) = 0
        FROM entity_action_log
        WHERE entity_type = :entityType
        AND entity_id = :entityId
        AND created_at > :actionCreatedAt
        AND reverted = false
    """)
    fun canRevertAction(entityType: String, entityId: UUID, actionCreatedAt: java.time.Instant): Mono<Boolean>
}
