package com.keeplearning.auth.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("user_client_custom_roles")
data class UserClientCustomRole(
    @Id
    val id: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    @Column("custom_role_id")
    val customRoleId: UUID,
    @Column("assigned_by")
    val assignedBy: UUID? = null,
    @Column("assigned_at")
    val assignedAt: Instant = Instant.now()
)
