package com.keeplearning.auth.grpc

import com.keeplearning.auth.config.JwtAuthorityConverter
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.grpc.server.GlobalServerInterceptor
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * gRPC server interceptor that validates JWT Bearer tokens from the `authorization` metadata header.
 *
 * Uses the same multi-issuer resolution as the REST endpoints:
 * 1. Extracts the Bearer token from the `authorization` metadata key
 * 2. Decodes the JWT to read the `iss` claim
 * 3. Validates the issuer starts with [keycloakIssuerPrefix]
 * 4. Verifies the token signature using the issuer's JWKS endpoint
 * 5. Extracts roles via [JwtAuthorityConverter]
 * 6. Stores the validated JWT and authorities in the gRPC [Context]
 */
@Component
@GlobalServerInterceptor
class GrpcJwtAuthInterceptor(
    private val jwtAuthorityConverter: JwtAuthorityConverter,
    @Value("\${KEYCLOAK_ISSUER_PREFIX}") private val keycloakIssuerPrefix: String
) : ServerInterceptor {

    private val logger = LoggerFactory.getLogger(GrpcJwtAuthInterceptor::class.java)

    /** Cache of JWT decoders keyed by issuer URI to avoid repeated JWKS discovery. */
    private val decoderCache = ConcurrentHashMap<String, ReactiveJwtDecoder>()

    companion object {
        private val AUTHORIZATION_KEY: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)

        /** gRPC Context key holding the validated [Jwt]. */
        val JWT_CONTEXT_KEY: Context.Key<Jwt> = Context.key("jwt")

        /** gRPC Context key holding the extracted [GrantedAuthority] collection. */
        val AUTHORITIES_CONTEXT_KEY: Context.Key<Collection<GrantedAuthority>> = Context.key("authorities")
    }

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val authHeader = headers.get(AUTHORIZATION_KEY)

        if (authHeader == null || !authHeader.startsWith("Bearer ", ignoreCase = true)) {
            call.close(
                Status.UNAUTHENTICATED.withDescription("Missing or invalid Authorization header"),
                Metadata()
            )
            return object : ServerCall.Listener<ReqT>() {}
        }

        val token = authHeader.substring(7)

        val jwt: Jwt
        try {
            jwt = decodeAndVerify(token)
        } catch (e: Exception) {
            logger.debug("JWT validation failed: {}", e.message)
            call.close(
                Status.UNAUTHENTICATED.withDescription("Invalid or expired token"),
                Metadata()
            )
            return object : ServerCall.Listener<ReqT>() {}
        }

        val authorities = jwtAuthorityConverter.convert(jwt) ?: emptyList()

        val ctx = Context.current()
            .withValue(JWT_CONTEXT_KEY, jwt)
            .withValue(AUTHORITIES_CONTEXT_KEY, authorities)

        return Contexts.interceptCall(ctx, call, headers, next)
    }

    /**
     * Decodes and verifies the JWT token.
     *
     * 1. Parses the token without signature verification to read the `iss` claim
     * 2. Validates the issuer against [keycloakIssuerPrefix]
     * 3. Uses a cached [ReactiveJwtDecoder] for the issuer to fully verify the token
     */
    private fun decodeAndVerify(token: String): Jwt {
        // Parse the payload to extract the issuer without verifying the signature
        val issuer = extractIssuer(token)
            ?: throw IllegalArgumentException("Token missing 'iss' claim")

        require(issuer.startsWith(keycloakIssuerPrefix)) { "Invalid issuer: $issuer" }

        val decoder = decoderCache.computeIfAbsent(issuer) {
            ReactiveJwtDecoders.fromIssuerLocation(it)
        }

        return decoder.decode(token).block()
            ?: throw IllegalStateException("Failed to decode JWT")
    }

    /**
     * Extracts the `iss` claim from the JWT payload without verifying the signature.
     * This is safe because the issuer is only used to select the correct JWKS endpoint;
     * the actual signature verification happens in [decodeAndVerify] via [ReactiveJwtDecoder].
     */
    private fun extractIssuer(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
            // Simple extraction without pulling in a JSON library for this one field
            val issRegex = """"iss"\s*:\s*"([^"]+)"""".toRegex()
            issRegex.find(payload)?.groupValues?.get(1)
        } catch (e: Exception) {
            logger.debug("Failed to extract issuer from token: {}", e.message)
            null
        }
    }
}
