package com.keeplearning.auth.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager

import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository

import reactor.core.publisher.Mono
import com.keeplearning.auth.security.SuperAdminAuthorizationManager

@Configuration
@EnableReactiveMethodSecurity
class SecurityConfig(
    private val superAdminAuthorizationManager: SuperAdminAuthorizationManager, // Ensure this is a @Component in a scanned package
    @Value("\${KEYCLOAK_ISSUER_PREFIX}") private val keycloakIssuerPrefix: String
) {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/api/public/**").permitAll()
                    .pathMatchers("/api/super/**").access(superAdminAuthorizationManager)
                    .pathMatchers("/api/account/**").hasAnyRole("ACCOUNT_ADMIN", "INSTITUTE_ADMIN")
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.authenticationManagerResolver(reactiveJwtIssuerResolver())
            }
            .build()
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
            Mono.just(JwtReactiveAuthenticationManager(decoder))
        }
    }
}
