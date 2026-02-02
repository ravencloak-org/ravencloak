package com.keeplearning.auth.realm.dto

data class CreateIdpRequest(
    val alias: String,
    val displayName: String? = null,
    val providerId: String,
    val enabled: Boolean = true,
    val trustEmail: Boolean = false,
    val config: Map<String, String>? = null
)

data class UpdateIdpRequest(
    val displayName: String? = null,
    val enabled: Boolean? = null,
    val trustEmail: Boolean? = null,
    val config: Map<String, String>? = null
)

data class IdpResponse(
    val alias: String,
    val displayName: String?,
    val providerId: String,
    val enabled: Boolean,
    val trustEmail: Boolean,
    val config: Map<String, String>
)
