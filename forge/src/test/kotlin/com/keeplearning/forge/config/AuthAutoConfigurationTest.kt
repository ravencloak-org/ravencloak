package com.keeplearning.forge.config

import com.keeplearning.forge.client.ScimClient
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.client.RestClient
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AuthAutoConfiguration::class.java))

    @Test
    fun `does not configure when base-url is absent`() {
        contextRunner.run { context ->
            assertTrue(context.getBeansOfType(ScimClient::class.java).isEmpty())
        }
    }

    @Test
    fun `configures beans when base-url and realm-name are present`() {
        contextRunner
            .withPropertyValues(
                "forge.base-url=http://localhost:8080",
                "forge.realm-name=test-realm"
            )
            .withBean(
                "forgeAuthorizedClientManager",
                OAuth2AuthorizedClientManager::class.java,
                { io.mockk.mockk() }
            )
            .withBean(
                "forgeRestClient",
                RestClient::class.java,
                { RestClient.builder().baseUrl("http://localhost:8080").build() }
            )
            .run { context ->
                assertNotNull(context.getBean(ScimClient::class.java))
                assertNotNull(context.getBean(AuthProperties::class.java))
            }
    }
}
