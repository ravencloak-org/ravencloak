package com.keeplearning.auth.client.controller

import com.keeplearning.auth.client.dto.OnboardUsersRequest
import com.keeplearning.auth.client.dto.OnboardUsersResponse
import com.keeplearning.auth.client.service.ClientOnboardingService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/clients")
class ClientOnboardingController(
    private val onboardingService: ClientOnboardingService
) {

    /**
     * Onboard users for the client identified by the JWT's azp claim.
     *
     * The client ID is extracted from the JWT token (azp claim), so client backends
     * don't need to specify it in the URL. For paired clients (frontend/backend),
     * users are automatically authorized for the frontend client.
     *
     * Request body:
     * ```json
     * {
     *   "users": [
     *     {
     *       "email": "john@example.com",
     *       "firstName": "John",
     *       "lastName": "Doe",
     *       "displayName": "John Doe",
     *       "phone": "+1234567890",
     *       "jobTitle": "Engineer",
     *       "department": "Engineering",
     *       "roles": ["admin", "faculty"]
     *     }
     *   ]
     * }
     * ```
     *
     * If `roles` is empty, the default role for the client will be assigned.
     * If no default role is configured and no roles are specified, an error is returned.
     */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun onboardUsers(
        @RequestBody request: OnboardUsersRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): OnboardUsersResponse {
        // Verify the caller has a service account (backend client)
        val hasAccess = onboardingService.verifyOnboardAccess(jwt)
        if (!hasAccess) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only clients with service accounts enabled can onboard users"
            )
        }

        return onboardingService.onboardUsers(jwt, request)
    }
}
