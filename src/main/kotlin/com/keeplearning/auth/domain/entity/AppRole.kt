package com.keeplearning.auth.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("app_roles")
data class AppRole(
    @Id
    val id: UUID? = null,
    val name: String,
    @Column("display_name")
    val displayName: String,
    val description: String? = null,
    val scope: String, // 'global', 'realm', or 'client'
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant? = null
)
