package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.KcSyncLog
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface KcSyncLogRepository : R2dbcRepository<KcSyncLog, UUID> {
    fun findByRealmId(realmId: UUID): Flux<KcSyncLog>
    fun findByEntityType(entityType: String): Flux<KcSyncLog>
    fun findFirstByRealmIdAndEntityTypeOrderByStartedAtDesc(
        realmId: UUID,
        entityType: String
    ): Mono<KcSyncLog>
    fun findFirstByEntityTypeOrderByStartedAtDesc(entityType: String): Mono<KcSyncLog>
}
