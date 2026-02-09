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
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

@AutoConfiguration
@ConditionalOnProperty(prefix = "forge", name = ["base-url"])
@EnableConfigurationProperties(AuthProperties::class)
class AuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = ["forgeWebClient"])
    fun forgeWebClient(
        properties: AuthProperties,
        authorizedClientManager: ReactiveOAuth2AuthorizedClientManager
    ): WebClient {
        val oauth2Filter = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
        oauth2Filter.setDefaultClientRegistrationId(properties.clientRegistrationId)

        return WebClient.builder()
            .baseUrl(properties.baseUrl)
            .filter(oauth2Filter)
            .build()
    }

    @Bean
    @ConditionalOnMissingBean
    fun scimClient(
        forgeWebClient: WebClient,
        properties: AuthProperties
    ): ScimClient {
        return ScimClient(forgeWebClient, properties)
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
