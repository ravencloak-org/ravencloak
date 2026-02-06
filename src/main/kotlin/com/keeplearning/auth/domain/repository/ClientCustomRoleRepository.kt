package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.ClientCustomRole
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface ClientCustomRoleRepository : R2dbcRepository<ClientCustomRole, UUID> {
    fun findByClientId(clientId: UUID): Flux<ClientCustomRole>
    fun findByClientIdAndName(clientId: UUID, name: String): Mono<ClientCustomRole>
    fun findByClientIdAndIsDefaultTrue(clientId: UUID): Mono<ClientCustomRole>
    fun existsByClientIdAndName(clientId: UUID, name: String): Mono<Boolean>
    fun deleteByClientId(clientId: UUID): Mono<Void>
}
