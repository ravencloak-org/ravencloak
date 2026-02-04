package com.keeplearning.auth.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("user_app_roles")
data class UserAppRole(
    @Id
    val id: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    @Column("app_role_id")
    val appRoleId: UUID,
    @Column("realm_id")
    val realmId: UUID? = null, // NULL for global roles
    @Column("client_id")
    val clientId: UUID? = null, // NULL for realm/global roles
    @Column("assigned_by")
    val assignedBy: UUID? = null,
    @Column("assigned_at")
    val assignedAt: Instant = Instant.now()
)
