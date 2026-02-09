package com.keeplearning.auth.scim

class ScimException(
    val status: Int,
    val scimType: String? = null,
    val detail: String? = null
) : RuntimeException(detail)
