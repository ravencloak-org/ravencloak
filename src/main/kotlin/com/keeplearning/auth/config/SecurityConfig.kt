package com.keeplearning.auth.config

import com.keeplearning.auth.security.SuperAdminAuthorizationManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import reactor.core.publisher.Mono

@Configuration
@EnableReactiveMethodSecurity
@EnableWebFluxSecurity
class SecurityConfig(
    private val superAdminAuthorizationManager: SuperAdminAuthorizationManager,
    private val jwtAuthorityConverter: JwtAuthorityConverter,
    @Value("\${KEYCLOAK_ISSUER_PREFIX}") private val keycloakIssuerPrefix: String
) {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity, logoutSuccessHandler: ServerLogoutSuccessHandler): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .cors { }
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/api/public/**").permitAll()
                    .pathMatchers("/auth/super/login", "/oauth2/**").permitAll()
                    // OpenAPI / Swagger UI endpoints
                    .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                    .pathMatchers("/api/super/**").access(superAdminAuthorizationManager)
                    .pathMatchers("/api/account/**").hasAnyRole("ACCOUNT_ADMIN", "INSTITUTE_ADMIN")
                    .pathMatchers("/api/scim/v2/**").authenticated()
                    .pathMatchers("/api/clients/**").authenticated()
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.authenticationManagerResolver(reactiveJwtIssuerResolver())
            }
            .oauth2Login { }
            .logout {
                it.logoutUrl("/auth/super/logout")
                it.logoutSuccessHandler(logoutSuccessHandler)
            }
            .build()
    }

    @Bean
    fun oidcLogoutSuccessHandler(
        clientRegistrationRepository: ReactiveClientRegistrationRepository
    ): ServerLogoutSuccessHandler {
        val oidcLogoutSuccessHandler = OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository)
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/")
        return oidcLogoutSuccessHandler
    }

    /**
     * Returns a JwtIssuerReactiveAuthenticationManagerResolver for multi-issuer JWT validation in WebFlux.
     * This bean can be used directly as the authenticationManagerResolver in your oauth2ResourceServer config.
     */
    @Bean
    fun reactiveJwtIssuerResolver(): JwtIssuerReactiveAuthenticationManagerResolver {
        return JwtIssuerReactiveAuthenticationManagerResolver { issuer ->
            require(issuer.startsWith(keycloakIssuerPrefix)) { "Invalid issuer" }
            val decoder: ReactiveJwtDecoder = ReactiveJwtDecoders.fromIssuerLocation(issuer)
            val authManager = JwtReactiveAuthenticationManager(decoder)

            // Use the JwtAuthorityConverter to extract realm roles
            val jwtAuthConverter = ReactiveJwtAuthenticationConverter()
            jwtAuthConverter.setJwtGrantedAuthoritiesConverter { jwt ->
                reactor.core.publisher.Flux.fromIterable(jwtAuthorityConverter.convert(jwt) ?: emptyList())
            }
            authManager.setJwtAuthenticationConverter(jwtAuthConverter)

            Mono.just(authManager)
        }
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val corsConfig = CorsConfiguration()
        corsConfig.allowedOrigins = listOf("http://localhost:5173", "https://forge.keeplearningos.com")
        corsConfig.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        corsConfig.allowedHeaders = listOf("*")
        corsConfig.exposedHeaders = listOf("*")
        corsConfig.allowCredentials = true
        corsConfig.maxAge = 3600
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", corsConfig)
        return source
    }


}
