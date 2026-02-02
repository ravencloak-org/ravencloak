package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.KcRole
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface KcRoleRepository : R2dbcRepository<KcRole, UUID> {
    fun findByRealmId(realmId: UUID): Flux<KcRole>
    fun findByRealmIdAndClientIdIsNull(realmId: UUID): Flux<KcRole>  // Realm roles only
    fun findByRealmIdAndNameAndClientIdIsNull(realmId: UUID, name: String): Mono<KcRole>  // Find specific realm role
    fun findByClientId(clientId: UUID): Flux<KcRole>  // Client roles
    fun findByClientIdAndName(clientId: UUID, name: String): Mono<KcRole>  // Find specific client role
    fun findByKeycloakId(keycloakId: String): Mono<KcRole>
    fun existsByKeycloakId(keycloakId: String): Mono<Boolean>
    fun deleteByRealmId(realmId: UUID): Mono<Void>
}
