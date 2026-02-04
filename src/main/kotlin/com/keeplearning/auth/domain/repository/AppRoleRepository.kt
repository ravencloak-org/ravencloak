package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.AppRole
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface AppRoleRepository : R2dbcRepository<AppRole, UUID> {
    fun findByName(name: String): Mono<AppRole>
    fun findByScope(scope: String): Flux<AppRole>
    fun existsByName(name: String): Mono<Boolean>
}
