package com.keeplearning.auth.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("users")
data class User(
    @Id
    val id: UUID? = null,
    @Column("keycloak_user_id")
    val keycloakUserId: String,
    val email: String,
    @Column("display_name")
    val displayName: String? = null,
    @Column("account_id")
    val accountId: UUID,
    @Column("realm_id")
    val realmId: UUID? = null,
    @Column("first_name")
    val firstName: String? = null,
    @Column("last_name")
    val lastName: String? = null,
    val phone: String? = null,
    val bio: String? = null,
    @Column("job_title")
    val jobTitle: String? = null,
    val department: String? = null,
    @Column("avatar_url")
    val avatarUrl: String? = null,
    val status: String = "ACTIVE",
    @Column("last_login_at")
    val lastLoginAt: Instant? = null,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant? = null
)
