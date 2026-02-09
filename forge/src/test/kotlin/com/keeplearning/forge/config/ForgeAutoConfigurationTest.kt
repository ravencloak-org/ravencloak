package com.keeplearning.forge.config

import com.keeplearning.forge.client.ScimClient
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ForgeAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ForgeAutoConfiguration::class.java))

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
                org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager::class.java,
                { io.mockk.mockk() }
            )
            .run { context ->
                assertNotNull(context.getBean(ScimClient::class.java))
                assertNotNull(context.getBean(ForgeProperties::class.java))
            }
    }
}
