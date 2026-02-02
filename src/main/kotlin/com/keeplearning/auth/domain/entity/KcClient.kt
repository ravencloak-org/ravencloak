package com.keeplearning.auth.domain.entity

import io.r2dbc.postgresql.codec.Json
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("kc_clients")
data class KcClient(
    @Id
    val id: UUID? = null,
    @Column("realm_id")
    val realmId: UUID,
    @Column("client_id")
    val clientId: String,
    val name: String? = null,
    val description: String? = null,
    val enabled: Boolean = true,
    @Column("public_client")
    val publicClient: Boolean = false,
    val protocol: String = "openid-connect",
    @Column("root_url")
    val rootUrl: String? = null,
    @Column("base_url")
    val baseUrl: String? = null,
    @Column("redirect_uris")
    val redirectUris: Json = Json.of("[]"),
    @Column("web_origins")
    val webOrigins: Json = Json.of("[]"),
    @Column("keycloak_id")
    val keycloakId: String,
    @Column("synced_at")
    val syncedAt: Instant = Instant.now(),
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant? = null
)
