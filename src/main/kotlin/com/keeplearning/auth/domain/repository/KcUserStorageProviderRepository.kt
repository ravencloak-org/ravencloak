package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.KcUserStorageProvider
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface KcUserStorageProviderRepository : R2dbcRepository<KcUserStorageProvider, UUID> {
    fun findByRealmId(realmId: UUID): Flux<KcUserStorageProvider>
    fun findByRealmIdAndName(realmId: UUID, name: String): Mono<KcUserStorageProvider>
    fun findByKeycloakId(keycloakId: String): Mono<KcUserStorageProvider>
    fun existsByKeycloakId(keycloakId: String): Mono<Boolean>
    fun deleteByRealmId(realmId: UUID): Mono<Void>
}
