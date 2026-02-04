package com.keeplearning.auth.audit.domain

import io.r2dbc.postgresql.codec.Json
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("entity_action_log")
data class EntityActionLog(
    @Id
    val id: UUID? = null,

    // Actor (from JWT)
    @Column("actor_keycloak_id")
    val actorKeycloakId: String,
    @Column("actor_email")
    val actorEmail: String? = null,
    @Column("actor_display_name")
    val actorDisplayName: String? = null,
    @Column("actor_issuer")
    val actorIssuer: String? = null,

    // Action
    @Column("action_type")
    val actionType: ActionType,
    @Column("entity_type")
    val entityType: EntityType,

    // Entity
    @Column("entity_id")
    val entityId: UUID,
    @Column("entity_keycloak_id")
    val entityKeycloakId: String? = null,
    @Column("entity_name")
    val entityName: String,
    @Column("realm_name")
    val realmName: String,
    @Column("realm_id")
    val realmId: UUID? = null,

    // Before/After snapshots (JSONB)
    @Column("before_state")
    val beforeState: Json? = null,
    @Column("after_state")
    val afterState: Json? = null,
    @Column("changed_fields")
    val changedFields: Array<String>? = null,

    // Revert tracking
    val reverted: Boolean = false,
    @Column("reverted_at")
    val revertedAt: Instant? = null,
    @Column("reverted_by_keycloak_id")
    val revertedByKeycloakId: String? = null,
    @Column("revert_reason")
    val revertReason: String? = null,
    @Column("revert_of_action_id")
    val revertOfActionId: UUID? = null,

    @Column("created_at")
    val createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EntityActionLog
        return id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}

enum class ActionType {
    CREATE, UPDATE, DELETE
}

enum class EntityType {
    CLIENT, REALM, ROLE, GROUP, IDP, USER
}
