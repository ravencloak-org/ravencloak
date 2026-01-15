package com.keeplearning.auth.security

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
class SecurityGuards {
    fun requireSuperAdmin(auth: JwtAuthenticationToken) {
        val issuer = auth.token.issuer.toString()

        if (!issuer.contains("/saas-admin") ||
            auth.authorities.none { it.authority == "ROLE_SUPER_ADMIN" }) {
            throw IllegalAccessException("Super admin access required")
        }
    }
}