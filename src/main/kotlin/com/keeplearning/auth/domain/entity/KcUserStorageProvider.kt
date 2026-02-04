package com.keeplearning.auth.domain.entity

import io.r2dbc.postgresql.codec.Json
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("kc_user_storage_providers")
data class KcUserStorageProvider(
    @Id
    val id: UUID? = null,
    @Column("realm_id")
    val realmId: UUID,
    val name: String,
    @Column("provider_id")
    val providerId: String,
    val priority: Int = 0,
    val config: Json = Json.of("{}"),
    @Column("keycloak_id")
    val keycloakId: String,
    @Column("synced_at")
    val syncedAt: Instant = Instant.now(),
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant? = null
)
