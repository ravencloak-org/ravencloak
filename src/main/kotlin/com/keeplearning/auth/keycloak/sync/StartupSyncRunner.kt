package com.keeplearning.auth.keycloak.sync

import com.keeplearning.auth.config.KeycloakSyncProperties
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class StartupSyncRunner(
    private val syncProperties: KeycloakSyncProperties,
    private val keycloakClient: KeycloakAdminClient,
    private val syncService: KeycloakSyncService
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(StartupSyncRunner::class.java)

    override fun run(args: ApplicationArguments) {
        if (!syncProperties.enabled) {
            logger.info("Keycloak sync is disabled, skipping startup sync")
            return
        }

        runBlocking {
            try {
                logger.info("Checking Keycloak connectivity...")
                val isHealthy = keycloakClient.healthCheck()

                if (!isHealthy) {
                    logger.warn("Keycloak is not available, skipping startup sync")
                    return@runBlocking
                }

                logger.info("Keycloak is available, starting sync...")
                val result = syncService.syncAll()

                if (result.success) {
                    logger.info("Startup sync completed successfully: ${result.realmsProcessed} realms synced")
                } else {
                    logger.warn("Startup sync completed with errors")
                }
            } catch (e: Exception) {
                logger.error("Startup sync failed", e)
            }
        }
    }
}
