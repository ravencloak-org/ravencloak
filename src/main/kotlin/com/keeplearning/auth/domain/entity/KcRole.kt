package com.keeplearning.auth.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("kc_roles")
data class KcRole(
    @Id
    val id: UUID? = null,
    @Column("realm_id")
    val realmId: UUID,
    @Column("client_id")
    val clientId: UUID? = null,  // NULL = realm role
    val name: String,
    val description: String? = null,
    val composite: Boolean = false,
    @Column("keycloak_id")
    val keycloakId: String,
    @Column("synced_at")
    val syncedAt: Instant = Instant.now(),
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant? = null
)
