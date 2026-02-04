package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.UserClientCustomRole
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface UserClientCustomRoleRepository : R2dbcRepository<UserClientCustomRole, UUID> {
    fun findByUserId(userId: UUID): Flux<UserClientCustomRole>
    fun findByCustomRoleId(customRoleId: UUID): Flux<UserClientCustomRole>
    fun findByUserIdAndCustomRoleId(userId: UUID, customRoleId: UUID): Mono<UserClientCustomRole>
    fun deleteByUserId(userId: UUID): Mono<Void>
    fun deleteByUserIdAndCustomRoleId(userId: UUID, customRoleId: UUID): Mono<Void>
}
