package com.keeplearning.auth.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig(
    @Value("\${KEYCLOAK_SAAS_ISSUER_URI:}") private val issuerUri: String
) {

    @Bean
    fun customOpenAPI(): OpenAPI {
        val authorizationUrl = if (issuerUri.isNotBlank()) {
            "$issuerUri/protocol/openid-connect/auth"
        } else {
            "http://localhost:8088/realms/saas-admin/protocol/openid-connect/auth"
        }

        val tokenUrl = if (issuerUri.isNotBlank()) {
            "$issuerUri/protocol/openid-connect/token"
        } else {
            "http://localhost:8088/realms/saas-admin/protocol/openid-connect/token"
        }

        return OpenAPI()
            .info(
                Info()
                    .title("KOS Auth Backend API")
                    .description("""
                        Authentication and authorization backend for KOS platform.

                        ## Authentication

                        This API uses OAuth2/OIDC with Keycloak for authentication. Most endpoints require a valid JWT token.

                        ### Endpoint Categories

                        - **Public** (`/api/public/**`): No authentication required
                        - **Super Admin** (`/api/super/**`): Requires SUPER_ADMIN role from saas-admin realm
                        - **Account Admin** (`/api/account/**`): Requires ACCOUNT_ADMIN or INSTITUTE_ADMIN role
                        - **Client** (`/api/clients/**`): Requires valid authentication (service account or user)
                    """.trimIndent())
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("KOS Team")
                    )
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        "oauth2",
                        SecurityScheme()
                            .type(SecurityScheme.Type.OAUTH2)
                            .flows(
                                OAuthFlows()
                                    .authorizationCode(
                                        OAuthFlow()
                                            .authorizationUrl(authorizationUrl)
                                            .tokenUrl(tokenUrl)
                                            .scopes(
                                                Scopes()
                                                    .addString("openid", "OpenID Connect")
                                                    .addString("profile", "User profile")
                                                    .addString("email", "User email")
                                            )
                                    )
                                    .clientCredentials(
                                        OAuthFlow()
                                            .tokenUrl(tokenUrl)
                                            .scopes(Scopes())
                                    )
                            )
                    )
                    .addSecuritySchemes(
                        "bearer",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT token from Keycloak")
                    )
            )
            .addSecurityItem(
                SecurityRequirement()
                    .addList("bearer")
            )
    }
}
