package com.keeplearning.auth.domain.repository

import com.keeplearning.auth.domain.entity.KcGroup
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface KcGroupRepository : R2dbcRepository<KcGroup, UUID> {
    fun findByRealmId(realmId: UUID): Flux<KcGroup>
    fun findByRealmIdAndParentIdIsNull(realmId: UUID): Flux<KcGroup>  // Root groups
    fun findByParentId(parentId: UUID): Flux<KcGroup>  // Child groups
    fun findByRealmIdAndPath(realmId: UUID, path: String): Mono<KcGroup>
    fun findByKeycloakId(keycloakId: String): Mono<KcGroup>
    fun existsByKeycloakId(keycloakId: String): Mono<Boolean>
    fun deleteByRealmId(realmId: UUID): Mono<Void>
}
