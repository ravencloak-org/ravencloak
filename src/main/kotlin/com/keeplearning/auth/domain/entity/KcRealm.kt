package com.keeplearning.auth.domain.entity

import io.r2dbc.postgresql.codec.Json
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("kc_realms")
data class KcRealm(
    @Id
    val id: UUID? = null,
    @Column("account_id")
    val accountId: UUID? = null,
    @Column("realm_name")
    val realmName: String,
    @Column("display_name")
    val displayName: String? = null,
    val enabled: Boolean = true,
    @Column("spi_enabled")
    val spiEnabled: Boolean = false,
    @Column("spi_api_url")
    val spiApiUrl: String? = null,
    val attributes: Json? = null,
    @Column("keycloak_id")
    val keycloakId: String,
    @Column("synced_at")
    val syncedAt: Instant = Instant.now(),
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant? = null
)
