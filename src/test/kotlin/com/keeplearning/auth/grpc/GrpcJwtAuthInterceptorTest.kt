package com.keeplearning.auth.grpc

import com.keeplearning.auth.config.JwtAuthorityConverter
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.Status
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import reactor.core.publisher.Mono
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class GrpcJwtAuthInterceptorTest {

    @MockK
    lateinit var jwtAuthorityConverter: JwtAuthorityConverter

    private lateinit var interceptor: GrpcJwtAuthInterceptor

    private val keycloakIssuerPrefix = "https://auth.example.com/realms/"

    @BeforeEach
    fun setup() {
        interceptor = GrpcJwtAuthInterceptor(jwtAuthorityConverter, keycloakIssuerPrefix)
    }

    // ==================== Missing/Invalid Authorization header ====================

    @Test
    fun `rejects call with no authorization header`() {
        val call = mockk<ServerCall<Any, Any>>(relaxed = true)
        val headers = Metadata()
        val next = mockk<ServerCallHandler<Any, Any>>()

        interceptor.interceptCall(call, headers, next)

        val statusSlot = slot<Status>()
        verify { call.close(capture(statusSlot), any()) }
        assertEquals(Status.UNAUTHENTICATED.code, statusSlot.captured.code)
        assertTrue(statusSlot.captured.description!!.contains("Missing"))
    }

    @Test
    fun `rejects call with non-Bearer authorization header`() {
        val call = mockk<ServerCall<Any, Any>>(relaxed = true)
        val headers = Metadata()
        headers.put(
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
            "Basic dXNlcjpwYXNz"
        )
        val next = mockk<ServerCallHandler<Any, Any>>()

        interceptor.interceptCall(call, headers, next)

        val statusSlot = slot<Status>()
        verify { call.close(capture(statusSlot), any()) }
        assertEquals(Status.UNAUTHENTICATED.code, statusSlot.captured.code)
    }

    @Test
    fun `rejects call with empty Bearer token`() {
        val call = mockk<ServerCall<Any, Any>>(relaxed = true)
        val headers = Metadata()
        headers.put(
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
            "Bearer "
        )
        val next = mockk<ServerCallHandler<Any, Any>>()

        interceptor.interceptCall(call, headers, next)

        val statusSlot = slot<Status>()
        verify { call.close(capture(statusSlot), any()) }
        assertEquals(Status.UNAUTHENTICATED.code, statusSlot.captured.code)
    }

    @Test
    fun `rejects call with malformed JWT (not 3 parts)`() {
        val call = mockk<ServerCall<Any, Any>>(relaxed = true)
        val headers = Metadata()
        headers.put(
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
            "Bearer not-a-jwt"
        )
        val next = mockk<ServerCallHandler<Any, Any>>()

        interceptor.interceptCall(call, headers, next)

        val statusSlot = slot<Status>()
        verify { call.close(capture(statusSlot), any()) }
        assertEquals(Status.UNAUTHENTICATED.code, statusSlot.captured.code)
    }

    @Test
    fun `rejects call with invalid issuer prefix`() {
        // Create a JWT-like token with a wrong issuer
        val payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"iss":"https://evil.com/realms/test","sub":"user1"}""".toByteArray())
        val fakeToken = "eyJhbGciOiJSUzI1NiJ9.$payload.fake-signature"

        val call = mockk<ServerCall<Any, Any>>(relaxed = true)
        val headers = Metadata()
        headers.put(
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
            "Bearer $fakeToken"
        )
        val next = mockk<ServerCallHandler<Any, Any>>()

        interceptor.interceptCall(call, headers, next)

        val statusSlot = slot<Status>()
        verify { call.close(capture(statusSlot), any()) }
        assertEquals(Status.UNAUTHENTICATED.code, statusSlot.captured.code)
    }

    // ==================== Issuer extraction ====================

    @Test
    fun `rejects token with missing iss claim`() {
        val payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"user1"}""".toByteArray())
        val fakeToken = "eyJhbGciOiJSUzI1NiJ9.$payload.fake-signature"

        val call = mockk<ServerCall<Any, Any>>(relaxed = true)
        val headers = Metadata()
        headers.put(
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
            "Bearer $fakeToken"
        )
        val next = mockk<ServerCallHandler<Any, Any>>()

        interceptor.interceptCall(call, headers, next)

        val statusSlot = slot<Status>()
        verify { call.close(capture(statusSlot), any()) }
        assertEquals(Status.UNAUTHENTICATED.code, statusSlot.captured.code)
    }
}
