package com.keeplearning.forge.exception

import com.keeplearning.auth.scim.common.ScimErrorResponse

class AuthException(
    val status: Int,
    val scimError: ScimErrorResponse? = null,
    message: String? = scimError?.detail ?: "Auth SDK error (HTTP $status)",
    cause: Throwable? = null
) : RuntimeException(message, cause)

@Deprecated("Renamed to AuthException", ReplaceWith("AuthException"))
typealias ForgeException = AuthException
