package com.keeplearning.forge.exception

import com.keeplearning.auth.scim.common.ScimErrorResponse

class ForgeException(
    val status: Int,
    val scimError: ScimErrorResponse? = null,
    message: String? = scimError?.detail ?: "Forge SDK error (HTTP $status)",
    cause: Throwable? = null
) : RuntimeException(message, cause)
