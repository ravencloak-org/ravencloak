package com.keeplearning.auth.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

enum class SyncEntityType {
    REALM, CLIENT, CLIENT_SCOPE, GROUP, ROLE, USER, IDP
}

enum class SyncDirection {
    FROM_KC, TO_KC
}

enum class SyncStatus {
    STARTED, COMPLETED, FAILED
}

@Table("kc_sync_log")
data class KcSyncLog(
    @Id
    val id: UUID? = null,
    @Column("realm_id")
    val realmId: UUID? = null,
    @Column("entity_type")
    val entityType: String,
    @Column("sync_direction")
    val syncDirection: String,
    val status: String,
    @Column("entities_processed")
    val entitiesProcessed: Int = 0,
    @Column("error_message")
    val errorMessage: String? = null,
    @Column("started_at")
    val startedAt: Instant = Instant.now(),
    @Column("completed_at")
    val completedAt: Instant? = null
)
