package com.keeplearning.auth.security

import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationResult
import org.springframework.security.authorization.ReactiveAuthorizationManager
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.server.authorization.AuthorizationContext
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SuperAdminAuthorizationManager :
    ReactiveAuthorizationManager<AuthorizationContext> {

    override fun authorize(
        authentication: Mono<Authentication>,
        context: AuthorizationContext
    ): Mono<AuthorizationResult> {

        return authentication
            .map { auth ->
                val jwtAuth = auth as JwtAuthenticationToken
                val issuer = jwtAuth.token.issuer.toString()

                val allowed =
                    issuer.contains("/saas-admin") &&
                            jwtAuth.authorities.any {
                                it.authority == "ROLE_SUPER_ADMIN"
                            }

                AuthorizationDecision(allowed)
            }
            .defaultIfEmpty(AuthorizationDecision(false))
            .cast(AuthorizationResult::class.java)
    }
}
