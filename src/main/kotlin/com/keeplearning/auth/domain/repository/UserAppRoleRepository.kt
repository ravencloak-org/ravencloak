package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.UserAppRole
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface UserAppRoleRepository : R2dbcRepository<UserAppRole, UUID> {
    fun findByUserId(userId: UUID): Flux<UserAppRole>
    fun findByUserIdAndAppRoleId(userId: UUID, appRoleId: UUID): Flux<UserAppRole>
    fun findByUserIdAndRealmId(userId: UUID, realmId: UUID): Flux<UserAppRole>
    fun findByUserIdAndClientId(userId: UUID, clientId: UUID): Flux<UserAppRole>
    fun findByAppRoleId(appRoleId: UUID): Flux<UserAppRole>
    fun findByRealmId(realmId: UUID): Flux<UserAppRole>
    fun deleteByUserId(userId: UUID): Mono<Void>
    fun deleteByUserIdAndAppRoleId(userId: UUID, appRoleId: UUID): Mono<Void>
}
