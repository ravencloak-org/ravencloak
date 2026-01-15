package com.keeplearning.auth.config

import org.springframework.stereotype.Component

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import kotlin.collections.emptyList


@Component
class JwtAuthorityConverter : Converter<Jwt, Collection<GrantedAuthority>> {

    override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
        val realmAccess = jwt.getClaimAsMap("realm_access")
        val roles = realmAccess?.get("roles") as? Collection<*>
            ?: emptyList<Any>()

        return roles.map {
            SimpleGrantedAuthority("ROLE_$it")
        }
    }
}
