package com.keeplearning.auth.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("kc_groups")
data class KcGroup(
    @Id
    val id: UUID? = null,
    @Column("realm_id")
    val realmId: UUID,
    @Column("parent_id")
    val parentId: UUID? = null,
    val name: String,
    val path: String,
    val attributes: String = "{}",  // JSONB stored as String
    @Column("keycloak_id")
    val keycloakId: String,
    @Column("synced_at")
    val syncedAt: Instant = Instant.now(),
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant? = null
)
