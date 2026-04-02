package com.keeplearning.auth.realm.service

import com.keeplearning.auth.audit.service.AuditService
import com.keeplearning.auth.domain.entity.KcRealm
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.IdentityProviderRepresentation
import com.keeplearning.auth.realm.dto.CreateIdpRequest
import com.keeplearning.auth.realm.dto.UpdateIdpRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class IdpServiceTest {

    @MockK
    lateinit var keycloakClient: KeycloakAdminClient

    @MockK
    lateinit var realmRepository: KcRealmRepository

    @MockK
    lateinit var auditService: AuditService

    private lateinit var service: IdpService

    private val realmId = UUID.randomUUID()
    private val realmName = "test-realm"

    private val realm = KcRealm(
        id = realmId,
        realmName = realmName,
        keycloakId = "kc-realm-id"
    )

    private val googleIdpRep = IdentityProviderRepresentation(
        alias = "google",
        displayName = "Google",
        providerId = "google",
        enabled = true,
        trustEmail = true,
        config = mapOf("clientId" to "google-client-id", "clientSecret" to "google-secret")
    )

    private val samlIdpRep = IdentityProviderRepresentation(
        alias = "corporate-saml",
        displayName = "Corporate SSO",
        providerId = "saml",
        enabled = true,
        trustEmail = false,
        config = mapOf("singleSignOnServiceUrl" to "https://sso.corp.com/saml")
    )

    @BeforeEach
    fun setup() {
        service = IdpService(keycloakClient, realmRepository, auditService)
    }

    // ==================== createIdentityProvider ====================

    @Test
    fun `createIdentityProvider creates IDP successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.createIdentityProvider(realmName, any()) } returns Unit

        val request = CreateIdpRequest(
            alias = "google",
            displayName = "Google",
            providerId = "google",
            enabled = true,
            trustEmail = true,
            config = mapOf("clientId" to "google-client-id", "clientSecret" to "google-secret")
        )

        val response = service.createIdentityProvider(realmName, request, actor = null)

        assertEquals("google", response.alias)
        assertEquals("Google", response.displayName)
        assertEquals("google", response.providerId)
        assertTrue(response.enabled)
        assertTrue(response.trustEmail)
        assertEquals("google-client-id", response.config["clientId"])

        coVerify { keycloakClient.createIdentityProvider(realmName, match { it.alias == "google" }) }
    }

    @Test
    fun `createIdentityProvider with null config returns empty map in response`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.createIdentityProvider(realmName, any()) } returns Unit

        val request = CreateIdpRequest(
            alias = "oidc-provider",
            providerId = "oidc",
            config = null
        )

        val response = service.createIdentityProvider(realmName, request, actor = null)

        assertEquals("oidc-provider", response.alias)
        assertEquals(emptyMap(), response.config)
    }

    @Test
    fun `createIdentityProvider throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val request = CreateIdpRequest(
            alias = "google",
            providerId = "google"
        )

        val exception = assertThrows<ResponseStatusException> {
            service.createIdentityProvider("unknown-realm", request, actor = null)
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Realm"))
    }

    // ==================== getIdentityProvider ====================

    @Test
    fun `getIdentityProvider returns IDP successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.getIdentityProvider(realmName, "google") } returns googleIdpRep

        val response = service.getIdentityProvider(realmName, "google")

        assertEquals("google", response.alias)
        assertEquals("Google", response.displayName)
        assertEquals("google", response.providerId)
        assertTrue(response.enabled)
        assertTrue(response.trustEmail)
        assertEquals(2, response.config.size)
    }

    @Test
    fun `getIdentityProvider with null config returns empty map`() = runTest {
        val idpWithNullConfig = googleIdpRep.copy(config = null)

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.getIdentityProvider(realmName, "google") } returns idpWithNullConfig

        val response = service.getIdentityProvider(realmName, "google")

        assertEquals(emptyMap(), response.config)
    }

    @Test
    fun `getIdentityProvider throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.getIdentityProvider("unknown-realm", "google")
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Realm"))
    }

    // ==================== listIdentityProviders ====================

    @Test
    fun `listIdentityProviders returns all IDPs successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.getIdentityProviders(realmName) } returns listOf(googleIdpRep, samlIdpRep)

        val response = service.listIdentityProviders(realmName)

        assertEquals(2, response.size)
        assertEquals("google", response[0].alias)
        assertEquals("corporate-saml", response[1].alias)
        assertEquals("saml", response[1].providerId)
    }

    @Test
    fun `listIdentityProviders returns empty list when no IDPs`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.getIdentityProviders(realmName) } returns emptyList()

        val response = service.listIdentityProviders(realmName)

        assertEquals(0, response.size)
    }

    @Test
    fun `listIdentityProviders throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.listIdentityProviders("unknown-realm")
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Realm"))
    }

    // ==================== updateIdentityProvider ====================

    @Test
    fun `updateIdentityProvider updates displayName successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.getIdentityProvider(realmName, "google") } returns googleIdpRep
        coEvery { keycloakClient.updateIdentityProvider(realmName, "google", any()) } returns Unit

        val request = UpdateIdpRequest(displayName = "Google OAuth2")

        val response = service.updateIdentityProvider(realmName, "google", request, actor = null)

        assertEquals("google", response.alias)
        assertEquals("Google OAuth2", response.displayName)
        // Verify other fields preserved
        assertTrue(response.enabled)
        assertTrue(response.trustEmail)
        assertEquals("google-client-id", response.config["clientId"])

        coVerify {
            keycloakClient.updateIdentityProvider(realmName, "google", match {
                it.displayName == "Google OAuth2" && it.alias == "google"
            })
        }
    }

    @Test
    fun `updateIdentityProvider updates enabled flag successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.getIdentityProvider(realmName, "google") } returns googleIdpRep
        coEvery { keycloakClient.updateIdentityProvider(realmName, "google", any()) } returns Unit

        val request = UpdateIdpRequest(enabled = false)

        val response = service.updateIdentityProvider(realmName, "google", request, actor = null)

        assertFalse(response.enabled)
        // Verify other fields preserved
        assertEquals("Google", response.displayName)
        assertTrue(response.trustEmail)
    }

    @Test
    fun `updateIdentityProvider updates config successfully`() = runTest {
        val newConfig = mapOf("clientId" to "new-google-id", "clientSecret" to "new-secret")

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.getIdentityProvider(realmName, "google") } returns googleIdpRep
        coEvery { keycloakClient.updateIdentityProvider(realmName, "google", any()) } returns Unit

        val request = UpdateIdpRequest(config = newConfig)

        val response = service.updateIdentityProvider(realmName, "google", request, actor = null)

        assertEquals("new-google-id", response.config["clientId"])
        assertEquals("new-secret", response.config["clientSecret"])
    }

    @Test
    fun `updateIdentityProvider updates multiple fields simultaneously`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.getIdentityProvider(realmName, "google") } returns googleIdpRep
        coEvery { keycloakClient.updateIdentityProvider(realmName, "google", any()) } returns Unit

        val request = UpdateIdpRequest(
            displayName = "Updated Google",
            enabled = false,
            trustEmail = false,
            config = mapOf("clientId" to "updated-id")
        )

        val response = service.updateIdentityProvider(realmName, "google", request, actor = null)

        assertEquals("Updated Google", response.displayName)
        assertFalse(response.enabled)
        assertFalse(response.trustEmail)
        assertEquals("updated-id", response.config["clientId"])
    }

    @Test
    fun `updateIdentityProvider throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val request = UpdateIdpRequest(displayName = "Updated")

        val exception = assertThrows<ResponseStatusException> {
            service.updateIdentityProvider("unknown-realm", "google", request, actor = null)
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Realm"))
    }

    // ==================== deleteIdentityProvider ====================

    @Test
    fun `deleteIdentityProvider deletes IDP successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.getIdentityProvider(realmName, "google") } returns googleIdpRep
        coEvery { keycloakClient.deleteIdentityProvider(realmName, "google") } returns Unit

        service.deleteIdentityProvider(realmName, "google", actor = null)

        coVerify { keycloakClient.deleteIdentityProvider(realmName, "google") }
    }

    @Test
    fun `deleteIdentityProvider throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.deleteIdentityProvider("unknown-realm", "google", actor = null)
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Realm"))
    }
}
