package com.keeplearning.auth.keycloak.client

import com.keeplearning.auth.config.KeycloakAdminProperties
import com.keeplearning.auth.config.KeycloakSpiProperties
import com.keeplearning.auth.keycloak.client.dto.RealmRepresentation
import com.keeplearning.auth.keycloak.client.dto.TokenResponse
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
class KeycloakAdminClientTest {

    @MockK
    lateinit var webClient: WebClient

    @MockK
    lateinit var requestBodyUriSpec: WebClient.RequestBodyUriSpec

    @MockK
    lateinit var requestHeadersSpec: WebClient.RequestHeadersSpec<*>

    @MockK
    lateinit var responseSpec: WebClient.ResponseSpec

    @MockK
    lateinit var requestHeadersUriSpec: WebClient.RequestHeadersUriSpec<*>

    @MockK
    lateinit var webClientBuilder: WebClient.Builder

    @MockK
    lateinit var mutatedWebClient: WebClient

    @MockK
    lateinit var mutatedRequestHeadersUriSpec: WebClient.RequestHeadersUriSpec<*>

    @MockK
    lateinit var mutatedRequestHeadersSpec: WebClient.RequestHeadersSpec<*>

    @MockK
    lateinit var mutatedResponseSpec: WebClient.ResponseSpec

    private val adminProperties = KeycloakAdminProperties(
        baseUrl = "http://localhost:8088",
        realm = "master",
        clientId = "admin-cli",
        clientSecret = "test-secret"
    )

    private val spiProperties = KeycloakSpiProperties(
        providerId = "kos-auth-storage",
        defaultApiUrl = "http://auth-backend:8080/api/users"
    )

    private lateinit var keycloakAdminClient: KeycloakAdminClient

    private val tokenResponse = TokenResponse(
        accessToken = "test-token",
        expiresIn = 300,
        tokenType = "Bearer"
    )

    @BeforeEach
    fun setup() {
        // Each test creates a fresh instance to reset the internal token cache
        keycloakAdminClient = KeycloakAdminClient(webClient, adminProperties, spiProperties)
    }

    /**
     * Sets up the WebClient mock chain for the token endpoint POST request.
     * The Kotlin reified extension bodyToMono<T>() calls bodyToMono(ParameterizedTypeReference<T>),
     * so we must mock that overload.
     */
    private fun stubTokenEndpoint(response: TokenResponse = tokenResponse) {
        every { webClient.post() } returns requestBodyUriSpec
        every { requestBodyUriSpec.uri(any<String>()) } returns requestBodyUriSpec
        every { requestBodyUriSpec.contentType(MediaType.APPLICATION_FORM_URLENCODED) } returns requestBodyUriSpec
        every { requestBodyUriSpec.body(any()) } returns requestHeadersSpec
        every { requestHeadersSpec.retrieve() } returns responseSpec
        every {
            responseSpec.bodyToMono(any<ParameterizedTypeReference<TokenResponse>>())
        } returns Mono.just(response)
    }

    /**
     * Sets up the WebClient.mutate() chain so adminClient() returns a mocked mutated WebClient.
     */
    private fun stubMutateChain() {
        every { webClient.mutate() } returns webClientBuilder
        every { webClientBuilder.defaultHeader(any(), any()) } returns webClientBuilder
        every { webClientBuilder.build() } returns mutatedWebClient
    }

    /**
     * Sets up the mutated WebClient GET chain for getRealms().
     * The Kotlin reified extension bodyToFlux<T>() calls bodyToFlux(ParameterizedTypeReference<T>),
     * so we must mock that overload.
     */
    private fun stubGetRealms(realms: List<RealmRepresentation> = emptyList()) {
        every { mutatedWebClient.get() } returns mutatedRequestHeadersUriSpec
        every { mutatedRequestHeadersUriSpec.uri(any<String>()) } returns mutatedRequestHeadersSpec
        every { mutatedRequestHeadersSpec.retrieve() } returns mutatedResponseSpec
        every {
            mutatedResponseSpec.bodyToFlux(any<ParameterizedTypeReference<RealmRepresentation>>())
        } returns Flux.fromIterable(realms)
    }

    // ==================== Token Caching Tests ====================

    @Test
    fun `getAccessToken fetches new token when cache is empty`() = runTest {
        stubTokenEndpoint()
        stubMutateChain()
        stubGetRealms()

        keycloakAdminClient.getRealms()

        verify(exactly = 1) { webClient.post() }
    }

    @Test
    fun `getAccessToken returns cached token when not expired`() = runTest {
        // Token with expiresIn=300 means expiresAt = now + 240s, well beyond the 30s threshold
        stubTokenEndpoint()
        stubMutateChain()
        stubGetRealms()

        // First call - should fetch token
        keycloakAdminClient.getRealms()
        // Second call - should use cached token
        keycloakAdminClient.getRealms()

        // webClient.post() should only be called once for token fetch
        verify(exactly = 1) { webClient.post() }
    }

    @Test
    fun `getAccessToken fetches new token when cached token is about to expire`() = runTest {
        // expiresIn=61 => expiresAt = now + (61-60) = now + 1s
        // The cache check is: expiresAt.isAfter(Instant.now().plusSeconds(30))
        // now + 1s is NOT after now + 30s, so it should re-fetch
        val shortLivedToken = TokenResponse(
            accessToken = "short-lived-token",
            expiresIn = 61,
            tokenType = "Bearer"
        )

        val freshToken = TokenResponse(
            accessToken = "fresh-token",
            expiresIn = 300,
            tokenType = "Bearer"
        )

        // First call returns short-lived token
        stubTokenEndpoint(shortLivedToken)
        stubMutateChain()
        stubGetRealms()

        keycloakAdminClient.getRealms()

        // Now re-stub to return a fresh token for the second call
        stubTokenEndpoint(freshToken)

        keycloakAdminClient.getRealms()

        // Should have called post() twice — once for each token fetch
        verify(exactly = 2) { webClient.post() }
    }

    @Test
    fun `getRealms uses token from getAccessToken`() = runTest {
        stubTokenEndpoint()
        stubMutateChain()
        stubGetRealms(
            listOf(
                RealmRepresentation(id = "1", realm = "master"),
                RealmRepresentation(id = "2", realm = "test-realm")
            )
        )

        val result = keycloakAdminClient.getRealms()

        assertEquals(2, result.size)
        assertEquals("master", result[0].realm)
        assertEquals("test-realm", result[1].realm)

        // Verify the Authorization header was set with the token
        verify { webClientBuilder.defaultHeader("Authorization", "Bearer test-token") }
    }

    @Test
    fun `adminClient sets Bearer token header`() = runTest {
        stubTokenEndpoint()
        stubMutateChain()
        stubGetRealms()

        keycloakAdminClient.getRealms()

        // Verify the full mutate chain was invoked with correct Bearer token
        verify { webClient.mutate() }
        verify { webClientBuilder.defaultHeader("Authorization", "Bearer test-token") }
        verify { webClientBuilder.build() }

        // The mutated client should be used for the actual API call
        val builtClient = webClientBuilder.build()
        assertNotNull(builtClient)
    }
}
