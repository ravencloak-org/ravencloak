package com.keeplearning.auth.scim

import com.keeplearning.auth.domain.entity.User
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.domain.repository.UserRepository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ScimUserService(
    private val userRepository: UserRepository,
    private val realmRepository: KcRealmRepository,
    private val databaseClient: DatabaseClient
) {
    private val logger = LoggerFactory.getLogger(ScimUserService::class.java)

    suspend fun getUser(realmName: String, userId: UUID, baseUrl: String): ScimUserResource {
        resolveRealm(realmName)
        val user = userRepository.findById(userId).awaitSingleOrNull()
            ?: throw ScimException(404, detail = "User $userId not found")
        return ScimUserMapper.toScimResource(user, baseUrl)
    }

    suspend fun listUsers(
        realmName: String,
        filter: String?,
        startIndex: Int,
        count: Int,
        baseUrl: String
    ): ScimListResponse {
        val realm = resolveRealm(realmName)
        val realmId = realm.id!!

        if (filter != null) {
            return listUsersWithFilter(realmId, filter, startIndex, count, baseUrl)
        }

        val users = userRepository.findByRealmId(realmId).collectList().awaitSingle()
        val total = users.size
        val paged = users.drop(startIndex - 1).take(count)
        return ScimListResponse(
            totalResults = total,
            startIndex = startIndex,
            itemsPerPage = paged.size,
            resources = paged.map { ScimUserMapper.toScimResource(it, baseUrl) }
        )
    }

    private suspend fun listUsersWithFilter(
        realmId: UUID,
        filterString: String,
        startIndex: Int,
        count: Int,
        baseUrl: String
    ): ScimListResponse {
        val sqlFilter = ScimFilterTranslator.translate(filterString)

        val countSql = "SELECT COUNT(*) FROM users WHERE realm_id = :realmId AND (${sqlFilter.whereClause})"
        var countSpec = databaseClient.sql(countSql).bind("realmId", realmId)
        for ((key, value) in sqlFilter.bindings) {
            countSpec = countSpec.bind(key, value)
        }
        val total = countSpec.map { row, _ -> row.get(0, java.lang.Long::class.java)!!.toInt() }
            .one().awaitSingle()

        val offset = (startIndex - 1).coerceAtLeast(0)
        val dataSql = "SELECT * FROM users WHERE realm_id = :realmId AND (${sqlFilter.whereClause}) ORDER BY created_at DESC LIMIT :limit OFFSET :offset"
        var dataSpec = databaseClient.sql(dataSql)
            .bind("realmId", realmId)
            .bind("limit", count)
            .bind("offset", offset)
        for ((key, value) in sqlFilter.bindings) {
            dataSpec = dataSpec.bind(key, value)
        }

        val users = dataSpec.map { row, metadata ->
            User(
                id = row.get("id", UUID::class.java),
                keycloakUserId = row.get("keycloak_user_id", String::class.java) ?: "",
                email = row.get("email", String::class.java)!!,
                displayName = row.get("display_name", String::class.java),
                accountId = row.get("account_id", UUID::class.java)!!,
                realmId = row.get("realm_id", UUID::class.java),
                firstName = row.get("first_name", String::class.java),
                lastName = row.get("last_name", String::class.java),
                phone = row.get("phone", String::class.java),
                bio = row.get("bio", String::class.java),
                jobTitle = row.get("job_title", String::class.java),
                department = row.get("department", String::class.java),
                avatarUrl = row.get("avatar_url", String::class.java),
                status = row.get("status", String::class.java) ?: "ACTIVE",
                lastLoginAt = row.get("last_login_at", Instant::class.java),
                createdAt = row.get("created_at", Instant::class.java) ?: Instant.now(),
                updatedAt = row.get("updated_at", Instant::class.java)
            )
        }.all().collectList().awaitSingle()

        return ScimListResponse(
            totalResults = total,
            startIndex = startIndex,
            itemsPerPage = users.size,
            resources = users.map { ScimUserMapper.toScimResource(it, baseUrl) }
        )
    }

    suspend fun createUser(realmName: String, resource: ScimUserResource, baseUrl: String): ScimUserResource {
        val realm = resolveRealm(realmName)

        val existing = userRepository.findByRealmIdAndEmail(realm.id!!, resource.userName).awaitSingleOrNull()
        if (existing != null) {
            throw ScimException(409, "uniqueness", "User with userName '${resource.userName}' already exists")
        }

        val user = ScimUserMapper.fromScimResource(resource, realm.accountId ?: realm.id, realm.id)
        val saved = userRepository.save(user).awaitSingle()
        logger.info("SCIM: Created user ${resource.userName} in realm $realmName")
        return ScimUserMapper.toScimResource(saved, baseUrl)
    }

    suspend fun replaceUser(realmName: String, userId: UUID, resource: ScimUserResource, baseUrl: String): ScimUserResource {
        resolveRealm(realmName)
        val existing = userRepository.findById(userId).awaitSingleOrNull()
            ?: throw ScimException(404, detail = "User $userId not found")

        val updated = existing.copy(
            email = resource.userName,
            displayName = resource.displayName,
            firstName = resource.name?.givenName,
            lastName = resource.name?.familyName,
            phone = resource.phoneNumbers?.firstOrNull()?.value,
            jobTitle = resource.title,
            keycloakUserId = resource.externalId ?: existing.keycloakUserId,
            status = if (resource.active) "ACTIVE" else "INACTIVE",
            updatedAt = Instant.now()
        )

        val saved = userRepository.save(updated).awaitSingle()
        logger.info("SCIM: Replaced user $userId in realm $realmName")
        return ScimUserMapper.toScimResource(saved, baseUrl)
    }

    suspend fun patchUser(realmName: String, userId: UUID, request: ScimPatchRequest, baseUrl: String): ScimUserResource {
        resolveRealm(realmName)
        val existing = userRepository.findById(userId).awaitSingleOrNull()
            ?: throw ScimException(404, detail = "User $userId not found")

        val patched = ScimUserMapper.applyPatch(existing, request.operations)
        val saved = userRepository.save(patched).awaitSingle()
        logger.info("SCIM: Patched user $userId in realm $realmName")
        return ScimUserMapper.toScimResource(saved, baseUrl)
    }

    suspend fun deleteUser(realmName: String, userId: UUID) {
        resolveRealm(realmName)
        val user = userRepository.findById(userId).awaitSingleOrNull()
            ?: throw ScimException(404, detail = "User $userId not found")
        userRepository.delete(user).awaitSingleOrNull()
        logger.info("SCIM: Deleted user $userId from realm $realmName")
    }

    private suspend fun resolveRealm(realmName: String) =
        realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ScimException(404, detail = "Realm '$realmName' not found")
}
