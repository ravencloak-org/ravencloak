package com.keeplearning.auth.config

import io.r2dbc.postgresql.codec.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.PostgresDialect

@Configuration
class R2dbcConfig {

    @Bean
    fun r2dbcCustomConversions(): R2dbcCustomConversions {
        // Use PostgresDialect which includes native support for Json type
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, emptyList<Any>())
    }
}
