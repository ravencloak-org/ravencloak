package com.keeplearning.forge.sync

import com.keeplearning.auth.scim.common.*
import com.keeplearning.forge.client.ScimClient
import com.keeplearning.forge.repository.AuthUser
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

class StartupSyncRunner<T : Any>(
    private val authStartupSync: AuthStartupSync<T>,
    private val scimClient: ScimClient
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(StartupSyncRunner::class.java)

    override fun run(args: ApplicationArguments) {
        runBlocking {
            try {
                performSync()
            } catch (e: Exception) {
                logger.error("Startup sync failed (non-fatal): {}", e.message, e)
            }
        }
    }

    internal suspend fun performSync() {
        logger.info("Starting user sync...")

        val localUsers = authStartupSync.fetchAllLocalUsers()
        val authUsers = localUsers.map { authStartupSync.mapToAuthUser(it) }
        val localResources = authUsers.map { mapAuthUserToScim(it) }

        val localChecksum = ScimChecksumUtil.computeChecksum(localResources)
        logger.info("Local checksum: {} ({} users)", localChecksum, localResources.size)

        val remoteChecksumResponse = scimClient.getChecksum()
        logger.info("Remote checksum: {} ({} users)", remoteChecksumResponse.checksum, remoteChecksumResponse.userCount)

        if (localChecksum == remoteChecksumResponse.checksum) {
            logger.info("Checksums match, no sync needed")
            return
        }

        logger.info("Checksums differ, performing sync...")

        val remoteUsers = fetchAllRemoteUsers()
        val remoteByEmail = remoteUsers.associateBy { it.userName.lowercase() }

        val toCreate = mutableListOf<ScimUserResource>()
        val toUpdate = mutableListOf<ScimBulkOperation>()

        for (localResource in localResources) {
            val email = localResource.userName.lowercase()
            val remote = remoteByEmail[email]

            if (remote == null) {
                toCreate.add(localResource)
            } else {
                val localCanonical = ScimChecksumUtil.canonicalize(localResource)
                val remoteCanonical = ScimChecksumUtil.canonicalize(remote)
                if (localCanonical != remoteCanonical) {
                    toUpdate.add(
                        ScimBulkOperation(
                            method = "PUT",
                            path = "/Users/${remote.id}",
                            bulkId = email,
                            data = localResource.copy(id = remote.id)
                        )
                    )
                }
            }
        }

        logger.info("Sync plan: {} to create, {} to update", toCreate.size, toUpdate.size)

        if (toCreate.isEmpty() && toUpdate.isEmpty()) {
            logger.info("No changes needed after diff")
            return
        }

        val operations = mutableListOf<ScimBulkOperation>()

        operations.addAll(toCreate.map { resource ->
            ScimBulkOperation(
                method = "POST",
                path = "/Users",
                bulkId = resource.userName,
                data = resource
            )
        })

        operations.addAll(toUpdate)

        val response = scimClient.bulkRequest(ScimBulkRequest(operations = operations))

        val succeeded = response.operations.count { it.status.startsWith("2") }
        val failed = response.operations.size - succeeded
        logger.info("Sync complete: {} succeeded, {} failed", succeeded, failed)

        if (failed > 0) {
            response.operations
                .filter { !it.status.startsWith("2") }
                .forEach { op ->
                    logger.warn("Failed operation: {} {} -> {}", op.method, op.bulkId, op.status)
                }
        }
    }

    private suspend fun fetchAllRemoteUsers(): List<ScimUserResource> {
        val allUsers = mutableListOf<ScimUserResource>()
        var startIndex = 1
        val pageSize = 100

        do {
            val response = scimClient.listUsers(startIndex = startIndex, count = pageSize)
            allUsers.addAll(response.resources)
            startIndex += response.resources.size
        } while (allUsers.size < response.totalResults)

        return allUsers
    }

    private fun mapAuthUserToScim(user: AuthUser): ScimUserResource {
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
}
