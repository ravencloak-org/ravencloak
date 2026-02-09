package com.keeplearning.auth.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.ApiVersionConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
class WebFluxVersioningConfig : WebFluxConfigurer {

    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer.useRequestHeader("API-Version")
    }
}
