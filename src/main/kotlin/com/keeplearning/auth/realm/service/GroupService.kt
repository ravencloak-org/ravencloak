package com.keeplearning.auth.realm.service

import com.keeplearning.auth.audit.domain.ActionType
import com.keeplearning.auth.audit.domain.EntityType
import com.keeplearning.auth.audit.service.AuditService
import com.keeplearning.auth.domain.entity.KcGroup
import com.keeplearning.auth.domain.repository.KcGroupRepository
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.GroupRepresentation
import com.keeplearning.auth.realm.dto.*
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

@Service
class GroupService(
    private val keycloakClient: KeycloakAdminClient,
    private val groupRepository: KcGroupRepository,
    private val realmRepository: KcRealmRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(GroupService::class.java)

    suspend fun createGroup(
        realmName: String,
        request: CreateGroupRequest,
        actor: JwtAuthenticationToken? = null
    ): GroupResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        // Create in Keycloak
        val groupRep = GroupRepresentation(
            name = request.name,
            attributes = request.attributes
        )
        val keycloakId = keycloakClient.createGroup(realmName, groupRep)

        // Save to local database
        val group = groupRepository.save(
            KcGroup(
                realmId = realm.id!!,
                parentId = null,
                name = request.name,
                path = "/${request.name}",
                attributes = request.attributes?.let { Json.of(objectMapper.writeValueAsString(it)) },
                keycloakId = keycloakId
            )
        ).awaitSingle()

        // Log audit trail
        if (actor != null) {
            auditService.logAction(
                actor = actor,
                actionType = ActionType.CREATE,
                entityType = EntityType.GROUP,
                entityId = group.id!!,
                entityName = group.name,
                realmName = realmName,
                realmId = realm.id,
                entityKeycloakId = keycloakId,
                afterState = group.toAuditState()
            )
        }

        logger.info("Created group: ${request.name} in realm: $realmName")
        return group.toResponse()
    }

    suspend fun createSubgroup(
        realmName: String,
        parentGroupId: String,
        request: CreateGroupRequest,
        actor: JwtAuthenticationToken? = null
    ): GroupResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val parentGroup = groupRepository.findByKeycloakId(parentGroupId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Parent group not found")

        // Create in Keycloak
        val groupRep = GroupRepresentation(
            name = request.name,
            attributes = request.attributes
        )
        val keycloakId = keycloakClient.createSubgroup(realmName, parentGroupId, groupRep)

        // Save to local database
        val group = groupRepository.save(
            KcGroup(
                realmId = realm.id!!,
                parentId = parentGroup.id,
                name = request.name,
                path = "${parentGroup.path}/${request.name}",
                attributes = request.attributes?.let { Json.of(objectMapper.writeValueAsString(it)) },
                keycloakId = keycloakId
            )
        ).awaitSingle()

        // Log audit trail
        if (actor != null) {
            auditService.logAction(
                actor = actor,
                actionType = ActionType.CREATE,
                entityType = EntityType.GROUP,
                entityId = group.id!!,
                entityName = group.path,
                realmName = realmName,
                realmId = realm.id,
                entityKeycloakId = keycloakId,
                afterState = group.toAuditState()
            )
        }

        logger.info("Created subgroup: ${request.name} under parent: ${parentGroup.name} in realm: $realmName")
        return group.toResponse()
    }

    suspend fun getGroup(realmName: String, groupId: String): GroupResponse {
        realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val group = groupRepository.findByKeycloakId(groupId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found")

        return group.toResponse()
    }

    suspend fun listGroups(realmName: String): List<GroupResponse> {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        return groupRepository.findByRealmIdAndParentIdIsNull(realm.id!!)
            .collectList()
            .awaitSingle()
            .map { it.toResponse() }
    }

    suspend fun updateGroup(
        realmName: String,
        groupId: String,
        request: UpdateGroupRequest,
        actor: JwtAuthenticationToken? = null
    ): GroupResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val group = groupRepository.findByKeycloakId(groupId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found")

        // Capture before state for audit
        val beforeState = group.toAuditState()

        // Get current group from Keycloak
        val currentGroup = keycloakClient.getGroup(realmName, groupId)

        // Update in Keycloak
        val updatedGroupRep = currentGroup.copy(
            name = request.name ?: currentGroup.name,
            attributes = request.attributes ?: currentGroup.attributes
        )
        keycloakClient.updateGroup(realmName, groupId, updatedGroupRep)

        // Update local database
        val newPath = if (request.name != null && group.parentId == null) {
            "/${request.name}"
        } else if (request.name != null) {
            group.path.substringBeforeLast("/") + "/${request.name}"
        } else {
            group.path
        }

        val updatedGroup = groupRepository.save(
            group.copy(
                name = request.name ?: group.name,
                path = newPath,
                attributes = request.attributes?.let { Json.of(objectMapper.writeValueAsString(it)) } ?: group.attributes,
                updatedAt = Instant.now()
            )
        ).awaitSingle()

        // Log audit trail
        if (actor != null) {
            auditService.logAction(
                actor = actor,
                actionType = ActionType.UPDATE,
                entityType = EntityType.GROUP,
                entityId = updatedGroup.id!!,
                entityName = updatedGroup.path,
                realmName = realmName,
                realmId = realm.id,
                entityKeycloakId = updatedGroup.keycloakId,
                beforeState = beforeState,
                afterState = updatedGroup.toAuditState()
            )
        }

        logger.info("Updated group: $groupId in realm: $realmName")
        return updatedGroup.toResponse()
    }

    suspend fun deleteGroup(
        realmName: String,
        groupId: String,
        actor: JwtAuthenticationToken? = null
    ) {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Realm '$realmName' not found")

        val group = groupRepository.findByKeycloakId(groupId).awaitSingleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found")

        // Capture before state for audit
        val beforeState = group.toAuditState()

        // Delete from Keycloak
        keycloakClient.deleteGroup(realmName, groupId)

        // Delete from local database
        groupRepository.delete(group).awaitSingleOrNull()

        // Log audit trail
        if (actor != null) {
            auditService.logAction(
                actor = actor,
                actionType = ActionType.DELETE,
                entityType = EntityType.GROUP,
                entityId = group.id!!,
                entityName = group.path,
                realmName = realmName,
                realmId = realm.id,
                entityKeycloakId = group.keycloakId,
                beforeState = beforeState
            )
        }

        logger.info("Deleted group: $groupId from realm: $realmName")
    }

    private fun KcGroup.toResponse() = GroupResponse(
        id = id!!,
        name = name,
        path = path
    )

    private fun KcGroup.toAuditState(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "path" to path,
        "parentId" to parentId,
        "keycloakId" to keycloakId,
        "attributes" to attributes?.asString()
    )
}
