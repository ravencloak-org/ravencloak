package com.keeplearning.forge.repository

import com.keeplearning.auth.scim.common.*
import com.keeplearning.forge.client.ScimClient
import com.keeplearning.forge.exception.ForgeException
import java.time.Instant
import java.util.UUID

class DefaultAuthRepository<T : AuthUser>(
    private val scimClient: ScimClient,
    private val factory: () -> T
) : AuthRepository<T> {

    override suspend fun findById(id: String): T? {
        return try {
            val resource = scimClient.getUser(UUID.fromString(id))
            mapFromScim(resource)
        } catch (e: ForgeException) {
            if (e.status == 404) null else throw e
        }
    }

    override suspend fun findByEmail(email: String): T? {
        val response = scimClient.listUsers(filter = "userName eq \"$email\"", count = 1)
        val resource = response.resources.firstOrNull() ?: return null
        return mapFromScim(resource)
    }

    override suspend fun findAll(filter: String?, startIndex: Int, count: Int): ScimListResponse {
        return scimClient.listUsers(filter = filter, startIndex = startIndex, count = count)
    }

    override suspend fun create(user: T): T {
        val resource = mapToScim(user)
        val created = scimClient.createUser(resource)
        return mapFromScim(created)
    }

    override suspend fun update(user: T): T {
        val id = user.id ?: throw IllegalArgumentException("User ID is required for update")
        val resource = mapToScim(user)
        val updated = scimClient.replaceUser(UUID.fromString(id), resource)
        return mapFromScim(updated)
    }

    override suspend fun createAll(users: List<T>): ScimBulkResponse {
        val operations = users.map { user ->
            ScimBulkOperation(
                method = "POST",
                path = "/Users",
                bulkId = user.email,
                data = mapToScim(user)
            )
        }
        return scimClient.bulkRequest(ScimBulkRequest(operations = operations))
    }

    override suspend fun updateAll(users: List<T>): ScimBulkResponse {
        val operations = users.map { user ->
            val id = user.id ?: throw IllegalArgumentException("User ID is required for update")
            ScimBulkOperation(
                method = "PUT",
                path = "/Users/$id",
                bulkId = user.email,
                data = mapToScim(user)
            )
        }
        return scimClient.bulkRequest(ScimBulkRequest(operations = operations))
    }

    override suspend fun patch(id: String, operations: List<ScimPatchOperation>): T {
        val request = ScimPatchRequest(operations = operations)
        val patched = scimClient.patchUser(UUID.fromString(id), request)
        return mapFromScim(patched)
    }

    override suspend fun delete(id: String) {
        scimClient.deleteUser(UUID.fromString(id))
    }

    internal fun mapToScim(user: T): ScimUserResource {
        return ScimUserResource(
            id = user.id,
            externalId = user.externalId,
            userName = user.email,
            name = ScimName(
                givenName = user.firstName,
                familyName = user.lastName
            ),
            displayName = user.displayName,
            emails = listOf(ScimEmail(value = user.email)),
            phoneNumbers = user.phone?.let { listOf(ScimPhoneNumber(value = it)) },
            title = user.jobTitle,
            active = user.active
        )
    }

    internal fun mapFromScim(resource: ScimUserResource): T {
        val entity = factory()
        entity.id = resource.id
        entity.externalId = resource.externalId
        entity.email = resource.userName
        entity.firstName = resource.name?.givenName
        entity.lastName = resource.name?.familyName
        entity.displayName = resource.displayName
        entity.phone = resource.phoneNumbers?.firstOrNull()?.value
        entity.jobTitle = resource.title
        entity.active = resource.active
        entity.createdAt = resource.meta?.created?.let { parseInstant(it) }
        entity.updatedAt = resource.meta?.lastModified?.let { parseInstant(it) }
        return entity
    }

    private fun parseInstant(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }
}

@Deprecated("Renamed to DefaultAuthRepository", ReplaceWith("DefaultAuthRepository"))
typealias DefaultForgeUserRepository<T> = DefaultAuthRepository<T>
