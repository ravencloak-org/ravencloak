package com.keeplearning.keycloak.spi

import org.keycloak.component.ComponentModel
import org.keycloak.models.KeycloakSession
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.provider.ProviderConfigurationBuilder
import org.keycloak.storage.UserStorageProviderFactory
import java.util.logging.Logger

/**
 * Factory class for creating ExternalUserStorageProvider instances.
 * This is the entry point that Keycloak uses to instantiate the provider.
 */
class ExternalUserStorageProviderFactory : UserStorageProviderFactory<ExternalUserStorageProvider> {

    companion object {
        private val logger: Logger = Logger.getLogger(ExternalUserStorageProviderFactory::class.java.name)
        const val PROVIDER_ID = "kos-auth-storage"

        // Configuration property names
        const val CONFIG_API_URL = "api-url"
        const val CONFIG_CLIENT_AWARE_AUTH = "client-aware-auth"
        const val CONFIG_TIMEOUT = "timeout"

        // Default values
        const val DEFAULT_API_URL = "http://auth-backend:8080"
        const val DEFAULT_TIMEOUT = "30"
    }

    override fun getId(): String = PROVIDER_ID

    override fun create(session: KeycloakSession, model: ComponentModel): ExternalUserStorageProvider {
        logger.fine { "Creating ExternalUserStorageProvider instance" }
        return ExternalUserStorageProvider(session, model)
    }

    override fun getConfigProperties(): List<ProviderConfigProperty> {
        return ProviderConfigurationBuilder.create()
            .property()
            .name(CONFIG_API_URL)
            .label("API URL")
            .helpText("Base URL of the authentication backend API (e.g., http://auth-backend:8080)")
            .type(ProviderConfigProperty.STRING_TYPE)
            .defaultValue(DEFAULT_API_URL)
            .add()
            .property()
            .name(CONFIG_CLIENT_AWARE_AUTH)
            .label("Client-Aware Authentication")
            .helpText("When enabled, passes client ID during authentication to enforce per-client user authorization")
            .type(ProviderConfigProperty.BOOLEAN_TYPE)
            .defaultValue("false")
            .add()
            .property()
            .name(CONFIG_TIMEOUT)
            .label("Request Timeout (seconds)")
            .helpText("Timeout for requests to the authentication backend")
            .type(ProviderConfigProperty.STRING_TYPE)
            .defaultValue(DEFAULT_TIMEOUT)
            .add()
            .build()
    }

    override fun close() {
        // No resources to clean up at the factory level
    }
}
