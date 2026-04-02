package com.keeplearning.auth.config

import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwtAuthorityConverterTest {

    private val converter = JwtAuthorityConverter()

    @Test
    fun `converts realm_access roles to granted authorities with ROLE_ prefix`() {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("realm_access", mapOf("roles" to listOf("ADMIN", "USER")))
            .build()

        val authorities = converter.convert(jwt)

        assertEquals(2, authorities.size)
        assertTrue(authorities.contains(SimpleGrantedAuthority("ROLE_ADMIN")))
        assertTrue(authorities.contains(SimpleGrantedAuthority("ROLE_USER")))
    }

    @Test
    fun `returns empty collection when no realm_access claim`() {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "user-123")
            .build()

        val authorities = converter.convert(jwt)

        assertTrue(authorities.isEmpty())
    }

    @Test
    fun `returns empty collection when realm_access has no roles key`() {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("realm_access", mapOf("other" to "value"))
            .build()

        val authorities = converter.convert(jwt)

        assertTrue(authorities.isEmpty())
    }

    @Test
    fun `handles single role correctly`() {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("realm_access", mapOf("roles" to listOf("SUPER_ADMIN")))
            .build()

        val authorities = converter.convert(jwt)

        assertEquals(1, authorities.size)
        assertTrue(authorities.contains(SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
    }

    @Test
    fun `handles empty roles list`() {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("realm_access", mapOf("roles" to emptyList<String>()))
            .build()

        val authorities = converter.convert(jwt)

        assertTrue(authorities.isEmpty())
    }
}
