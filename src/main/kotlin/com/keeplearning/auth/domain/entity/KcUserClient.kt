package com.keeplearning.auth.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("kc_user_clients")
data class KcUserClient(
    @Id
    val id: UUID? = null,
    @Column("realm_id")
    val realmId: UUID,
    @Column("user_keycloak_id")
    val userKeycloakId: String,
    @Column("user_email")
    val userEmail: String,
    @Column("client_id")
    val clientId: UUID,
    @Column("assigned_at")
    val assignedAt: Instant = Instant.now(),
    @Column("assigned_by_keycloak_id")
    val assignedByKeycloakId: String? = null
)
