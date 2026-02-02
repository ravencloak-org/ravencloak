package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.KcClient
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface KcClientRepository : R2dbcRepository<KcClient, UUID> {
    fun findByRealmId(realmId: UUID): Flux<KcClient>
    fun findByRealmIdAndClientId(realmId: UUID, clientId: String): Mono<KcClient>
    fun findByKeycloakId(keycloakId: String): Mono<KcClient>
    fun existsByKeycloakId(keycloakId: String): Mono<Boolean>
    fun deleteByRealmId(realmId: UUID): Mono<Void>
}
