package com.keeplearning.keycloak.spi

import org.keycloak.component.ComponentModel
import org.keycloak.models.KeycloakSession
import org.keycloak.storage.UserStorageProviderFactory
import java.util.logging.Logger

/**
 * Factory class for creating ExternalUserStorageProvider instances.
 * This is the entry point that Keycloak uses to instantiate the provider.
 */
class ExternalUserStorageProviderFactory : UserStorageProviderFactory<ExternalUserStorageProvider> {

    companion object {
        private val logger: Logger = Logger.getLogger(ExternalUserStorageProviderFactory::class.java.name)
        const val PROVIDER_ID = "external-user-storage"
    }

    override fun getId(): String = PROVIDER_ID

    override fun create(session: KeycloakSession, model: ComponentModel): ExternalUserStorageProvider {
        logger.fine { "Creating ExternalUserStorageProvider instance" }
        return ExternalUserStorageProvider(session, model)
    }

    override fun close() {
        // No resources to clean up at the factory level
    }
}
