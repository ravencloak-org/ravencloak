package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.User
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface UserRepository : R2dbcRepository<User, UUID> {
    fun findByEmail(email: String): Mono<User>
    fun findByKeycloakUserId(keycloakUserId: String): Mono<User>
    fun findByAccountId(accountId: UUID): Flux<User>
    fun findByRealmId(realmId: UUID): Flux<User>
    fun findByAccountIdAndEmail(accountId: UUID, email: String): Mono<User>
    fun existsByEmail(email: String): Mono<Boolean>
    fun existsByKeycloakUserId(keycloakUserId: String): Mono<Boolean>
}
