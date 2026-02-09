package com.keeplearning.forge.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "forge")
data class AuthProperties(
    val baseUrl: String,
    val realmName: String,
    val clientRegistrationId: String = "forge",
    val apiVersion: String = "1.0",
    val startupSync: StartupSyncProperties = StartupSyncProperties()
)

data class StartupSyncProperties(
    val enabled: Boolean = true
)

@Deprecated("Renamed to AuthProperties", ReplaceWith("AuthProperties"))
typealias ForgeProperties = AuthProperties
