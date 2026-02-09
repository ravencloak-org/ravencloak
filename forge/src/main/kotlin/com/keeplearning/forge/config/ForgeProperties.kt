package com.keeplearning.forge.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "forge")
data class ForgeProperties(
    val baseUrl: String,
    val realmName: String,
    val clientRegistrationId: String = "forge",
    val apiVersion: String = "1.0"
)
