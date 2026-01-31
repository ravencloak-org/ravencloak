package com.keeplearning.auth.auth

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/auth/super")
class SuperAdminAuthController {

    /**
     * Login entry point.
     *
     * This endpoint is NEVER executed directly.
     * Spring Security intercepts it and redirects to Keycloak.
     */
    @GetMapping("/login")
    fun login(): ResponseEntity<Void> {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create("/oauth2/authorization/saas-admin"))
            .build()
    }

    /**
     * Returns current logged-in super admin info.
     */
    @GetMapping("/me")
    suspend fun me(
        @AuthenticationPrincipal user: OidcUser?
    ): ResponseEntity<SuperAdminMeResponse> {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        return ResponseEntity.ok(
            SuperAdminMeResponse(
                id = user.subject,
                username = user.preferredUsername,
                email = user.email,
                roles = user.authorities.map { it.authority }
            )
        )
    }

}