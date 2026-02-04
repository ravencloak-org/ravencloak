package com.keeplearning.auth.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("client_custom_roles")
data class ClientCustomRole(
    @Id
    val id: UUID? = null,
    @Column("client_id")
    val clientId: UUID,
    val name: String,
    @Column("display_name")
    val displayName: String? = null,
    val description: String? = null,
    @Column("created_by")
    val createdBy: UUID? = null,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant? = null
)
