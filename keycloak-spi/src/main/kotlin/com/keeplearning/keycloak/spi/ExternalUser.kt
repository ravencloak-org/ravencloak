package com.keeplearning.keycloak.spi

/**
 * Data class representing a user fetched from the external REST API.
 */
data class ExternalUser(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null
)
