package com.keeplearning.auth.audit.service

import com.keeplearning.auth.audit.domain.ActionType
import com.keeplearning.auth.audit.domain.EntityActionLog
import com.keeplearning.auth.audit.domain.EntityActionLogRepository
import com.keeplearning.auth.audit.domain.EntityType
import com.keeplearning.auth.audit.dto.RevertResponse
import com.keeplearning.auth.domain.entity.KcClient
import com.keeplearning.auth.domain.entity.KcGroup
import com.keeplearning.auth.domain.entity.KcRole
import com.keeplearning.auth.domain.repository.KcClientRepository
import com.keeplearning.auth.domain.repository.KcGroupRepository
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.domain.repository.KcRoleRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.ClientRepresentation
import com.keeplearning.auth.keycloak.client.dto.GroupRepresentation
import com.keeplearning.auth.keycloak.client.dto.RoleRepresentation
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Service
class RevertService(
    private val auditRepository: EntityActionLogRepository,
    private val keycloakClient: KeycloakAdminClient,
    private val clientRepository: KcClientRepository,
    private val roleRepository: KcRoleRepository,
    private val groupRepository: KcGroupRepository,
    private val realmRepository: KcRealmRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(RevertService::class.java)

    suspend fun revertAction(
        actionId: UUID,
        reason: String,
        actor: JwtAuthenticationToken
    ): RevertResponse {
        val action = auditRepository.findById(actionId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Action not found")

        if (action.reverted) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Action has already been reverted")
        }

        // Check if can revert (no subsequent actions on same entity)
        val canRevert = auditRepository.canRevertAction(
            action.entityType.name,
            action.entityId,
            action.createdAt
        ).awaitSingle()

        if (!canRevert) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot revert: there are subsequent actions on this entity"
            )
        }

        return try {
            when (action.entityType) {
                EntityType.CLIENT -> revertClientAction(action, reason, actor)
                EntityType.ROLE -> revertRoleAction(action, reason, actor)
                EntityType.GROUP -> revertGroupAction(action, reason, actor)
                EntityType.IDP -> revertIdpAction(action, reason, actor)
                EntityType.REALM -> throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Realm actions cannot be reverted"
                )
                EntityType.USER -> throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User actions cannot be reverted through this endpoint"
                )
            }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to revert action $actionId: ${e.message}", e)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to revert action: ${e.message}"
            )
        }
    }

    private suspend fun revertClientAction(
        action: EntityActionLog,
        reason: String,
        actor: JwtAuthenticationToken
    ): RevertResponse {
        val realmName = action.realmName
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm not found")

        when (action.actionType) {
            ActionType.CREATE -> {
                // Delete the created client
                val keycloakId = action.entityKeycloakId
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Keycloak ID")

                keycloakClient.deleteClient(realmName, keycloakId)
                clientRepository.deleteById(action.entityId).awaitSingleOrNull()

                markAsReverted(action, reason, actor)
                return RevertResponse(true, "Client deleted (reverted CREATE)", null)
            }

            ActionType.DELETE -> {
                // Restore the deleted client
                val beforeState = parseState(action.beforeState?.asString())
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing before state")

                val clientRep = ClientRepresentation(
                    clientId = beforeState["clientId"] as String,
                    name = beforeState["name"] as? String,
                    description = beforeState["description"] as? String,
                    enabled = beforeState["enabled"] as? Boolean ?: true,
                    publicClient = beforeState["publicClient"] as? Boolean ?: true,
                    standardFlowEnabled = beforeState["standardFlowEnabled"] as? Boolean ?: true,
                    directAccessGrantsEnabled = beforeState["directAccessGrantsEnabled"] as? Boolean ?: false,
                    serviceAccountsEnabled = beforeState["serviceAccountsEnabled"] as? Boolean ?: false,
                    rootUrl = beforeState["rootUrl"] as? String,
                    baseUrl = beforeState["baseUrl"] as? String,
                    redirectUris = (beforeState["redirectUris"] as? List<*>)?.filterIsInstance<String>(),
                    webOrigins = (beforeState["webOrigins"] as? List<*>)?.filterIsInstance<String>()
                )

                val newKeycloakId = keycloakClient.createClient(realmName, clientRep)

                val client = KcClient(
                    id = action.entityId,
                    realmId = realm.id!!,
                    clientId = clientRep.clientId,
                    name = clientRep.name,
                    description = clientRep.description,
                    enabled = clientRep.enabled ?: true,
                    publicClient = clientRep.publicClient ?: true,
                    standardFlowEnabled = clientRep.standardFlowEnabled ?: true,
                    directAccessGrantsEnabled = clientRep.directAccessGrantsEnabled ?: false,
                    serviceAccountsEnabled = clientRep.serviceAccountsEnabled ?: false,
                    rootUrl = clientRep.rootUrl,
                    baseUrl = clientRep.baseUrl,
                    redirectUris = Json.of(objectMapper.writeValueAsString(clientRep.redirectUris ?: emptyList<String>())),
                    webOrigins = Json.of(objectMapper.writeValueAsString(clientRep.webOrigins ?: emptyList<String>())),
                    keycloakId = newKeycloakId
                )
                clientRepository.save(client).awaitSingle()

                val revertLogId = markAsReverted(action, reason, actor)
                return RevertResponse(true, "Client restored (reverted DELETE)", revertLogId)
            }

            ActionType.UPDATE -> {
                // Restore to before state
                val beforeState = parseState(action.beforeState?.asString())
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing before state")

                val keycloakId = action.entityKeycloakId
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Keycloak ID")

                val clientRep = ClientRepresentation(
                    clientId = beforeState["clientId"] as String,
                    name = beforeState["name"] as? String,
                    description = beforeState["description"] as? String,
                    enabled = beforeState["enabled"] as? Boolean ?: true,
                    publicClient = beforeState["publicClient"] as? Boolean ?: true,
                    standardFlowEnabled = beforeState["standardFlowEnabled"] as? Boolean ?: true,
                    directAccessGrantsEnabled = beforeState["directAccessGrantsEnabled"] as? Boolean ?: false,
                    serviceAccountsEnabled = beforeState["serviceAccountsEnabled"] as? Boolean ?: false,
                    rootUrl = beforeState["rootUrl"] as? String,
                    baseUrl = beforeState["baseUrl"] as? String,
                    redirectUris = (beforeState["redirectUris"] as? List<*>)?.filterIsInstance<String>(),
                    webOrigins = (beforeState["webOrigins"] as? List<*>)?.filterIsInstance<String>()
                )

                keycloakClient.updateClient(realmName, keycloakId, clientRep)

                val existingClient = clientRepository.findById(action.entityId).awaitSingleOrNull()
                if (existingClient != null) {
                    clientRepository.save(
                        existingClient.copy(
                            name = clientRep.name,
                            description = clientRep.description,
                            enabled = clientRep.enabled ?: true,
                            publicClient = clientRep.publicClient ?: true,
                            standardFlowEnabled = clientRep.standardFlowEnabled ?: true,
                            directAccessGrantsEnabled = clientRep.directAccessGrantsEnabled ?: false,
                            serviceAccountsEnabled = clientRep.serviceAccountsEnabled ?: false,
                            rootUrl = clientRep.rootUrl,
                            baseUrl = clientRep.baseUrl,
                            redirectUris = Json.of(objectMapper.writeValueAsString(clientRep.redirectUris ?: emptyList<String>())),
                            webOrigins = Json.of(objectMapper.writeValueAsString(clientRep.webOrigins ?: emptyList<String>())),
                            updatedAt = Instant.now()
                        )
                    ).awaitSingle()
                }

                val revertLogId = markAsReverted(action, reason, actor)
                return RevertResponse(true, "Client restored to previous state (reverted UPDATE)", revertLogId)
            }
        }
    }

    private suspend fun revertRoleAction(
        action: EntityActionLog,
        reason: String,
        actor: JwtAuthenticationToken
    ): RevertResponse {
        val realmName = action.realmName
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm not found")

        when (action.actionType) {
            ActionType.CREATE -> {
                val beforeState = parseState(action.afterState?.asString())
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing after state")

                val roleName = beforeState["name"] as String
                val clientId = beforeState["clientId"] as? UUID

                if (clientId != null) {
                    val client = clientRepository.findById(clientId).awaitSingleOrNull()
                    if (client != null) {
                        keycloakClient.deleteClientRole(realmName, client.keycloakId, roleName)
                    }
                } else {
                    keycloakClient.deleteRealmRole(realmName, roleName)
                }

                roleRepository.deleteById(action.entityId).awaitSingleOrNull()
                markAsReverted(action, reason, actor)
                return RevertResponse(true, "Role deleted (reverted CREATE)", null)
            }

            ActionType.DELETE -> {
                val beforeState = parseState(action.beforeState?.asString())
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing before state")

                val roleName = beforeState["name"] as String
                val description = beforeState["description"] as? String
                val clientId = beforeState["clientId"] as? UUID

                val roleRep = RoleRepresentation(
                    name = roleName,
                    description = description
                )

                val keycloakId = if (clientId != null) {
                    val client = clientRepository.findById(clientId).awaitSingleOrNull()
                        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Associated client not found")
                    keycloakClient.createClientRole(realmName, client.keycloakId, roleRep)
                    "${client.keycloakId}/$roleName"
                } else {
                    keycloakClient.createRealmRole(realmName, roleRep)
                    val created = keycloakClient.getRealmRole(realmName, roleName)
                    created.id ?: roleName
                }

                val role = KcRole(
                    id = action.entityId,
                    realmId = realm.id!!,
                    clientId = clientId,
                    name = roleName,
                    description = description,
                    composite = false,
                    keycloakId = keycloakId
                )
                roleRepository.save(role).awaitSingle()

                val revertLogId = markAsReverted(action, reason, actor)
                return RevertResponse(true, "Role restored (reverted DELETE)", revertLogId)
            }

            ActionType.UPDATE -> {
                val beforeState = parseState(action.beforeState?.asString())
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing before state")

                val roleName = beforeState["name"] as String
                val description = beforeState["description"] as? String
                val clientId = beforeState["clientId"] as? UUID

                val roleRep = RoleRepresentation(
                    name = roleName,
                    description = description
                )

                if (clientId != null) {
                    // Client role updates require delete + recreate in Keycloak
                    val client = clientRepository.findById(clientId).awaitSingleOrNull()
                    if (client != null) {
                        keycloakClient.deleteClientRole(realmName, client.keycloakId, roleName)
                        keycloakClient.createClientRole(realmName, client.keycloakId, roleRep)
                    }
                } else {
                    keycloakClient.updateRealmRole(realmName, roleName, roleRep)
                }

                val existingRole = roleRepository.findById(action.entityId).awaitSingleOrNull()
                if (existingRole != null) {
                    roleRepository.save(
                        existingRole.copy(
                            description = description,
                            updatedAt = Instant.now()
                        )
                    ).awaitSingle()
                }

                val revertLogId = markAsReverted(action, reason, actor)
                return RevertResponse(true, "Role restored to previous state (reverted UPDATE)", revertLogId)
            }
        }
    }

    private suspend fun revertGroupAction(
        action: EntityActionLog,
        reason: String,
        actor: JwtAuthenticationToken
    ): RevertResponse {
        val realmName = action.realmName
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm not found")

        when (action.actionType) {
            ActionType.CREATE -> {
                val keycloakId = action.entityKeycloakId
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Keycloak ID")

                keycloakClient.deleteGroup(realmName, keycloakId)
                groupRepository.deleteById(action.entityId).awaitSingleOrNull()

                markAsReverted(action, reason, actor)
                return RevertResponse(true, "Group deleted (reverted CREATE)", null)
            }

            ActionType.DELETE -> {
                val beforeState = parseState(action.beforeState?.asString())
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing before state")

                val groupName = beforeState["name"] as String

                val groupRep = GroupRepresentation(name = groupName)
                val newKeycloakId = keycloakClient.createGroup(realmName, groupRep)

                val group = KcGroup(
                    id = action.entityId,
                    realmId = realm.id!!,
                    parentId = null,
                    name = groupName,
                    path = "/$groupName",
                    keycloakId = newKeycloakId
                )
                groupRepository.save(group).awaitSingle()

                val revertLogId = markAsReverted(action, reason, actor)
                return RevertResponse(true, "Group restored (reverted DELETE)", revertLogId)
            }

            ActionType.UPDATE -> {
                val beforeState = parseState(action.beforeState?.asString())
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing before state")

                val keycloakId = action.entityKeycloakId
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Keycloak ID")

                val groupName = beforeState["name"] as String
                val groupRep = GroupRepresentation(name = groupName)
                keycloakClient.updateGroup(realmName, keycloakId, groupRep)

                val existingGroup = groupRepository.findById(action.entityId).awaitSingleOrNull()
                if (existingGroup != null) {
                    groupRepository.save(
                        existingGroup.copy(
                            name = groupName,
                            path = beforeState["path"] as? String ?: existingGroup.path,
                            updatedAt = Instant.now()
                        )
                    ).awaitSingle()
                }

                val revertLogId = markAsReverted(action, reason, actor)
                return RevertResponse(true, "Group restored to previous state (reverted UPDATE)", revertLogId)
            }
        }
    }

    private suspend fun revertIdpAction(
        action: EntityActionLog,
        reason: String,
        actor: JwtAuthenticationToken
    ): RevertResponse {
        // IDPs are Keycloak-only, simpler revert
        throw ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "IDP revert is not yet implemented"
        )
    }

    private suspend fun markAsReverted(
        action: EntityActionLog,
        reason: String,
        actor: JwtAuthenticationToken
    ): UUID? {
        val jwt = actor.token
        val actorKeycloakId = jwt.subject

        // Update the original action as reverted
        val revertedAction = action.copy(
            reverted = true,
            revertedAt = Instant.now(),
            revertedByKeycloakId = actorKeycloakId,
            revertReason = reason
        )
        auditRepository.save(revertedAction).awaitSingle()

        // Create a new audit log entry for the revert action
        val revertLog = EntityActionLog(
            actorKeycloakId = actorKeycloakId,
            actorEmail = jwt.claims["email"] as? String,
            actorDisplayName = jwt.claims["name"] as? String
                ?: jwt.claims["preferred_username"] as? String,
            actorIssuer = jwt.issuer?.toString(),
            actionType = when (action.actionType) {
                ActionType.CREATE -> ActionType.DELETE
                ActionType.DELETE -> ActionType.CREATE
                ActionType.UPDATE -> ActionType.UPDATE
            },
            entityType = action.entityType,
            entityId = action.entityId,
            entityKeycloakId = action.entityKeycloakId,
            entityName = action.entityName,
            realmName = action.realmName,
            realmId = action.realmId,
            beforeState = action.afterState,
            afterState = action.beforeState,
            revertOfActionId = action.id
        )

        val savedRevertLog = auditRepository.save(revertLog).awaitSingle()
        logger.info("Reverted action ${action.id}, created revert log ${savedRevertLog.id}")

        return savedRevertLog.id
    }

    private fun parseState(json: String?): Map<String, Any?>? {
        if (json == null) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            logger.warn("Failed to parse state JSON: ${e.message}")
            null
        }
    }
}
