package com.keeplearning.forge.config

import com.keeplearning.forge.client.ScimClient
import com.keeplearning.forge.sync.AuthStartupSync
import com.keeplearning.forge.sync.StartupSyncRunner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor
import org.springframework.web.client.RestClient

@AutoConfiguration
@ConditionalOnProperty(prefix = "forge", name = ["base-url"])
@EnableConfigurationProperties(AuthProperties::class)
class AuthAutoConfiguration {

    /**
     * Service-based client manager that works outside servlet request context.
     * Required because SCIM calls may run in background coroutines (no HttpServletRequest).
     */
    @Bean
    @ConditionalOnMissingBean(name = ["forgeAuthorizedClientManager"])
    fun forgeAuthorizedClientManager(
        clientRegistrationRepository: ClientRegistrationRepository,
        authorizedClientService: OAuth2AuthorizedClientService
    ): OAuth2AuthorizedClientManager {
        val manager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository,
            authorizedClientService
        )
        manager.setAuthorizedClientProvider(
            OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build()
        )
        return manager
    }

    @Bean
    @ConditionalOnMissingBean(name = ["forgeRestClient"])
    fun forgeRestClient(
        properties: AuthProperties,
        forgeAuthorizedClientManager: OAuth2AuthorizedClientManager
    ): RestClient {
        val oauth2Interceptor = OAuth2ClientHttpRequestInterceptor(forgeAuthorizedClientManager)
        oauth2Interceptor.setClientRegistrationIdResolver { properties.clientRegistrationId }
        // Use a fixed principal so OAuth2 works outside servlet request context
        // (e.g., background coroutines without HttpServletRequest)
        oauth2Interceptor.setPrincipalResolver {
            AnonymousAuthenticationToken(
                "forge-sdk",
                properties.clientRegistrationId,
                AuthorityUtils.createAuthorityList("ROLE_CLIENT")
            )
        }

        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestInterceptor(oauth2Interceptor)
            .build()
    }

    @Bean
    @ConditionalOnMissingBean
    fun scimClient(
        forgeRestClient: RestClient,
        properties: AuthProperties
    ): ScimClient {
        return ScimClient(forgeRestClient, properties)
    }

    @Bean
    @ConditionalOnBean(AuthStartupSync::class)
    @ConditionalOnProperty(prefix = "forge.startup-sync", name = ["enabled"], matchIfMissing = true)
    fun <T : Any> startupSyncRunner(
        authStartupSync: AuthStartupSync<T>,
        scimClient: ScimClient
    ): StartupSyncRunner<T> {
        return StartupSyncRunner(authStartupSync, scimClient)
    }
}

@Deprecated("Renamed to AuthAutoConfiguration", ReplaceWith("AuthAutoConfiguration"))
typealias ForgeAutoConfiguration = AuthAutoConfiguration
