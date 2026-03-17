package com.keeplearning.auth.security

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.server.authorization.AuthorizationContext
import reactor.core.publisher.Mono
import java.net.URL
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SuperAdminAuthorizationManagerTest {

    private lateinit var manager: SuperAdminAuthorizationManager
    private lateinit var context: AuthorizationContext

    @BeforeEach
    fun setup() {
        manager = SuperAdminAuthorizationManager()
        context = mockk()
    }

    private fun createJwtAuth(issuerUrl: String, vararg roles: String): JwtAuthenticationToken {
        val jwt = mockk<Jwt>()
        every { jwt.issuer } returns URL(issuerUrl)

        val authorities = roles.map { SimpleGrantedAuthority(it) }
        val auth = mockk<JwtAuthenticationToken>()
        every { auth.token } returns jwt
        every { auth.authorities } returns authorities

        return auth
    }

    @Test
    fun `allows saas-admin issuer with SUPER_ADMIN role`() {
        val auth = createJwtAuth(
            "https://keycloak.example.com/realms/saas-admin",
            "ROLE_SUPER_ADMIN"
        )

        val result = manager.authorize(Mono.just(auth), context).block()

        assertNotNull(result)
        assertTrue((result as AuthorizationDecision).isGranted)
    }

    @Test
    fun `allows master issuer with SUPER_ADMIN role`() {
        val auth = createJwtAuth(
            "https://keycloak.example.com/realms/master",
            "ROLE_SUPER_ADMIN"
        )

        val result = manager.authorize(Mono.just(auth), context).block()

        assertNotNull(result)
        assertTrue((result as AuthorizationDecision).isGranted)
    }

    @Test
    fun `denies wrong issuer even with SUPER_ADMIN role`() {
        val auth = createJwtAuth(
            "https://keycloak.example.com/realms/some-tenant",
            "ROLE_SUPER_ADMIN"
        )

        val result = manager.authorize(Mono.just(auth), context).block()

        assertNotNull(result)
        assertFalse((result as AuthorizationDecision).isGranted)
    }

    @Test
    fun `denies saas-admin issuer without SUPER_ADMIN role`() {
        val auth = createJwtAuth(
            "https://keycloak.example.com/realms/saas-admin",
            "ROLE_USER", "ROLE_ADMIN"
        )

        val result = manager.authorize(Mono.just(auth), context).block()

        assertNotNull(result)
        assertFalse((result as AuthorizationDecision).isGranted)
    }

    @Test
    fun `denies master issuer without SUPER_ADMIN role`() {
        val auth = createJwtAuth(
            "https://keycloak.example.com/realms/master",
            "ROLE_ADMIN"
        )

        val result = manager.authorize(Mono.just(auth), context).block()

        assertNotNull(result)
        assertFalse((result as AuthorizationDecision).isGranted)
    }

    @Test
    fun `denies empty authentication`() {
        val result = manager.authorize(Mono.empty(), context).block()

        assertNotNull(result)
        assertFalse((result as AuthorizationDecision).isGranted)
    }

    @Test
    fun `allows saas-admin issuer with multiple roles including SUPER_ADMIN`() {
        val auth = createJwtAuth(
            "https://keycloak.example.com/realms/saas-admin",
            "ROLE_USER", "ROLE_SUPER_ADMIN", "ROLE_ADMIN"
        )

        val result = manager.authorize(Mono.just(auth), context).block()

        assertNotNull(result)
        assertTrue((result as AuthorizationDecision).isGranted)
    }
}
