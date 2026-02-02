package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.KcRealm
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface KcRealmRepository : R2dbcRepository<KcRealm, UUID> {
    fun findByRealmName(realmName: String): Mono<KcRealm>
    fun findByKeycloakId(keycloakId: String): Mono<KcRealm>
    fun findByAccountId(accountId: UUID): Flux<KcRealm>
    fun existsByRealmName(realmName: String): Mono<Boolean>
    fun existsByKeycloakId(keycloakId: String): Mono<Boolean>
    fun deleteByRealmName(realmName: String): Mono<Void>
}
