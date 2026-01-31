package com.keeplearning.auth.auth


data class SuperAdminMeResponse(
    val id: String,
    val username: String?,
    val email: String?,
    val roles: List<String?>
)
