package com.keeplearning.auth.keycloak.sync

import tools.jackson.databind.ObjectMapper
import com.keeplearning.auth.domain.entity.*
import com.keeplearning.auth.domain.repository.*
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.GroupRepresentation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class KeycloakSyncService(
    private val keycloakClient: KeycloakAdminClient,
    private val realmRepository: KcRealmRepository,
    private val clientRepository: KcClientRepository,
    private val roleRepository: KcRoleRepository,
    private val groupRepository: KcGroupRepository,
    private val userStorageProviderRepository: KcUserStorageProviderRepository,
    private val syncLogRepository: KcSyncLogRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(KeycloakSyncService::class.java)

    suspend fun syncAll(): SyncResult {
        logger.info("Starting full Keycloak sync...")
        return try {
            val realmResult = syncRealms()
            val realms = realmRepository.findAll().collectList().awaitSingle()

            val entityResults = realms.map { realm ->
                syncRealmEntities(realm)
            }

            val success = realmResult.success && entityResults.all { it.success }
            logger.info("Full sync completed: ${realmResult.count} realms processed, success=$success")

            SyncResult(
                realmsProcessed = realmResult.count,
                success = success
            )
        } catch (e: Exception) {
            logger.error("Full sync failed", e)
            SyncResult(realmsProcessed = 0, success = false)
        }
    }

    suspend fun syncRealms(): EntitySyncResult {
        return logSync(null, SyncEntityType.REALM) {
            val realms = keycloakClient.getRealms()
                .filter { it.realm != "master" }

            var count = 0
            for (realmRep in realms) {
                val existing = realmRepository.findByKeycloakId(realmRep.id!!).awaitSingleOrNull()
                if (existing != null) {
                    realmRepository.save(
                        existing.copy(
                            realmName = realmRep.realm,
                            displayName = realmRep.displayName,
                            enabled = realmRep.enabled,
                            syncedAt = Instant.now(),
                            updatedAt = Instant.now()
                        )
                    ).awaitSingle()
                } else {
                    realmRepository.save(
                        KcRealm(
                            realmName = realmRep.realm,
                            displayName = realmRep.displayName,
                            enabled = realmRep.enabled,
                            keycloakId = realmRep.id,
                            syncedAt = Instant.now()
                        )
                    ).awaitSingle()
                }
                count++
            }

            EntitySyncResult(SyncEntityType.REALM, count, true)
        }
    }

    suspend fun syncRealmEntities(realm: KcRealm): RealmSyncResult = coroutineScope {
        val clientsDeferred = async { syncClients(realm) }
        val rolesDeferred = async { syncRealmRoles(realm) }
        val groupsDeferred = async { syncGroups(realm) }
        val providersDeferred = async { syncUserStorageProviders(realm) }

        val clients = clientsDeferred.await()
        val roles = rolesDeferred.await()
        val groups = groupsDeferred.await()
        val providers = providersDeferred.await()

        RealmSyncResult(
            realmName = realm.realmName,
            clients = clients,
            roles = roles,
            groups = groups,
            userStorageProviders = providers,
            success = clients.success && roles.success && groups.success && providers.success
        )
    }

    suspend fun syncClients(realm: KcRealm): EntitySyncResult {
        return logSync(realm.id, SyncEntityType.CLIENT) {
            val clients = keycloakClient.getClients(realm.realmName)

            var count = 0
            for (clientRep in clients) {
                val existing = clientRepository.findByKeycloakId(clientRep.id!!).awaitSingleOrNull()
                if (existing != null) {
                    clientRepository.save(
                        existing.copy(
                            clientId = clientRep.clientId,
                            name = clientRep.name,
                            description = clientRep.description,
                            enabled = clientRep.enabled,
                            publicClient = clientRep.publicClient,
                            protocol = clientRep.protocol,
                            rootUrl = clientRep.rootUrl,
                            baseUrl = clientRep.baseUrl,
                            redirectUris = objectMapper.writeValueAsString(clientRep.redirectUris ?: emptyList<String>()),
                            webOrigins = objectMapper.writeValueAsString(clientRep.webOrigins ?: emptyList<String>()),
                            syncedAt = Instant.now(),
                            updatedAt = Instant.now()
                        )
                    ).awaitSingle()
                } else {
                    clientRepository.save(
                        KcClient(
                            realmId = realm.id!!,
                            clientId = clientRep.clientId,
                            name = clientRep.name,
                            description = clientRep.description,
                            enabled = clientRep.enabled,
                            publicClient = clientRep.publicClient,
                            protocol = clientRep.protocol,
                            rootUrl = clientRep.rootUrl,
                            baseUrl = clientRep.baseUrl,
                            redirectUris = objectMapper.writeValueAsString(clientRep.redirectUris ?: emptyList<String>()),
                            webOrigins = objectMapper.writeValueAsString(clientRep.webOrigins ?: emptyList<String>()),
                            keycloakId = clientRep.id,
                            syncedAt = Instant.now()
                        )
                    ).awaitSingle()
                }
                count++
            }

            EntitySyncResult(SyncEntityType.CLIENT, count, true)
        }
    }

    suspend fun syncRealmRoles(realm: KcRealm): EntitySyncResult {
        return logSync(realm.id, SyncEntityType.ROLE) {
            val roles = keycloakClient.getRealmRoles(realm.realmName)

            var count = 0
            for (roleRep in roles) {
                val existing = roleRepository.findByKeycloakId(roleRep.id!!).awaitSingleOrNull()
                if (existing != null) {
                    roleRepository.save(
                        existing.copy(
                            name = roleRep.name,
                            description = roleRep.description,
                            composite = roleRep.composite,
                            syncedAt = Instant.now(),
                            updatedAt = Instant.now()
                        )
                    ).awaitSingle()
                } else {
                    roleRepository.save(
                        KcRole(
                            realmId = realm.id!!,
                            clientId = null,
                            name = roleRep.name,
                            description = roleRep.description,
                            composite = roleRep.composite,
                            keycloakId = roleRep.id,
                            syncedAt = Instant.now()
                        )
                    ).awaitSingle()
                }
                count++
            }

            EntitySyncResult(SyncEntityType.ROLE, count, true)
        }
    }

    suspend fun syncGroups(realm: KcRealm): EntitySyncResult {
        return logSync(realm.id, SyncEntityType.GROUP) {
            val groups = keycloakClient.getGroups(realm.realmName)

            var count = 0
            for (groupRep in groups) {
                count += syncGroupRecursive(realm.id!!, groupRep, null)
            }

            EntitySyncResult(SyncEntityType.GROUP, count, true)
        }
    }

    private suspend fun syncGroupRecursive(
        realmId: UUID,
        groupRep: GroupRepresentation,
        parentId: UUID?
    ): Int {
        val attributesJson = objectMapper.writeValueAsString(groupRep.attributes ?: emptyMap<String, List<String>>())

        val existing = groupRepository.findByKeycloakId(groupRep.id!!).awaitSingleOrNull()
        val savedGroup = if (existing != null) {
            groupRepository.save(
                existing.copy(
                    name = groupRep.name,
                    path = groupRep.path ?: "/${groupRep.name}",
                    parentId = parentId,
                    attributes = attributesJson,
                    syncedAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            ).awaitSingle()
        } else {
            groupRepository.save(
                KcGroup(
                    realmId = realmId,
                    parentId = parentId,
                    name = groupRep.name,
                    path = groupRep.path ?: "/${groupRep.name}",
                    attributes = attributesJson,
                    keycloakId = groupRep.id,
                    syncedAt = Instant.now()
                )
            ).awaitSingle()
        }

        var count = 1
        groupRep.subGroups?.forEach { subGroup ->
            count += syncGroupRecursive(realmId, subGroup, savedGroup.id)
        }

        return count
    }

    suspend fun syncUserStorageProviders(realm: KcRealm): EntitySyncResult {
        return logSync(realm.id, SyncEntityType.USER) {
            val providers = keycloakClient.getUserStorageProviders(realm.realmName)

            var count = 0
            for (componentRep in providers) {
                val existing = userStorageProviderRepository.findByKeycloakId(componentRep.id!!).awaitSingleOrNull()
                if (existing != null) {
                    userStorageProviderRepository.save(
                        existing.copy(
                            name = componentRep.name,
                            providerId = componentRep.providerId,
                            priority = componentRep.config?.get("priority")?.firstOrNull()?.toIntOrNull() ?: 0,
                            config = objectMapper.writeValueAsString(componentRep.config ?: emptyMap<String, List<String>>()),
                            syncedAt = Instant.now(),
                            updatedAt = Instant.now()
                        )
                    ).awaitSingle()
                } else {
                    userStorageProviderRepository.save(
                        KcUserStorageProvider(
                            realmId = realm.id!!,
                            name = componentRep.name,
                            providerId = componentRep.providerId,
                            priority = componentRep.config?.get("priority")?.firstOrNull()?.toIntOrNull() ?: 0,
                            config = objectMapper.writeValueAsString(componentRep.config ?: emptyMap<String, List<String>>()),
                            keycloakId = componentRep.id,
                            syncedAt = Instant.now()
                        )
                    ).awaitSingle()
                }
                count++
            }

            EntitySyncResult(SyncEntityType.USER, count, true)
        }
    }

    suspend fun syncRealm(realmName: String): RealmSyncResult {
        var realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()

        if (realm == null) {
            val realmRep = keycloakClient.getRealm(realmName)
            realm = realmRepository.save(
                KcRealm(
                    realmName = realmRep.realm,
                    displayName = realmRep.displayName,
                    enabled = realmRep.enabled,
                    keycloakId = realmRep.id!!,
                    syncedAt = Instant.now()
                )
            ).awaitSingle()
        }

        return syncRealmEntities(realm)
    }

    private suspend fun logSync(
        realmId: UUID?,
        entityType: SyncEntityType,
        syncOperation: suspend () -> EntitySyncResult
    ): EntitySyncResult {
        val syncLog = KcSyncLog(
            realmId = realmId,
            entityType = entityType.name,
            syncDirection = SyncDirection.FROM_KC.name,
            status = SyncStatus.STARTED.name,
            startedAt = Instant.now()
        )

        val log = syncLogRepository.save(syncLog).awaitSingle()

        return try {
            val result = syncOperation()
            syncLogRepository.save(
                log.copy(
                    status = SyncStatus.COMPLETED.name,
                    entitiesProcessed = result.count,
                    completedAt = Instant.now()
                )
            ).awaitSingle()
            result
        } catch (e: Exception) {
            syncLogRepository.save(
                log.copy(
                    status = SyncStatus.FAILED.name,
                    errorMessage = e.message,
                    completedAt = Instant.now()
                )
            ).awaitSingle()
            EntitySyncResult(entityType, 0, false, e.message)
        }
    }
}

data class EntitySyncResult(
    val entityType: SyncEntityType,
    val count: Int,
    val success: Boolean,
    val error: String? = null
)

data class RealmSyncResult(
    val realmName: String,
    val clients: EntitySyncResult,
    val roles: EntitySyncResult,
    val groups: EntitySyncResult,
    val userStorageProviders: EntitySyncResult,
    val success: Boolean
)

data class SyncResult(
    val realmsProcessed: Int,
    val success: Boolean
)
