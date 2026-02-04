package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.KcUserClient
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface KcUserClientRepository : R2dbcRepository<KcUserClient, UUID> {
    fun findByClientId(clientId: UUID): Flux<KcUserClient>
    fun findByClientIdAndUserEmail(clientId: UUID, userEmail: String): Mono<KcUserClient>
    fun findByClientIdAndUserKeycloakId(clientId: UUID, userKeycloakId: String): Mono<KcUserClient>
    fun findByRealmIdAndUserEmail(realmId: UUID, userEmail: String): Flux<KcUserClient>
    fun findByRealmIdAndUserKeycloakId(realmId: UUID, userKeycloakId: String): Flux<KcUserClient>
    fun existsByClientIdAndUserEmail(clientId: UUID, userEmail: String): Mono<Boolean>
    fun existsByClientIdAndUserKeycloakId(clientId: UUID, userKeycloakId: String): Mono<Boolean>
    fun deleteByClientIdAndUserEmail(clientId: UUID, userEmail: String): Mono<Void>
    fun deleteByClientId(clientId: UUID): Mono<Void>
}
