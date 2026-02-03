package com.keeplearning.auth.public.dto

data class PublicUserResponse(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val displayName: String?,
    val enabled: Boolean,
    val emailVerified: Boolean,
    val attributes: Map<String, List<String>> = emptyMap()
)
