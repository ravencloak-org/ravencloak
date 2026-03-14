package com.keeplearning.auth.grpc

import com.keeplearning.auth.domain.entity.User
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.domain.repository.UserRepository
import com.keeplearning.auth.grpc.provisioning.v1.*
import com.keeplearning.auth.scim.ScimChecksumService
import com.keeplearning.auth.scim.ScimException
import com.keeplearning.auth.scim.ScimUserService
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import com.keeplearning.auth.scim.common.ScimUserResource as ScimUser
import com.keeplearning.auth.scim.common.ScimName
import com.keeplearning.auth.scim.common.ScimPhoneNumber

@Service
class UserProvisioningGrpcService(
    private val scimUserService: ScimUserService,
    private val checksumService: ScimChecksumService,
    private val userRepository: UserRepository,
    private val realmRepository: KcRealmRepository
) : UserProvisioningGrpcKt.UserProvisioningCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(UserProvisioningGrpcService::class.java)

    override suspend fun createUser(request: CreateUserRequest): com.keeplearning.auth.grpc.provisioning.v1.User {
        return handleScimErrors {
            val scimResource = request.toScimResource()
            val created = scimUserService.createUser(request.realmName, scimResource, "")
            created.toProtoUser()
        }
    }

    override suspend fun getUser(request: GetUserRequest): com.keeplearning.auth.grpc.provisioning.v1.User {
        return handleScimErrors {
            val userId = parseUuid(request.userId)
            val found = scimUserService.getUser(request.realmName, userId, "")
            found.toProtoUser()
        }
    }

    override suspend fun listUsers(request: ListUsersRequest): ListUsersResponse {
        return handleScimErrors {
            val startIndex = if (request.startIndex > 0) request.startIndex else 1
            val count = if (request.count > 0) request.count else 100
            val filter = if (request.hasFilter()) request.filter else null

            val result = scimUserService.listUsers(
                realmName = request.realmName,
                filter = filter,
                startIndex = startIndex,
                count = count,
                baseUrl = ""
            )

            listUsersResponse {
                totalResults = result.totalResults
                this.startIndex = result.startIndex
                itemsPerPage = result.itemsPerPage
                result.resources.forEach { users += it.toProtoUser() }
            }
        }
    }

    override suspend fun updateUser(request: UpdateUserRequest): com.keeplearning.auth.grpc.provisioning.v1.User {
        return handleScimErrors {
            val userId = parseUuid(request.userId)
            val scimResource = request.toScimResource()
            val updated = scimUserService.replaceUser(request.realmName, userId, scimResource, "")
            updated.toProtoUser()
        }
    }

    override suspend fun deleteUser(request: DeleteUserRequest): DeleteUserResponse {
        return handleScimErrors {
            val userId = parseUuid(request.userId)
            scimUserService.deleteUser(request.realmName, userId)
            deleteUserResponse {}
        }
    }

    override suspend fun bulkCreateUsers(request: BulkCreateUsersRequest): BulkUsersResponse {
        return handleScimErrors {
            val realm = realmRepository.findByRealmName(request.realmName).awaitSingleOrNull()
                ?: throw StatusException(Status.NOT_FOUND.withDescription("Realm '${request.realmName}' not found"))

            val results = request.usersList.map { userData ->
                try {
                    val scimResource = userData.toScimResource()
                    val created = scimUserService.createUser(request.realmName, scimResource, "")
                    bulkOperationResult {
                        userId = created.id ?: ""
                        status = 0 // OK
                        user = created.toProtoUser()
                    }
                } catch (e: ScimException) {
                    logger.warn("Bulk create failed for user ${userData.email}: ${e.detail}")
                    bulkOperationResult {
                        status = scimStatusToGrpcCode(e.status)
                        errorMessage = e.detail ?: "Unknown error"
                    }
                } catch (e: Exception) {
                    logger.error("Unexpected error in bulk create for user ${userData.email}", e)
                    bulkOperationResult {
                        status = Status.Code.INTERNAL.value()
                        errorMessage = e.message ?: "Internal error"
                    }
                }
            }

            bulkUsersResponse { results.forEach { this.results += it } }
        }
    }

    override suspend fun bulkUpdateUsers(request: BulkUpdateUsersRequest): BulkUsersResponse {
        return handleScimErrors {
            val realm = realmRepository.findByRealmName(request.realmName).awaitSingleOrNull()
                ?: throw StatusException(Status.NOT_FOUND.withDescription("Realm '${request.realmName}' not found"))

            val results = request.usersList.map { userData ->
                try {
                    val userId = parseUuid(userData.userId)
                    val scimResource = userData.toScimResource()
                    val updated = scimUserService.replaceUser(request.realmName, userId, scimResource, "")
                    bulkOperationResult {
                        this.userId = updated.id ?: ""
                        status = 0 // OK
                        user = updated.toProtoUser()
                    }
                } catch (e: ScimException) {
                    logger.warn("Bulk update failed for user ${userData.userId}: ${e.detail}")
                    bulkOperationResult {
                        this.userId = userData.userId
                        status = scimStatusToGrpcCode(e.status)
                        errorMessage = e.detail ?: "Unknown error"
                    }
                } catch (e: Exception) {
                    logger.error("Unexpected error in bulk update for user ${userData.userId}", e)
                    bulkOperationResult {
                        this.userId = userData.userId
                        status = Status.Code.INTERNAL.value()
                        errorMessage = e.message ?: "Internal error"
                    }
                }
            }

            bulkUsersResponse { results.forEach { this.results += it } }
        }
    }

    override suspend fun getChecksum(request: GetChecksumRequest): ChecksumResponse {
        return handleScimErrors {
            val result = checksumService.getChecksum(request.realmName, "")
            checksumResponse {
                checksum = result.checksum
                userCount = result.userCount
            }
        }
    }

    // ──────────────────── Mapping helpers ────────────────────

    private fun CreateUserRequest.toScimResource() = ScimUser(
        userName = email,
        externalId = externalId.ifBlank { null },
        name = ScimName(
            givenName = if (hasFirstName()) firstName else null,
            familyName = if (hasLastName()) lastName else null
        ),
        displayName = if (hasDisplayName()) displayName else null,
        phoneNumbers = if (hasPhone()) listOf(ScimPhoneNumber(value = phone)) else null,
        title = if (hasJobTitle()) jobTitle else null,
        active = active
    )

    private fun UpdateUserRequest.toScimResource() = ScimUser(
        userName = email,
        externalId = externalId.ifBlank { null },
        name = ScimName(
            givenName = if (hasFirstName()) firstName else null,
            familyName = if (hasLastName()) lastName else null
        ),
        displayName = if (hasDisplayName()) displayName else null,
        phoneNumbers = if (hasPhone()) listOf(ScimPhoneNumber(value = phone)) else null,
        title = if (hasJobTitle()) jobTitle else null,
        active = active
    )

    private fun CreateUserData.toScimResource() = ScimUser(
        userName = email,
        externalId = externalId.ifBlank { null },
        name = ScimName(
            givenName = if (hasFirstName()) firstName else null,
            familyName = if (hasLastName()) lastName else null
        ),
        displayName = if (hasDisplayName()) displayName else null,
        phoneNumbers = if (hasPhone()) listOf(ScimPhoneNumber(value = phone)) else null,
        title = if (hasJobTitle()) jobTitle else null,
        active = active
    )

    private fun UpdateUserData.toScimResource() = ScimUser(
        userName = email,
        externalId = externalId.ifBlank { null },
        name = ScimName(
            givenName = if (hasFirstName()) firstName else null,
            familyName = if (hasLastName()) lastName else null
        ),
        displayName = if (hasDisplayName()) displayName else null,
        phoneNumbers = if (hasPhone()) listOf(ScimPhoneNumber(value = phone)) else null,
        title = if (hasJobTitle()) jobTitle else null,
        active = active
    )

    private fun ScimUser.toProtoUser(): com.keeplearning.auth.grpc.provisioning.v1.User = user {
        id = this@toProtoUser.id ?: ""
        email = this@toProtoUser.userName
        externalId = this@toProtoUser.externalId ?: ""
        this@toProtoUser.name?.givenName?.let { firstName = it }
        this@toProtoUser.name?.familyName?.let { lastName = it }
        this@toProtoUser.displayName?.let { displayName = it }
        this@toProtoUser.phoneNumbers?.firstOrNull()?.value?.let { phone = it }
        this@toProtoUser.title?.let { jobTitle = it }
        active = this@toProtoUser.active
        createdAt = this@toProtoUser.meta?.created ?: ""
        updatedAt = this@toProtoUser.meta?.lastModified ?: ""
    }

    // ──────────────────── Error handling ────────────────────

    private suspend fun <T> handleScimErrors(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: StatusException) {
            throw e
        } catch (e: ScimException) {
            throw when (e.status) {
                404 -> StatusException(Status.NOT_FOUND.withDescription(e.detail))
                409 -> StatusException(Status.ALREADY_EXISTS.withDescription(e.detail))
                400 -> StatusException(Status.INVALID_ARGUMENT.withDescription(e.detail))
                else -> StatusException(Status.INTERNAL.withDescription(e.detail))
            }
        } catch (e: IllegalArgumentException) {
            throw StatusException(Status.INVALID_ARGUMENT.withDescription(e.message))
        } catch (e: Exception) {
            logger.error("Unexpected gRPC error", e)
            throw StatusException(Status.INTERNAL.withDescription("Internal server error"))
        }
    }

    private fun parseUuid(value: String): UUID {
        return try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            throw StatusException(Status.INVALID_ARGUMENT.withDescription("Invalid UUID: $value"))
        }
    }

    private fun scimStatusToGrpcCode(httpStatus: Int): Int = when (httpStatus) {
        404 -> Status.Code.NOT_FOUND.value()
        409 -> Status.Code.ALREADY_EXISTS.value()
        400 -> Status.Code.INVALID_ARGUMENT.value()
        else -> Status.Code.INTERNAL.value()
    }
}
